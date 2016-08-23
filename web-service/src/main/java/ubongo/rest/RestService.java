package ubongo.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.datatypes.ExecutionRequest;
import ubongo.common.datatypes.FlowData;
import ubongo.common.datatypes.Machine;
import ubongo.common.datatypes.Task;
import ubongo.common.datatypes.unit.Unit;
import ubongo.persistence.Configuration;
import ubongo.persistence.PersistenceException;

import javax.servlet.ServletContext;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.UnmarshalException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Path("/api")
public final class RestService {

    @Context
    ServletContext context;

    private static boolean serviceProviderInitAttempted = false;
    private static ServiceProvider serviceProvider = null;

    private static final int DEFAULT_QUERY_LIMIT_FALLBACK = 1000;
    private static boolean defaultQueryLimitRetrieved = false;
    private static int defaultQueryLimit = DEFAULT_QUERY_LIMIT_FALLBACK;

    private static Logger logger = LogManager.getLogger(RestService.class);

    private static final String APP_VERSION = "1.0.0";
    private static final String TOMCAT_CONTEXT_CONFIGURED =
            "Please make sure context.xml is configured correctly in the Tomcat directory.";
    private static final String FAILURE_MSG =
            "The ServiceProvider failed to start. Please check the server's configuration and restart it.";

    private void init() throws UbongoHttpException {
        if (serviceProvider == null) {
            initServiceProvider();
        }
        if (!defaultQueryLimitRetrieved) {
            initDbQueryLimit();
        }
    }

    private void initServiceProvider() throws UbongoHttpException {
        if (serviceProviderInitAttempted) {
            throw new UbongoHttpException(500, FAILURE_MSG);
        }
        try {
            if (context == null) {
                throw new UbongoHttpException(500,
                        "Failed to retrieve servlet context. " + TOMCAT_CONTEXT_CONFIGURED);
            }
            String configPath = getContextParam("ubongo.config");
            String unitsPath = getContextParam("ubongo.units.dir");
            String queriesPath = getContextParam("ubongo.db.queries");
            Configuration configuration;
            try {
                configuration = Configuration.loadConfiguration(configPath);
            } catch (UnmarshalException e) {
                throw new UbongoHttpException(500, FAILURE_MSG);
            }
            serviceProvider = new ServiceProviderImpl(configuration, unitsPath, queriesPath, configuration.getDebug());
        } catch (UbongoHttpException e) {
            serviceProvider = null;
            throw e;
        } catch (Exception e) {
            throw new UbongoHttpException(500,
                    "The service has encountered an unexpected exception during initialization. Details: " + e.getMessage());
        }
        finally {
            serviceProviderInitAttempted = true;
        }
    }

    private void initDbQueryLimit() throws UbongoHttpException {
        String value = context.getInitParameter("ubongo.query.limit.default");
        try {
            defaultQueryLimit = value == null ? DEFAULT_QUERY_LIMIT_FALLBACK : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            defaultQueryLimit = DEFAULT_QUERY_LIMIT_FALLBACK;
        } finally {
            defaultQueryLimitRetrieved = true;
        }
    }

    private String getContextParam(String paramName) throws UbongoHttpException {
        String value = context.getInitParameter(paramName);
        if (value == null) {
            throw new UbongoHttpException(500,
                    paramName + " could not be found in Tomcat context. " + TOMCAT_CONTEXT_CONFIGURED);
        }
        return value;
    }

    @GET
    @Path("version")
    @Produces(MediaType.APPLICATION_JSON)
    public String getVersion() {
        return "{\"version\": \"" + APP_VERSION + "\"}";
    }

