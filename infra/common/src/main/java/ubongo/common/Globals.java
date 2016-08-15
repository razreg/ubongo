package ubongo.common;

import java.nio.file.Paths;

public class Globals {

    public static final String CATALINA = System.getProperty("catalina.home"); // should be catalina.base on server and catalina.home from intelliJ
    public static final String SERVICE_SOURCES_ROOT =
            Paths.get(CATALINA, "webapps/ROOT/WEB-INF/classes").toString();

}
