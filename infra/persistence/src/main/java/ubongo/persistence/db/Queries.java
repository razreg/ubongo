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
    public static final String propFileName = "db/queries.properties";
    private static Properties props;

    public static Properties getQueries() throws SQLException {
        if (props == null) {
            String errorMsg = "Unable to load property file: " + propFileName;
            InputStream is;
            try {
                if (propFilePath == null) {
                    // TODO get path in a better way
                    Path path = Paths.get(Queries.class.getProtectionDomain().getCodeSource()
                            .getLocation().toURI());
                    propFilePath = Paths.get(path.toString(), propFileName).toString();
                }
                errorMsg = "Unable to load property file: " + propFilePath;
                is = new FileInputStream(propFilePath);
            } catch (FileNotFoundException | URISyntaxException e) {
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
