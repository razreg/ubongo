package ubongo.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.Globals;
import ubongo.common.datatypes.FlowData;
import ubongo.common.datatypes.Machine;
import ubongo.common.datatypes.Task;
import ubongo.common.datatypes.unit.Unit;
import ubongo.persistence.Configuration;
import ubongo.persistence.PersistenceException;
import ubongo.persistence.db.Queries;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.UnmarshalException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

@Path("/api")
public final class RestService {

    // TODO add log messages
    private static Logger logger = LogManager.getLogger(RestService.class);

    // TODO move section to configuration?
    private static final int DEFAULT_QUERY_LIMIT = 1000;
    private static final String CONFIG_PATH =
            Paths.get(Globals.SERVICE_SOURCES_ROOT, "data/config/ubongo-config-raz.xml").toString();
    private static final String UNITS_DIR_PATH =
            Paths.get(Globals.SERVICE_SOURCES_ROOT, "data/unit_settings").toString(); // TODO move to config file?

    private static final String APP_VERSION = "1.0.0";
    private static ServiceProvider serviceProvider = null;
    private static final String FAILURE_MSG =
            "The ServiceProvider failed to start. Please check the server's configuration and restart it.";

    @GET
    @Path("/version")
    @Produces(MediaType.APPLICATION_JSON)
    public String getVersion() {
        return "{\"version\": \"" + APP_VERSION + "\"}";
    }

    @GET
    @Path("machines")
    @Produces(MediaType.APPLICATION_JSON)
    public String getAllMachines() throws UbongoHttpException {
        if (serviceProvider == null) {
            throw new UbongoHttpException(500, FAILURE_MSG);
        }
        ObjectMapper mapper = new ObjectMapper();
        String response;
        try {
            List<Machine> machines = serviceProvider.getAllMachines();
            if (machines == null) {
                throw new UbongoHttpException(500, "Failed to retrieve machines.");
            }
            response = mapper.writeValueAsString(machines);
        } catch (JsonProcessingException e) {
            throw new UbongoHttpException(500, "Failed to serialize machines to JSON.");
        } catch (PersistenceException e) {
            throw new UbongoHttpException(500, "Failed to retrieve machines from DB.");
        }
        return response;
    }

    @GET
    @Path("analyses/names")
    @Produces(MediaType.APPLICATION_JSON)
    public String getAllAnalysisNames(@QueryParam("limit") int limit) throws UbongoHttpException {
        if (serviceProvider == null) {
            throw new UbongoHttpException(500, FAILURE_MSG);
        }
        ObjectMapper mapper = new ObjectMapper();
        String response;
        try {
            List<String> analysisNames =
                    serviceProvider.getAllAnalysisNames(limit > 0 ? limit : DEFAULT_QUERY_LIMIT);
            if (analysisNames == null) {
                throw new UbongoHttpException(500, "Failed to retrieve analysis names from DB.");
            }
            response = mapper.writeValueAsString(analysisNames);
        } catch (JsonProcessingException e) {
            throw new UbongoHttpException(500, "Failed to serialize analysis names to JSON.");
        } catch (PersistenceException e) {
            throw new UbongoHttpException(500, "Failed to read analysis names from DB. Details: " + e.getMessage());
        }
        return response;
    }

    @GET
    @Path("flows")
    @Produces(MediaType.APPLICATION_JSON)
    public String getAllFlows(@QueryParam("limit") int limit) throws UbongoHttpException {
        if (serviceProvider == null) {
            throw new UbongoHttpException(500, FAILURE_MSG);
        }
        ObjectMapper mapper = new ObjectMapper();
        String response;
        try {
            List<FlowData> flows = serviceProvider.getAllFlows(limit > 0 ? limit : DEFAULT_QUERY_LIMIT);
            if (flows == null) {
                throw new UbongoHttpException(500, "Failed to retrieve flows from DB.");
            }
            response = mapper.writeValueAsString(flows);
        } catch (JsonProcessingException e) {
            throw new UbongoHttpException(500, "Failed to serialize flows to JSON.");
        } catch (PersistenceException e) {
            throw new UbongoHttpException(500, "Failed to read flows from DB. Details: " + e.getMessage());
        }
        return response;
    }

