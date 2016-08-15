package ubongo.rest;

public enum ResourceAction {

    CANCEL ("cancel"), RESUME ("resume"), RUN ("run"), STOP ("stop");

    private String name;

    ResourceAction(String name) {
        this.name = name;
    }

    // required by Jersey
    public static ResourceAction fromString(final String s) {
        return ResourceAction.valueOf(s.toUpperCase());
    }

    @Override
    public String toString() {
        return name;
    }

}
