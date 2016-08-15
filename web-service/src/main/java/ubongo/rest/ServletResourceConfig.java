package ubongo.rest;

import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.ApplicationPath;

@ApplicationPath("rest")
public class ServletResourceConfig extends ResourceConfig {
    public ServletResourceConfig() {
        packages("ubongo.rest");
    }
}
