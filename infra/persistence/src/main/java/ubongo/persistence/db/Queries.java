package ubongo.persistence.db;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Properties;

public class Queries {

    public static String propFilePath = null;
    private static Properties props;

    // TODO make this not static and pass a path to the queries.properties file in the constructor

    public static Properties getQueries() throws SQLException {
        if (props == null) {
            String errorMsg = "Unable to load property file"; // TODO improve msg
            InputStream is;
            try {
                if (propFilePath == null) {
                    /*
                    // TODO get path in a better way
                    Path path = Paths.get(Queries.class.getProtectionDomain().getCodeSource()
                            .getLocation().toURI());
                    propFilePath = Paths.get(path.toString(), propFileName).toString();
                    */
                    propFilePath = "/specific/netapp5/hezi/razregev/db/queries.properties";
                }
                errorMsg = "Unable to load property file: " + propFilePath;
                is = new FileInputStream(propFilePath);
            } catch (FileNotFoundException e) {
                throw new SQLException(errorMsg);
            }
            props = new Properties();
            try {
                props.load(is);
            } catch (IOException e) {
                throw new SQLException(errorMsg + ". Details: " + e.getMessage());
            }
        }
        return props;
    }

    public static String getQuery(String query) throws SQLException{
        return getQueries().getProperty(query);
    }

}
