package ubongo.common.datatypes;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import ubongo.common.JsonDateSerializer;

import java.util.Date;

public class FlowData {

    private int flowId;
    private Context context;

    @JsonSerialize(using=JsonDateSerializer.class)
    private Date creationDate;
    private FlowStatus status;

    public FlowData(int flowId, Context context, Date creationDate, FlowStatus status) {
        this.flowId = flowId;
        this.setContext(context);
        this.creationDate = creationDate;
        this.status = status;
    }

    public int getFlowId() {
        return flowId;
    }

    public void setFlowId(int flowId) {
        this.flowId = flowId;
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

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }
}
