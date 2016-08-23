package ubongo.common;

public class Utils {

    public static String concatStrings(String ... strings) {
        StringBuilder sb = new StringBuilder();
        for (String string : strings) {
            sb.append(string);
        }
        return sb.toString();
    }

    public static String concatStrings(Object ... objects) {
        StringBuilder sb = new StringBuilder();
        for (Object object : objects) {
            sb.append(object.toString());
        }
        return sb.toString();
    }

}