    @GET
    @Path("machines")
    @Produces(MediaType.APPLICATION_JSON)
    public String getAllMachines() throws UbongoHttpException {
        init();
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

    @POST
    @Path("machines/{machineId}")
    @Produces(MediaType.APPLICATION_JSON)
    public void changeMachineActivityStatus(
            @PathParam("machineId") int machineId,
            @DefaultValue("false") @QueryParam("activate") boolean activate,
            @DefaultValue("false") @QueryParam("deactivate") boolean deactivate) throws UbongoHttpException {
        init();
        if (activate) {
            if (deactivate) {
                throw new UbongoHttpException(400, "Received a POST request to machines/"
                        + machineId + " with both activate and deactivate query params turned on. Please choose only one.");
            }
            try {
                serviceProvider.changeMachineActivityStatus(machineId, true);
            } catch (PersistenceException e) {
                throw new UbongoHttpException(500, "Failed to send activation request for machine with ID=" + machineId);
            }
        } else if (deactivate) {
            try {
                serviceProvider.changeMachineActivityStatus(machineId, false);
            } catch (PersistenceException e) {
                throw new UbongoHttpException(500, "Failed to send deactivation request for machine with ID=" + machineId);
            }
        } else {
            throw new UbongoHttpException(400, "Received a POST request to machines/"
                    + machineId + " without any query param. Please send query param 'activate' or 'deactivate'.");
        }
    }

    @GET
    @Path("analyses/{analysisName}")
    @Produces(MediaType.APPLICATION_JSON)
    public String getAnalysis(@PathParam("analysisName") String analysisName) throws UbongoHttpException {
        init();
        ObjectMapper mapper = new ObjectMapper();
        String response;
        try {
            List<Unit> units = serviceProvider.getAnalysis(analysisName);
            if (units == null) {
                throw new UbongoHttpException(500, "Failed to retrieve analysis from DB.");
            }
            response = mapper.writeValueAsString(units);
        } catch (PersistenceException e) {
            throw new UbongoHttpException(500, "Failed to read analysis from DB. Details: " + e.getMessage());
        } catch (JsonProcessingException e) {
            throw new UbongoHttpException(500, "Failed to serialize analysis units to JSON.");
        }
        return response;
    }

    @GET
    @Path("analyses")
    @Produces(MediaType.APPLICATION_JSON)
    public String getAllAnalysisNames(@DefaultValue("false") @QueryParam("names") boolean names,
                                      @QueryParam("limit") int limit) throws UbongoHttpException {
        if (!names) {
            throw new UbongoHttpException(500, "Invalid request: names=true must be set.");
        }
        init();
        ObjectMapper mapper = new ObjectMapper();
        String response;
        try {
            List<String> analysisNames =
                    serviceProvider.getAllAnalysisNames(limit > 0 ? limit : defaultQueryLimit);
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

    @POST
    @Path("analyses")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public void createAnalysis(String requestBody) throws UbongoHttpException {
        init();
        String analysisName;
        List<Unit> units;
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode jsonNode = mapper.readTree(requestBody);
            if (jsonNode.hasNonNull("analysisName")) {
                analysisName = jsonNode.get("analysisName").asText();
            } else {
                throw new UbongoHttpException(400, "Analysis name cannot be empty nor null.");
            }
            if (jsonNode.hasNonNull("units")) {
                units = mapper.readValue(jsonNode.get("units").toString(),
                        new TypeReference<List<Unit>>() {
                        });
            } else {
                throw new UbongoHttpException(400, "Analysis must contain at least one unit.");
            }
            serviceProvider.createAnalysis(analysisName, units);
        } catch (IOException e) {
            throw new UbongoHttpException(400, "The request is malformed - expected array of unit objects but received: "
                    + requestBody);
        } catch (PersistenceException e) {
            throw new UbongoHttpException(500, "Failed to create analysis in the DB");
        }
    }

    @GET
    @Path("flows")
    @Produces(MediaType.APPLICATION_JSON)
    public String getAllFlows(@QueryParam("limit") int limit) throws UbongoHttpException {
        init();
        ObjectMapper mapper = new ObjectMapper();
        String response;
        try {
            List<FlowData> flows = serviceProvider.getAllFlows(limit > 0 ? limit : defaultQueryLimit);
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
        init();
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
        init();
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
        init();
        ObjectMapper mapper = new ObjectMapper();
        String response;
        try {
            List<Task> tasks = serviceProvider.getAllTasks(limit > 0 ? limit : defaultQueryLimit);
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
        init();
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
        init();
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
        init();
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
        init();
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
        init();
        ObjectMapper mapper = new ObjectMapper();
        String response;
        try {
            List<Unit> units = new ArrayList<>(serviceProvider.getAllUnits().values());
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

    @GET
    @Path("requests")
    @Produces(MediaType.APPLICATION_JSON)
    public String getRequests(@DefaultValue("false") @QueryParam("count") boolean count,
                              @QueryParam("t") Long fromTime,
                              @QueryParam("limit")int limit) throws UbongoHttpException {
        init();
        if (count) {
            try {
                return Integer.toString(serviceProvider
                        .countRequests(new Timestamp(fromTime == null ? 0 : fromTime)));
            } catch (PersistenceException e) {
                throw new UbongoHttpException(500, "Failed to count requests.");
            }
        } else {
            ObjectMapper mapper = new ObjectMapper();
            try {
                List<ExecutionRequest> requests = serviceProvider.getAllRequests(limit);
                return mapper.writeValueAsString(requests);
            } catch (JsonProcessingException e) {
                throw new UbongoHttpException(500, "Failed to serialize requests to JSON.");
            } catch (PersistenceException e) {
                throw new UbongoHttpException(500, "Failed to retrieve requests from the DB.");
            }
        }
    }
}
