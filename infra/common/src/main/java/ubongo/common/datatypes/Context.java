package ubongo.common.datatypes;

import java.io.Serializable;

public class Context implements Serializable, Cloneable {

    private String study;
    private String subject;
    private String run;

    public Context(String study, String subject, String run) {
        this.study = study;
        this.subject = subject;
        this.run = run;
    }

    public Context() {}

    public String getStudy() {
        return study;
    }

    public void setStudy(String study) {
        this.study = study;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getRun() {
        return run;
    }

    public void setRun(String run) {
        this.run = run;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
