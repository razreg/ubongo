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
import ubongo.persistence.exceptions.PersistenceException;

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
                logAndWrapException(500, "Failed to retrieve servlet context. " + TOMCAT_CONTEXT_CONFIGURED);
            }
            String configPath = getContextParam("ubongo.config");
            String unitsPath = getContextParam("ubongo.units.dir");
            String queriesPath = getContextParam("ubongo.db.queries");
            Configuration configuration = null;
            try {
                configuration = Configuration.loadConfiguration(configPath);
            } catch (UnmarshalException e) {
                logAndWrapException(500, FAILURE_MSG, e);
            }
            if (configuration != null) {
                serviceProvider = new ServiceProviderImpl(configuration, unitsPath, queriesPath, configuration.getDebug());
            }
            serviceProvider.start();
        } catch (UbongoHttpException e) {
            serviceProvider = null;
            throw e;
        } catch (Exception e) {
            logAndWrapException(500,
                    "The service has encountered an unexpected exception during initialization", e);
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
            logAndWrapException(500,
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
        String response = "[]";
        try {
            List<Machine> machines = serviceProvider.getAllMachines();
            if (machines == null) {
                logAndWrapException(500, "Failed to retrieve machines.");
            }
            response = mapper.writeValueAsString(machines);
        } catch (JsonProcessingException e) {
            logAndWrapException(500, "Failed to serialize machines to JSON.", e);
        } catch (Exception e) {
            logAndWrapException(500, "Failed to retrieve machines from DB.", e);
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
                logAndWrapException(400, "Received a POST request to machines/"
                        + machineId + " with both activate and deactivate query params turned on. Please choose only one.");
            }
            try {
                serviceProvider.changeMachineActivityStatus(machineId, true);
            } catch (Exception e) {
                logAndWrapException(500, "Failed to send activation request for machine with ID=" + machineId, e);
            }
        } else if (deactivate) {
            try {
                serviceProvider.changeMachineActivityStatus(machineId, false);
            } catch (Exception e) {
                logAndWrapException(500, "Failed to send deactivation request for machine with ID=" + machineId, e);
            }
        } else {
            logAndWrapException(400, "Received a POST request to machines/"
                    + machineId + " without any query param. Please send query param 'activate' or 'deactivate'.");
        }
    }

    @GET
    @Path("analyses/{analysisName}")
    @Produces(MediaType.APPLICATION_JSON)
    public String getAnalysis(@PathParam("analysisName") String analysisName) throws UbongoHttpException {
        init();
        ObjectMapper mapper = new ObjectMapper();
        String response = "[]";
        try {
            List<Unit> units = serviceProvider.getAnalysis(analysisName);
            if (units == null) {
                logAndWrapException(500, "Failed to retrieve analysis from DB.");
            }
            response = mapper.writeValueAsString(units);
        } catch (JsonProcessingException e) {
            logAndWrapException(500, "Failed to serialize analysis units to JSON.", e);
        } catch (Exception e) {
            logAndWrapException(500, "Failed to read analysis from DB.", e);
        }
        return response;
    }

    @GET
    @Path("analyses")
    @Produces(MediaType.APPLICATION_JSON)
    public String getAllAnalysisNames(@DefaultValue("false") @QueryParam("names") boolean names,
                                      @QueryParam("limit") int limit) throws UbongoHttpException {
        if (!names) {
            logAndWrapException(500, "Invalid request: names=true must be set.");
        }
        init();
        ObjectMapper mapper = new ObjectMapper();
        String response = "[]";
        try {
            List<String> analysisNames =
                    serviceProvider.getAllAnalysisNames(limit > 0 ? limit : defaultQueryLimit);
            if (analysisNames == null) {
                logAndWrapException(500, "Failed to retrieve analysis names from DB.");
            }
            response = mapper.writeValueAsString(analysisNames);
        } catch (JsonProcessingException e) {
            logAndWrapException(500, "Failed to serialize analysis names to JSON.", e);
        } catch (Exception e) {
            logAndWrapException(500, "Failed to read analysis names from DB.", e);
        }
        return response;
    }

    @POST
    @Path("analyses")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public void createAnalysis(String requestBody) throws UbongoHttpException {
        init();
        String analysisName = null;
        List<Unit> units = null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode jsonNode = mapper.readTree(requestBody);
            if (jsonNode.hasNonNull("analysisName")) {
                analysisName = jsonNode.get("analysisName").asText();
            } else {
                logAndWrapException(400, "Analysis name cannot be empty nor null.");
            }
            if (jsonNode.hasNonNull("units")) {
                units = mapper.readValue(jsonNode.get("units").toString(),
                        new TypeReference<List<Unit>>() {
                        });
            } else {
                logAndWrapException(400, "Analysis must contain at least one unit.");
            }
            serviceProvider.createAnalysis(analysisName, units);
        } catch (IOException e) {
            logAndWrapException(400, "The request is malformed - expected array of unit objects but received: "
                    + requestBody, e);
        } catch (Exception e) {
            logAndWrapException(500, "Failed to create analysis in the DB", e);
        }
    }

    @GET
    @Path("flows")
    @Produces(MediaType.APPLICATION_JSON)
    public String getAllFlows(@QueryParam("limit") int limit) throws UbongoHttpException {
        init();
        ObjectMapper mapper = new ObjectMapper();
        String response = "[]";
        try {
            List<FlowData> flows = serviceProvider.getAllFlows(limit > 0 ? limit : defaultQueryLimit);
            if (flows == null) {
                logAndWrapException(500, "Failed to retrieve flows from DB.");
            }
            response = mapper.writeValueAsString(flows);
        } catch (JsonProcessingException e) {
            logAndWrapException(500, "Failed to serialize flows to JSON.", e);
        } catch (Exception e) {
            logAndWrapException(500, "Failed to read flows from DB.", e);
        }
        return response;
    }

    @POST
    @Path("flows")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String createFlow(String requestBody) throws UbongoHttpException {
        init();
        int flow = -1;
        ubongo.common.datatypes.Context context = null;
        List<Task> tasks = null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode jsonNode = mapper.readTree(requestBody);
            if (jsonNode.hasNonNull("context")) {
                context = mapper.readValue(jsonNode.get("context").toString(),
                        new TypeReference<ubongo.common.datatypes.Context>(){});
            } else {
                logAndWrapException(400, "Study name for flow cannot be empty nor null.");
            }
            if (jsonNode.hasNonNull("tasks")) {
                tasks = mapper.readValue(jsonNode.get("tasks").toString(),
                        new TypeReference<List<Task>>(){});
            } else {
                logAndWrapException(400, "Flow must contain at least one task.");
            }
            flow = serviceProvider.createFlow(context, tasks);
        } catch (IOException e) {
            logAndWrapException(500, "Failed to deserialize JSON to FlowData.", e);
        } catch (Exception e) {
            logAndWrapException(500, "Failed to create flow in DB.", e);
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
                } catch (Exception e) {
                    logAndWrapException(500, "Failed to cancel flow.", e);
                }
                break;
            case RUN:
                try {
                    serviceProvider.runFlow(flowId);
                } catch (Exception e) {
                    logAndWrapException(500, "Failed to run flow.", e);
                }
                break;
            default:
                logAndWrapException(405, "Unsupported action on flow (" + action.name() + ").");
        }
    }

    @GET
    @Path("flows/all/tasks")
    @Produces(MediaType.APPLICATION_JSON)
    public String getAllTasks(@QueryParam("limit") int limit) throws UbongoHttpException {
        init();
        ObjectMapper mapper = new ObjectMapper();
        String response = "[]";
        try {
            List<Task> tasks = serviceProvider.getAllTasks(limit > 0 ? limit : defaultQueryLimit);
            if (tasks == null) {
                logAndWrapException(500, "Failed to retrieve tasks from DB.");
            }
            response = mapper.writeValueAsString(tasks);
        } catch (JsonProcessingException e) {
            logAndWrapException(500, "Failed to serialize tasks to JSON.", e);
        } catch (Exception e) {
            logAndWrapException(500, "Failed to read tasks from DB.", e);
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
        String response = "[]";
        try {
            List<Task> tasks = serviceProvider.getTasks(flowId);
            if (tasks == null) {
                logAndWrapException(500, "Failed to retrieve tasks.");
            }
            response = mapper.writeValueAsString(tasks);
        }
        catch (JsonProcessingException e) {
            logAndWrapException(500, "Failed to serialize tasks to JSON.", e);
        } catch (Exception e) {
            logAndWrapException(500, "Failed to read tasks from DB.", e);
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
        Task task = null;
        try {
            task = serviceProvider.getTask(taskId);
        } catch (PersistenceException e) {
            logAndWrapException(500, "Failed to retrieve task from DB.", e);
        }
        if (task == null || task.getFlowId() != flowId) {
            // task cannot be null at this point
            logAndWrapException(400, "The taskId does not match the flowId.");
        }
        switch (action) {
            case CANCEL:
                try {
                    serviceProvider.cancelTask(task);
                } catch (Exception e) {
                    logAndWrapException(500, "Failed to cancel tasks.", e);
                }
                break;
            case RESUME:
                try {
                    serviceProvider.resumeTask(task);
                } catch (Exception e) {
                    logAndWrapException(500, "Failed to resume task.", e);
                }
                break;
            case STOP:
                try {
                    serviceProvider.killTask(task);
                } catch (Exception e) {
                    logAndWrapException(500, "Failed to stop task.", e);
                }
                break;
            default:
                logAndWrapException(405, "Unsupported action on flow (" + action.name() + ").");
        }
    }

    @GET
    @Path("units")
    @Produces(MediaType.APPLICATION_JSON)
    public String getAllUnits() throws UbongoHttpException {
        init();
        ObjectMapper mapper = new ObjectMapper();
        String response = "{}";
        try {
            List<Unit> units = new ArrayList<>(serviceProvider.getAllUnits().values());
            response = mapper.writeValueAsString(units);
        } catch (JsonProcessingException e) {
            logAndWrapException(500, "Failed to serialize units to JSON.", e);
        } catch (Exception e) {
            logAndWrapException(500, "One or more units could not be read.", e);
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
            } catch (Exception e) {
                logAndWrapException(500, "Failed to count requests.", e);
            }
        } else {
            ObjectMapper mapper = new ObjectMapper();
            try {
                List<ExecutionRequest> requests = serviceProvider.getAllRequests(limit);
                return mapper.writeValueAsString(requests);
            } catch (JsonProcessingException e) {
                logAndWrapException(500, "Failed to serialize requests to JSON.", e);
            } catch (Exception e) {
                logAndWrapException(500, "Failed to retrieve requests from the DB.", e);
            }
        }
        return "{}";
    }

    @POST
    @Path("units/{unitId}")
    @Produces(MediaType.APPLICATION_JSON)
    public void generateBashForUnit(@PathParam("unitId") int unitId) throws UbongoHttpException {
        init();
        try {
            serviceProvider.generateBashFileForUnit(unitId);
        } catch (Exception e) {
            logAndWrapException(500, "Failed to send request for bash file generation.", e);
        }
    }

    private static void logAndWrapException(int status, String msg) throws UbongoHttpException {
        logAndWrapException(status, msg, null);
    }

    private static void logAndWrapException(int status, String msg, Throwable t) throws UbongoHttpException {
        String logMsg = "Status: " + status + ". Message: " + msg;
        if (t != null) {
            logger.error(logMsg, t);
            throw new UbongoHttpException(status, msg, t);
        } else {
            logger.error(logMsg);
            throw new UbongoHttpException(status, msg);
        }
    }
}
