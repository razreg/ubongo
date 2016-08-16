package ubongo.persistence.db;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Properties;

public class QueriesProvider {

    private String propFilePath = null;
    private Properties props;

    public QueriesProvider(String path) {
        propFilePath = path;
    }

    private Properties getQueries() throws SQLException {
        if (props == null) {
            String errorMsg = "Unable to load property file from path: " + propFilePath;
            try (InputStream is = new FileInputStream(propFilePath)) {
                props = new Properties();
                props.load(is);
            } catch (FileNotFoundException e) {
                throw new SQLException(errorMsg);
            } catch (IOException e) {
                throw new SQLException(errorMsg + ". Details: " + e.getMessage());
            }
        }
        return props;
    }

    public String getQuery(String query) throws SQLException{
        return getQueries().getProperty(query);
    }

}