    @POST
    @Path("flows")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String createFlow(String requestBody) throws UbongoHttpException {
        if (serviceProvider == null) {
            throw new UbongoHttpException(500, FAILURE_MSG);
        }
        int flow;
        String studyName;
        List<Task> tasks;
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode jsonNode = mapper.readTree(requestBody);
            if (jsonNode.hasNonNull("studyName")) {
                studyName = jsonNode.get("studyName").asText();
            } else {
                throw new UbongoHttpException(400, "Study name for flow cannot be empty nor null.");
            }
            if (jsonNode.hasNonNull("tasks")) {
                tasks = mapper.readValue(jsonNode.get("tasks").toString(),
                        new TypeReference<List<Task>>(){});
            } else {
                throw new UbongoHttpException(400, "Flow must contain at least one task.");
            }
            flow = serviceProvider.createFlow(studyName, tasks);
        } catch (IOException e) {
            throw new UbongoHttpException(500, "Failed to deserialize JSON to FlowData.");
        } catch (PersistenceException e) {
            throw new UbongoHttpException(500, "Failed to create flow in DB. Details: " + e.getMessage());
        }
        return "{\"flowId\": \"" + flow + "\"}";
    }

    @POST
    @Path("flows/{flowId}")
    @Produces(MediaType.APPLICATION_JSON)
    public void performActionOnFlow(@PathParam("flowId") int flowId,
                                    @NotNull @QueryParam("action") ResourceAction action)
            throws UbongoHttpException {
        if (serviceProvider == null) {
            throw new UbongoHttpException(500, FAILURE_MSG);
        }
        switch (action) {
            case CANCEL:
                try {
                    serviceProvider.cancelFlow(flowId);
                } catch (PersistenceException e) {
                    throw new UbongoHttpException(500, "Failed to cancel flow. Details: " + e.getMessage());
                }
                break;
            case RUN:
                try {
                    serviceProvider.runFlow(flowId);
                } catch (PersistenceException e) {
                    throw new UbongoHttpException(500, "Failed to run flow. Details: " + e.getMessage());
                }
                break;
            default:
                throw new UbongoHttpException(405, "Unsupported action on flow (" + action.name() + ").");
        }
    }

    @GET
    @Path("flows/all/tasks")
    @Produces(MediaType.APPLICATION_JSON)
    public String getAllTasks(@QueryParam("limit") int limit) throws UbongoHttpException {
        if (serviceProvider == null) {
            throw new UbongoHttpException(500, FAILURE_MSG);
        }
        ObjectMapper mapper = new ObjectMapper();
        String response;
        try {
            List<Task> tasks = serviceProvider.getAllTasks(limit > 0 ? limit : DEFAULT_QUERY_LIMIT);
            if (tasks == null) {
                throw new UbongoHttpException(500, "Failed to retrieve tasks from DB.");
            }
            response = mapper.writeValueAsString(tasks);
        } catch (PersistenceException e) {
            throw new UbongoHttpException(500, "Failed to read tasks from DB. Details: " + e.getMessage());
        } catch (JsonProcessingException e) {
            throw new UbongoHttpException(500, "Failed to serialize tasks to JSON.");
        }
        return response;
    }

    @GET
    @Path("flows/{flowId}/tasks")
    @Produces(MediaType.APPLICATION_JSON)
    public String getTasks(@DefaultValue("-1") @PathParam("flowId") int flowId) throws UbongoHttpException {
        if (serviceProvider == null) {
            throw new UbongoHttpException(500, FAILURE_MSG);
        }
        if (flowId < 0) {
            throw new UbongoHttpException(400, "Flow ID must be a positive integer.");
        }
        ObjectMapper mapper = new ObjectMapper();
        String response;
        try {
            List<Task> tasks = serviceProvider.getTasks(flowId);
            if (tasks == null) {
                throw new UbongoHttpException(500, "Failed to retrieve tasks.");
            }
            response = mapper.writeValueAsString(tasks);
        }
        catch (JsonProcessingException e) {
            throw new UbongoHttpException(500, "Failed to serialize tasks to JSON.");
        } catch (PersistenceException e) {
            throw new UbongoHttpException(500, "Failed to read tasks from DB. Details: " + e.getMessage());
        }
        return response;
    }

    @POST
    @Path("flows/{flowId}/tasks/{taskId}")
    @Produces(MediaType.APPLICATION_JSON)
    public void performActionOnTask(@PathParam("flowId") int flowId,
                                    @PathParam("taskId") int taskId,
                                    @QueryParam("action") ResourceAction action)
            throws UbongoHttpException {
        if (serviceProvider == null) {
            throw new UbongoHttpException(500, FAILURE_MSG);
        }
        Task task;
        try {
            task = serviceProvider.getTask(taskId);
        } catch (PersistenceException e) {
            throw new UbongoHttpException(500, "Failed to retrieve task from DB. Details: " + e.getMessage());
        }
        if (task.getFlowId() != flowId) {
            throw new UbongoHttpException(400, "The taskId does not match the flowId.");
        }
        switch (action) {
            case CANCEL:
                try {
                    serviceProvider.cancelTask(task);
                } catch (PersistenceException e) {
                    throw new UbongoHttpException(500, "Failed to cancel tasks. Details: " + e.getMessage());
                }
                break;
            case RESUME:
                try {
                    serviceProvider.resumeTask(task);
                } catch (PersistenceException e) {
                    throw new UbongoHttpException(500, "Failed to resume task. Details: " + e.getMessage());
                }
                break;
            case STOP:
                try {
                    serviceProvider.killTask(task);
                } catch (PersistenceException e) {
                    throw new UbongoHttpException(500, "Failed to stop task. Details: " + e.getMessage());
                }
                break;
            default:
                throw new UbongoHttpException(405, "Unsupported action on flow (" + action.name() + ").");
        }
    }

    @GET
    @Path("flows/{flowId}/tasks/{taskId}/logs")
    @Produces(MediaType.APPLICATION_JSON)
    public String getTaskLogs(@PathParam("flowId") int flowId,
                              @PathParam("taskId") int taskId) throws UbongoHttpException {
        if (serviceProvider == null) {
            throw new UbongoHttpException(500, FAILURE_MSG);
        }
        ObjectMapper mapper = new ObjectMapper();
        String response;
        try {
            List<String> logs = serviceProvider.showTaskLogs(taskId);
            if (logs == null) {
                throw new UbongoHttpException(500, "Failed to retrieve task logs.");
            }
            response = mapper.writeValueAsString(logs);
        }
        catch (JsonProcessingException e) {
            throw new UbongoHttpException(500, "Failed to serialize logs to JSON.");
        }
        return response;
    }

    @GET
    @Path("log")
    @Produces(MediaType.APPLICATION_JSON)
    public String getServerLog() throws UbongoHttpException {
        if (serviceProvider == null) {
            throw new UbongoHttpException(500, FAILURE_MSG);
        }
        ObjectMapper mapper = new ObjectMapper();
        String response;
        try {
            List<String> logs = serviceProvider.showServerLog();
            if (logs == null) {
                throw new UbongoHttpException(500, "Failed to retrieve server log.");
            }
            response = mapper.writeValueAsString(logs);
        }
        catch (JsonProcessingException e) {
            throw new UbongoHttpException(500, "Failed to serialize server log to JSON.");
        }
        return response;
    }

    @GET
    @Path("units")
    @Produces(MediaType.APPLICATION_JSON)
    public String getAllUnits() throws UbongoHttpException {
        if (serviceProvider == null) {
            throw new UbongoHttpException(500, FAILURE_MSG);
        }
        ObjectMapper mapper = new ObjectMapper();
        String response;
        try {
            List<Unit> units = serviceProvider.getAllUnits();
            if (units == null) {
                throw new UbongoHttpException(500, "Failed to retrieve units.");
            }
            response = mapper.writeValueAsString(units);
        } catch (PersistenceException e) {
            throw new UbongoHttpException(500, "One or more units could not be read. Details: " + e.getMessage());
        } catch (JsonProcessingException e) {
            throw new UbongoHttpException(500, "Failed to serialize units to JSON.");
        }
        return response;
    }

    public RestService() {
        Queries.propFilePath = Paths.get(Globals.SERVICE_SOURCES_ROOT, Queries.propFileName).toString();
        try {
            if (serviceProvider == null) {
                Configuration configuration = Configuration.loadConfiguration(CONFIG_PATH);
                serviceProvider = new ServiceProviderImpl(configuration, UNITS_DIR_PATH, configuration.getDebug());
            }
        } catch (UnmarshalException e) {
            serviceProvider = null;
        }
    }
}
