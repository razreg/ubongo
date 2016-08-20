package ubongo.common.datatypes;

import java.io.Serializable;

public class ExecutionRequest implements Serializable {

    private int id;
    private int entityId;
    private Action action;
    private Status status;
    private java.sql.Timestamp creationTime;
    private java.sql.Timestamp lastUpdated;

    public ExecutionRequest() {}

    public ExecutionRequest(int entityId, Action action) {
        this.id = 0;
        this.entityId = entityId;
        this.action = action;
        this.status = Status.NEW;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public java.sql.Timestamp getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(java.sql.Timestamp creationTime) {
        this.creationTime = creationTime;
    }

    public java.sql.Timestamp getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(java.sql.Timestamp lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public enum Action implements Serializable {

        CANCEL_TASK ("Cancel_Task"),
        KILL_TASK ("Kill_Task"),
        RESUME_TASK ("Resume_Task"),
        RUN_FLOW ("Run_Flow"),
        CANCEL_FLOW ("Cancel_Flow"),
        ACTIVATE_MACHINE ("Activate_Machine"),
        DEACTIVATE_MACHINE ("Deactivate_Machine");

        private String name;

        Action(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public static Action fromString(String name) {
            if (name != null) {
                for (Action a : Action.values()) {
                    if (name.equalsIgnoreCase(a.name)) {
                        return a;
                    }
                }
            }
            return null;
        }
    }

    public enum Status implements Serializable {
        NEW("New"), COMPLETED("Executed"), FAILED("Failed");

        private String name;

        Status(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public static Status fromString(String name) {
            if (name != null) {
                for (Status a : Status.values()) {
                    if (name.equalsIgnoreCase(a.name)) {
                        return a;
                    }
                }
            }
            return null;
        }
    }
}
