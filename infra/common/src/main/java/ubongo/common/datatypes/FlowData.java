package ubongo.common.datatypes;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import ubongo.common.JsonDateSerializer;

import java.util.Date;

public class FlowData {

    private int flowId;
    private String studyName;

    @JsonSerialize(using=JsonDateSerializer.class)
    private Date creationDate;
    private FlowStatus status;

    public FlowData(int flowId, String studyName, Date creationDate, FlowStatus status) {
        this.flowId = flowId;
        this.studyName = studyName;
        this.creationDate = creationDate;
        this.status = status;
    }

    public int getFlowId() {
        return flowId;
    }

    public void setFlowId(int flowId) {
        this.flowId = flowId;
    }

    public String getStudyName() {
        return studyName;
    }

    public void setStudyName(String studyName) {
        this.studyName = studyName;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public FlowStatus getStatus() {
        return status;
    }

    public void setStatus(FlowStatus status) {
        this.status = status;
    }
}
