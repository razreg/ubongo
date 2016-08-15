package ubongo.common;

public class Utils {

    public static String concatStrings(String ... strings) {
        StringBuilder sb = new StringBuilder();
        for (String string : strings) {
            sb.append(string);
        }
        return sb.toString();
    }

}
