package ubongo.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.datatypes.Machine;
import ubongo.common.datatypes.Task;
import ubongo.common.datatypes.TaskStatus;
import ubongo.persistence.Persistence;
import ubongo.persistence.exceptions.PersistenceException;
import ubongo.server.exceptions.MachinesManagementException;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class QueueManager {

    /* max time (in milliseconds) the producer thread awaits to be notified of new tasks.
       After this amount of time, it will check if there are new tasks in the DB. */
    private static final int MAX_PRODUCER_IDLE_TIME = 1000 * 60 * 30; // 30 minutes
    private static final int MAX_QUEUE_CAPACITY = 500;
    private static final int NUM_CONSUMER_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors());

    private static Logger logger = LogManager.getLogger(QueueManager.class);
    private final Object consumerLock = new Object();
    private final Object producerLock = new Object();
    private boolean producerMayWork = true; // lets the producers know whether they may work or not
    private boolean producerUpdatingDatabase = false; // lets the consumers know they need to wait

    private BlockingQueue<Task> queue = new ArrayBlockingQueue<>(MAX_QUEUE_CAPACITY, true);
    private Persistence persistence;
    private ExecutionProxy executionProxy;
    private MachinesManager machinesManager;
    private ExecutorService consumers;
    private ExecutorService producer;

    /**
     * setLocatorMap maps between TaskKey and DependencyKey; TaskKey is an identifier based on a task's flow-id and
     * serial number within that flow, whereas DependencyKey is an object that includes a unique identifier for
     * a set of tasks. This set of tasks corresponds to the TaskKey - namely, all tasks within the set share the same
     * flow-id and serial number. Therefore, this data-structure behaves very much like
     * <a href="https://en.wikipedia.org/wiki/Disjoint-set_data_structure">union-find</a>.
     */
    private final Map<TaskKey, DependencyKey> setLocatorMap = new HashMap<>();

    /**
     * dependencyMap maps between a unique id corresponding to a DependencyKey {@link DependencyKey#id} and a set of
     * tasks. This set of tasks is all the tasks that are currently known to be dependent on the tasks that correspond
     * to the given id (a DependencyKey stores a set of tasks).
     * It may not be clear why we need to separate between setLocatorMap and dependencyMap. The separation was not
     * necessary if a mutable object could have acted as a map key in Java. If that were the case, we could have simply
     * have a map between a set of tasks and the tasks that depend on the first set.
     */
    private final Map<Integer, Set<Task>> dependencyMap = new HashMap<>();
    private boolean updatingDependencies = false;

    /**
     * Task cancellation mechanism to store taskIds when they are pending cancellation but have not been cancelled yet.
     * It is required so that when a task is about to be cancelled it would not be retrieved by the queue producer and
     * sent for execution which would complicate the cancellation flow and require stopping execution, which is more
     * complex.
     */
    private final Set<Integer> taskIdsInCancel = new HashSet<>();

    QueueManager(Persistence persistence, MachinesManager machinesManager) {
        this.executionProxy = ExecutionProxy.getInstance();
        this.persistence = persistence;
        this.machinesManager = machinesManager;
    }

    /**
     * @see #initQueue()
     */
    public void start() {
        initQueue();
        logger.info("Queue Manager started");
    }

    /**
     * Shutdowns all threads used by the QueueManager and clear data-types.
     */
    public void stop() {
        producer.shutdownNow();
        consumers.shutdownNow();
        queue.clear();
        dependencyMap.clear();
        taskIdsInCancel.clear();
        setLocatorMap.clear();
        logger.info("Queue Manager stopped");
    }

    /**
     * Sets the flow tasks in the DB in status 'New' so they will be 'visible' to the producer thread/s and therefore
     * will be added to the queue for execution.
     * @param flowId to update in DB.
     * @throws PersistenceException in case the update failed - may cause some of the tasks of a flow to be in status
     * 'New' and others to remain in their old status.
     */
    public void startFlow(int flowId) throws PersistenceException {

        // start flow in DB and notify producer thread
        synchronized(producerLock) {
            producerMayWork = false;
        }
        persistence.startFlow(flowId);
        synchronized(producerLock) {
            producerMayWork = true;
            producerLock.notify();
        }
    }

    /**
     * Resumes the task in the DB (changes status to 'New') and notifies the producer.
     * @param taskId of task to resume (must be resumable, i.e. stopped, canceled, etc.)
     * @throws PersistenceException if the DB update has failed.
     */
    public void resumeTask(int taskId) throws PersistenceException {
        synchronized(producerLock) {
            producerMayWork = false;
        }
        logger.debug("Resuming task with id=" + taskId);
        persistence.resumeTask(taskId);
        synchronized(producerLock) {
            producerMayWork = true;
            producerLock.notify();
        }
    }

    /**
     * This method is called by the ExecutionProxy {@link ExecutionProxy} to update the system after a task
     * has been completed, stopped or failed. First, the status of task is persisted in the DB. Second,
     * the QueueManager updates tasks which depend on the completion of task by calling handleCompletedTask
     * {@link #handleCompletedTask(Task)}.
     * @param task to update based on status.
     */
    synchronized public void updateTaskAfterExecution(Task task) {
        try {
            persistence.updateTaskStatus(task);
        } catch (Exception e) {
            logger.fatal("Failed to update task with id=" + task.getId() + " in DB", e);
            ExecutionServer.notifyFatal(e);
        }
        try {
            TaskStatus status = task.getStatus();
            if (status == TaskStatus.COMPLETED ||
                status == TaskStatus.FAILED ||
                status == TaskStatus.STOPPED ||
                status == TaskStatus.ON_HOLD) {
                synchronized (dependencyMap) {
                    while (updatingDependencies) {
                        dependencyMap.wait();
                    }
                    updatingDependencies = true;
                    handleCompletedTask(task);
                    updatingDependencies = false;
                    dependencyMap.notifyAll();
                }
            }
        } catch (Exception e) {
            logger.fatal("Some problem occurred while tried to handle dependent tasks of task with id="
                    + task.getId() + "from flow with id=" + task.getFlowId(), e);
            ExecutionServer.notifyFatal(e);
        }
    }

    /**
     * Initializes all data-structures of the QueueManager. Namely, this method creates thread pools and kick-starts
     * the producer and consumer threads.
     */
    private void initQueue() {
        consumers = Executors.newFixedThreadPool(NUM_CONSUMER_THREADS);
        for (int i = 0; i < NUM_CONSUMER_THREADS; i++)
            consumers.execute(new Consumer(queue, persistence));
        producer = Executors.newSingleThreadExecutor();
        producer.execute(new Producer(queue, persistence));
    }

    /**
     * @see QueueManager#handleCompletedTask(Task, boolean)
     */
    synchronized private boolean handleCompletedTask(Task task) throws PersistenceException {
        return handleCompletedTask(task, true);
    }

    /**
     * The given task is used as a key to retrieve all tasks with the same flow-id and serial number.
     * If all of these tasks were completed, it means that tasks with greater serial numbers - which depend on the
     * previous ones - can be now executed. The status of these tasks is updated to 'New' so they will be picked up by
     * the queue producer and executed in future cycles, now that all of their dependencies are completed.
     * On the other hand, if some task failed or was stopped, then its dependents (those with a greater serial number)
     * cannot be executed until manual intervention and therefore will change status to On_Hold.
     * @param task to be handled.
     * @param letProducerWorkWhenDone tells the method whether the producer can work after this run -
     *        it is required since this method is recursive and we want the producer to work only once it is completed.
     * @return true iff some tasks were updated to 'New' as a result of the completion of task.
     * @throws PersistenceException if updating the DB has failed.
     */
    synchronized private boolean handleCompletedTask(Task task, boolean letProducerWorkWhenDone) throws PersistenceException {
        TaskKey key = new TaskKey(task);
        DependencyKey dependencyKey = setLocatorMap.get(key);
        if (dependencyKey == null) {
            return false;
        }
        /* the set of all tasks with the same flow id and serial number which need to be completed in order for tasks
           with greater serial numbers to be executed */
        Set<Integer> taskIdsSet = dependencyKey.getSet();
        if (taskIdsSet != null) {
            taskIdsSet.remove(task.getId());
            // if the following tasks can be executed or should be on hold
            if (taskIdsSet.isEmpty() || task.getStatus() == TaskStatus.FAILED || task.getStatus() == TaskStatus.CANCELED
                    || task.getStatus() == TaskStatus.STOPPED_FAILURE || task.getStatus() == TaskStatus.STOPPED
                    || task.getStatus() == TaskStatus.ON_HOLD) {
                Set<Task> dependingTasks = dependencyMap.get(dependencyKey.getId());
                if (dependingTasks != null) {
                    boolean makeNew = task.getStatus() == TaskStatus.COMPLETED;
                    TaskStatus newStatus = makeNew ? TaskStatus.NEW : TaskStatus.ON_HOLD;
                    /* send dependingTasks back to DB as NEW so they will be retrieved by the queue
                       and next time it will be able to run them (this allows orderly dependency verification),
                       or change status to On Hold if the flow is stuck */
                    dependingTasks.forEach(t -> t.setStatus(newStatus));
                    persistence.updateTasksStatus(dependingTasks);
                    if (logger.isInfoEnabled()) {
                        logger.info("Changed status to " + newStatus + " for " + dependingTasks.size()
                                + " tasks from flow with id=" + task.getFlowId() + " after handling task with id="
                                + task.getId() + " and status " + task.getStatus());
                    }
                    if (!makeNew) {
                        for (Task t : dependingTasks) {
                            handleCompletedTask(t, false);
                        }
                    }
                }
                if (letProducerWorkWhenDone) {
                    synchronized (producerLock) {
                        producerMayWork = true;
                        producerLock.notify();
                    }
                }
                dependencyMap.remove(dependencyKey.getId());
                setLocatorMap.remove(key);
                return true;
            }
        }
        return false;
    }

    /**
     * This method is called when a task is pending cancellation. Adding this task to the taskIdsInCancel data-structure
     * allows the queue producer to filter it out from the queue so that it won't be executed while trying to cancel it.
     * @param task which is pending cancellation
     */
    protected void aboutToCancel(Task task) {
        synchronized (taskIdsInCancel) {
            taskIdsInCancel.add(task.getId());
        }
    }

    /**
     * Removes the taskId from taskIdsInCancel because at this point it should be safe and the producer will not attempt
     * to add the task to the queue (because it should be now cancelled).
     * @param task which was cancelled.
     */
    protected void cancelCompleted(Task task) {
        synchronized (taskIdsInCancel) {
            taskIdsInCancel.remove(task.getId());
        }
    }

    /**
     * The consumer for the queue -
     * retrieves tasks from the queue and sends them for execution.
     */
    private class Consumer extends Thread {

        BlockingQueue<Task> queue;
        Persistence persistence;

        public Consumer(BlockingQueue<Task> queue, Persistence persistence) {
            this.queue = queue;
            this.persistence = persistence;
        }

        /**
         * Main consumer loop where the queue is polled for new tasks. Each task is tested to see whether it is ready
         * for execution {@link #taskReadyForExecute(Task)} and if so, is assigned an available machine and sent to
         * the ExecutionProxy {@link ExecutionProxy} for further processing.
         */
        @SuppressWarnings("InfiniteLoopStatement")
        @Override
        public void run() {
            try {
                while (true) {
                    Task task = queue.take();
                    Task currTask = (Task) task.clone();
                    synchronized (consumerLock) {
                        while (producerUpdatingDatabase) {
                            consumerLock.wait();
                        }
                    }
                    if (!taskReadyForExecute(currTask)) {
                        continue;
                    }
                    try {
                        Machine machine = machinesManager.getAvailableMachine();
                        currTask.setMachine(machine);
                        currTask.setStatus(TaskStatus.PROCESSING);
                        synchronized (taskIdsInCancel) {
                            if (taskIdsInCancel.contains(currTask.getId())) {
                                continue;
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Queue consumer thread failed to find available machine to run task. The flow will be stalled.", e);
                        currTask.setStatus(TaskStatus.ON_HOLD);
                        updateTaskAfterExecution(currTask);
                        continue;
                    }
                    persistence.updateTaskStatus(currTask);
                    if (currTask.getOutputPath().contains("*")) {
                        logger.error("Tried to execute taskId=" + currTask.getId()
                                + " but found '*' in the output path: " + currTask.getOutputPath());
                        currTask.setStatus(TaskStatus.FAILED);
                        updateTaskAfterExecution(currTask);
                        continue;
                    }
                    if (logger.isDebugEnabled()) {
                        logger.debug("Sending task with id=" + currTask.getId() + " for execution...");
                    }
                    executionProxy.execute(currTask, QueueManager.this);
                    if (logger.isInfoEnabled()) {
                        logger.info("Task with id=" + currTask.getId() + " was sent for execution");
                    }
                }
            } catch (InterruptedException | CloneNotSupportedException e) {
                /* InterruptedException will happen when the QueueManager is stopped and it will probably be logger
                   by the closer or the reason will be already known to the user. CloneNotSupportedException is not
                   really possible because clone is supported for task */
            } catch (Exception e) {
                logger.fatal("Failed to execute task", e);
                ExecutionServer.notifyFatal(e);
            }
        }

        /**
         * Checks whether all the tasks preceding the given task in its flow has already completed. If the task cannot
         * currently be executed, a TaskKey {@link TaskKey} is created for it and stored with the set of tasks on which
         * it depends in dependencyMap {@link #dependencyMap}. After storing the task with its dependencies, the DB is
         * queried once more to verify that no dependency has completed while storing the task; this step is required to
         * prevent a situation where the task is stuck waiting for a dependency to be completed while it was already
         * completed. Note that in this case, the return value would be false as the task is updated in the DB to 'New'
         * and will be retrieved by the QueueManager in its future cycles.
         * @param task to check dependencies for.
         * @return true iff all previous tasks in the same flow as task were executed and completed successfully.
         * If the serial number for this task is 0 it can always be executed.
         * @throws InterruptedException if the method was interrupted while waiting for the dependencyMap to be available.
         * @throws PersistenceException if tasks retrieval from the DB or updates to the DB failed.
         */
        synchronized private boolean taskReadyForExecute(Task task)
                throws InterruptedException, PersistenceException {
            boolean ready;
            int serial = task.getSerialNumber() - 1; // get the serial number of the preceding task
            if (serial < 0) {
                return true; // there are no predecessors for this task
            }
            synchronized (dependencyMap) {
                while (updatingDependencies) {
                    dependencyMap.wait();
                }
                updatingDependencies = true;
                // get a list of uncompleted tasks (e.g. failed or stopped) with serial smaller by one
                List<Task> tasks = persistence.getTasks(task.getFlowId()).stream()
                        .filter(t -> t.getSerialNumber() == serial &&
                                t.getStatus() != TaskStatus.COMPLETED).collect(Collectors.toList());
                if (!(ready = tasks.isEmpty())) { // if there are some dependencies
                    storeDependencies(task, tasks);
                    // after storing, we need to verify no dependent was completed in the meanwhile
                    // if it were, check if we can run now (return false anyway)
                    List<Task> completedTasks =
                            persistence.getTasks(task.getFlowId()).stream()
                            .filter(t -> t.getSerialNumber() == serial &&
                                    t.getStatus() == TaskStatus.COMPLETED).collect(Collectors.toList());
                    for (Task completedTask : completedTasks) {
                        if (handleCompletedTask(completedTask)) break;
                    }
                }
                updatingDependencies = false;
                dependencyMap.notifyAll();
            }
            return ready;
        }

        /**
         * First we note that any of the tasks of dependencyList are expected to have the same serial number, and task
         * has a serial number greater than that of any task in dependencyList. The serial number of the tasks in
         * dependencyList is used (together with the flow id) to find any task that already depends on these tasks.
         * The method then adds the current task to the dependencyMap {@link #dependencyMap} so that when a task from
         * dependencyList will be completed, the QueueManager will poll the dependencyMap and see if any of the
         * dependents may be executed.
         * @param task to add to dependencyMap so that when a task that it depends on will be completed,
         *             task will be reachable.
         * @param dependencyList is the list of tasks that task depends on, as explained above.
         */
        synchronized private void storeDependencies(Task task, List<Task> dependencyList) {
            TaskKey key = new TaskKey(dependencyList.stream().findAny().get()); // representative of set
            Set<Integer> taskIds;
            boolean inserted = false;
            // do we already have tasks depending on retrieved dependencyList?
            if (setLocatorMap.containsKey(key)) {
                DependencyKey dependencyKey = setLocatorMap.get(key);
                taskIds = dependencyKey.getSet();
                if (taskIds == null) {
                    setLocatorMap.remove(key); // false alarm
                } else {
                    Set<Task> dependents = dependencyMap.get(dependencyKey.getId());
                    if (dependents == null) { // false alarm
                        setLocatorMap.remove(key);
                        dependencyMap.remove(dependencyKey.getId());
                    } else { // this is not the first task depending on retrieved dependencyList
                        dependents.add(task);
                        inserted = true;
                        if (logger.isDebugEnabled()) {
                            logger.debug("Task with id=" + task.getId() + " from flow with id=" + task.getFlowId() +
                                    " depends on the completion of " + taskIds.size() + " other task(s)");
                        }
                    }
                }
            }
            if (!inserted) {
                taskIds = dependencyList.stream().map(Task::getId).collect(Collectors.toSet());
                DependencyKey dependencyKey = new DependencyKey(taskIds);
                setLocatorMap.put(key, dependencyKey);
                Set<Task> taskSet = new HashSet<>();
                taskSet.add(task);
                dependencyMap.put(dependencyKey.getId(), taskSet);
                if (logger.isDebugEnabled()) {
                    logger.debug("Task with id=" + task.getId() + " from flow with id=" + task.getFlowId() +
                            " depends on the completion of " + taskIds.size() + " other task(s)");
                }
            }
        }
    }

    /**
     * The producer for the queue -
     * retrieves tasks from the DB and inserts them to the queue.
     */
    private class Producer extends Thread {

        BlockingQueue<Task> queue;
        Persistence persistence;

        public Producer(BlockingQueue<Task> queue, Persistence persistence) {
            this.queue = queue;
            this.persistence = persistence;
        }

        /**
         * The main loop of the producer, where new tasks are fetched from the DB and inserted to the queue
         * for consumer processing.
         * If a task is in status 'New' in the DB, it is still possible that the user has requested to cancel it.
         * To support this scenario, where a task/flow is pending cancellation but is also ready to be executed,
         * before sending a status update of 'Cancel' to the DB, the taskIdsInCancel {@link #taskIdsInCancel} set is
         * updated to include the tasks that are about to be canceled. The producer verifies that any task to be
         * executed is not pending cancellation - if it hadn't done that, tasks that could be cancelled might be
         * mistakenly sent for execution and then will require the more complicated task-stopping scenario.
         */
        @SuppressWarnings("InfiniteLoopStatement")
        @Override
        public void run() {
            try {
                while (true) {
                    synchronized (producerLock) {
                        while (!producerMayWork) {
                            producerLock.wait(MAX_PRODUCER_IDLE_TIME);
                        }
                    }
                    List<Task> tasks;
                    try {
                        tasks = persistence.getNewTasks();
                    } catch (Exception e) {
                        continue;
                    }
                    synchronized(producerLock) {
                        if (tasks == null || tasks.isEmpty()) {
                            producerMayWork = false;
                            continue;
                        }
                    }
                    for (Task task: tasks) {
                        Task currTask = (Task) task.clone();
                        if (!insertContextToTask(currTask)) {
                            continue; // skip this task - it was not filled with context yet
                        }
                        if (holdTaskIfPreviousTasksAreStuck(currTask)) continue;
                        if (logger.isDebugEnabled()) {
                            logger.debug("Adding new task to queue (taskId=" + currTask.getId() + ")");
                        }
                        synchronized (consumerLock) {
                            producerUpdatingDatabase = true;
                        }
                        boolean taskInsertedToQueue = queue.offer(currTask);
                        if (!taskInsertedToQueue) {
                            synchronized (consumerLock) {
                                producerUpdatingDatabase = false;
                                consumerLock.notifyAll();
                            }
                            queue.put(currTask);
                            synchronized (consumerLock) {
                                producerUpdatingDatabase = true;
                            }
                        }
                        synchronized (taskIdsInCancel) {
                            if (taskIdsInCancel.contains(currTask.getId())) {
                                continue;
                            }
                            currTask.setStatus(TaskStatus.PENDING);
                            persistence.updateTaskStatus(currTask);
                        }
                        synchronized (consumerLock) {
                            producerUpdatingDatabase = false;
                            consumerLock.notifyAll();
                        }
                    }
                }
            } catch (InterruptedException e) {
                /* not interesting to log and certainly not to throw (this is usually triggered by planned
                shutdown and in case of unplanned, other more informative exceptions will have already been logged */
            } catch (Exception e) {
                logger.fatal("A fatal error has occurred in the Queue Manager's producer thread", e);
                ExecutionServer.notifyFatal(e);
            }
        }

        private boolean holdTaskIfPreviousTasksAreStuck(Task currTask) throws PersistenceException {
            int serial = currTask.getSerialNumber() - 1;
            List<Task> previousHoldingTasks = persistence.getTasks(currTask.getFlowId()).stream()
                    .filter(t -> t.getSerialNumber() == serial &&
                            (t.getStatus() == TaskStatus.FAILED ||
                                    t.getStatus() == TaskStatus.CANCELED ||
                                    t.getStatus() == TaskStatus.STOPPED ||
                                    t.getStatus() == TaskStatus.STOPPED_FAILURE ||
                                    t.getStatus() == TaskStatus.ON_HOLD)).collect(Collectors.toList());
            if (!previousHoldingTasks.isEmpty()) {
                synchronized (consumerLock) {
                    producerUpdatingDatabase = true;
                }
                currTask.setStatus(TaskStatus.ON_HOLD);
                logger.debug("Found holding tasks for current taskId="
                        + currTask.getId() + ". Changing status to 'On Hold'.");
                updateTaskAfterExecution(currTask);
                synchronized (consumerLock) {
                    producerUpdatingDatabase = false;
                    consumerLock.notifyAll();
                }
                return true;
            }
            return false;
        }

        /**
         * Some tasks in the DB might have wildcard context variables (i.e., ".*") and therefore this method replaces
         * the given task with the list of tasks corresponding to the different context combinations, if there are
         * more than one.
         * @param task to refactor (insert context values instead of .*)
         * @return true iff the task can be executed as is and no context wildcards were found.
         * @throws Exception in case of IOException or related exceptions. Another type of exception that may be
         * thrown is of course a PersistenceException in case the deletion and insertions to the database failed
         * ({@link Persistence#insertContextToTask(Task, List)}).
         */
        private boolean insertContextToTask(Task task) throws Exception {
            List<Task> tasks = Task.createTasks(task.getUnit(), task.getContext(), task.getSerialNumber());
            if (tasks.size() == 1 && task.getInputPath().equals(tasks.get(0).getInputPath()) &&
                    task.getOutputPath().equals(tasks.get(0).getOutputPath())) {
                return true;
            }
            persistence.insertContextToTask(task, tasks);
            if (logger.isDebugEnabled()) {
                logger.debug("Task with id=" + task.getId() +
                        " was found to include context wildcards. Context was inserted to the task and updated in the database, splitting it to "
                        + tasks.size() + " different tasks within flow with id=" + task.getFlowId());
            }
            return false;
        }
    }

    /**
     * A TaskKey is used to retrieve a set of Tasks using a given Task object - all tasks with the same flow-id and
     * serial number correspond to the same TaskKey and therefore this object is useful to implement a union-find
     * data-structure, where each task can be a representative
     * {@see <a href="https://en.wikipedia.org/wiki/Disjoint-set_data_structure">disjoint-set</a>}.
     */
    private static final class TaskKey {

        private int flowId;
        private int serial;

        public TaskKey(Task task) {
            flowId = task.getFlowId();
            serial = task.getSerialNumber();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof TaskKey)) {
                return false;
            }
            TaskKey other = (TaskKey) o;
            return this.flowId == other.flowId && this.serial == other.serial;
        }

        @Override
        public int hashCode() {
            int hash = 17;
            hash = hash * 31 + flowId;
            hash = hash * 31 + serial;
            return hash;
        }
    }

    /**
     * A DependencyKey is used as the value of setLocatorMap {@link #setLocatorMap} such that given a task we can
     * quickly find the set of preceding tasks on which it depends. The id {@link DependencyKey#id} is then used to
     * search the dependencyMap {@link #dependencyMap} in order to locate the rest of the tasks that rely on the
     * completion of taskIds {@link DependencyKey#taskIds}.
     */
    private static final class DependencyKey {

        private static AtomicInteger counter = new AtomicInteger(0);
        private Set<Integer> taskIds;
        private int id;

        public DependencyKey(Set<Integer> taskIds) {
            this.taskIds = taskIds;
            this.id = counter.getAndIncrement();
        }

        public Set<Integer> getSet() {
            return taskIds;
        }

        public int getId() {
            return id;
        }
    }
}
