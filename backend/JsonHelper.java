import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class JsonHelper {

    // ---------------------------------------------------------------
    // 1. Escape text so it is safe inside JSON strings.
    //    Order matters: backslash first, then the rest.
    // ---------------------------------------------------------------
    public static String escape(String text) {
        if (text == null) return "";

        String result = text;
        result = result.replace("\\", "\\\\");  // \  -> \\
        result = result.replace("\"", "\\\"");  // "  -> \"
        result = result.replace("\n", "\\n");   // newline -> \n
        result = result.replace("\r", "\\r");   // carriage return -> \r
        result = result.replace("\t", "\\t");   // tab -> \t
        result = result.replace("\b", "\\b");   // backspace -> \b
        result = result.replace("\f", "\\f");   // form feed -> \f
        return result;
    }

    // ---------------------------------------------------------------
    // 2. Wrap a string in double quotes (or emit null).
    // ---------------------------------------------------------------
    public static String str(String text) {
        if (text == null) return "null";
        return "\"" + escape(text) + "\"";
    }

    // ---------------------------------------------------------------
    // 3. Convert simple types to JSON text.
    // ---------------------------------------------------------------
    public static String num(int value) {
        return Integer.toString(value);
    }

    public static String num(long value) {
        return Long.toString(value);
    }

    public static String num(double value) {
        // Whole numbers print without ".0" (no scientific notation).
        if (value == Math.floor(value)) return Long.toString((long) value);
        return Double.toString(value);
    }

    public static String bool(boolean value) {
        return value ? "true" : "false";
    }

    // ---------------------------------------------------------------
    // 4. Convert any Object to JSON text (type dispatch).
    // ---------------------------------------------------------------
    public static String value(Object obj) {
        if (obj == null)                      return "null";
        if (obj instanceof String)            return str((String) obj);
        if (obj instanceof Integer)           return num(((Integer) obj).intValue());
        if (obj instanceof Long)              return num(((Long) obj).longValue());
        if (obj instanceof Double)            return num(((Double) obj).doubleValue());
        if (obj instanceof Float)             return num(((Float) obj).doubleValue());
        if (obj instanceof Boolean)           return bool(((Boolean) obj).booleanValue());
        if (obj instanceof ArrayList)         return array((ArrayList) obj);
        if (obj instanceof HashMap)           return obj((HashMap) obj);
        if (obj instanceof LinkedHashMap)     return obj((LinkedHashMap) obj);

        // Fallback: convert to string.
        return str(obj.toString());
    }

    // ---------------------------------------------------------------
    // 5. Build a JSON object { "key": value, ... }.
    //    LinkedHashMap keeps the original key order.
    // ---------------------------------------------------------------
    public static String obj(LinkedHashMap<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        for (String key : map.keySet()) {
            if (!first) sb.append(",");
            first = false;

            sb.append(str(key));
            sb.append(":");
            sb.append(value(map.get(key)));
        }

        sb.append("}");
        return sb.toString();
    }

    // Same thing, but for a plain HashMap (copy into a LinkedHashMap first).
    public static String obj(HashMap<String, Object> map) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<String, Object>();
        for (String key : map.keySet()) {
            copy.put(key, map.get(key));
        }
        return obj(copy);
    }

    // ---------------------------------------------------------------
    // 6. Build a JSON array [ value, value, ... ].
    // ---------------------------------------------------------------
    public static String array(ArrayList list) {
        if (list == null || list.isEmpty()) return "[]";

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(value(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // 7. Pretty print (optional, for debugging in a browser).
    //    Adds newlines and indentation after { [ , and before } ].
    // ---------------------------------------------------------------
    public static String pretty(String compact) {
        if (compact == null || compact.length() == 0) return compact;

        StringBuilder out = new StringBuilder();
        int indent = 0;
        boolean insideString = false;

        for (int i = 0; i < compact.length(); i++) {
            char c = compact.charAt(i);

            // Toggle string mode when we see an unescaped quote.
            if (c == '"' && (i == 0 || compact.charAt(i - 1) != '\\')) {
                insideString = !insideString;
                out.append(c);
                continue;
            }

            // Everything inside a string is copied as-is.
            if (insideString) {
                out.append(c);
                continue;
            }

            if (c == '{' || c == '[') {
                out.append(c);
                indent++;
                out.append("\n").append(spaces(indent * 2));
            } else if (c == '}' || c == ']') {
                indent--;
                if (indent < 0) indent = 0;
                out.append("\n").append(spaces(indent * 2)).append(c);
            } else if (c == ',') {
                out.append(c).append("\n").append(spaces(indent * 2));
            } else if (c == ':') {
                out.append(": ");
            } else {
                out.append(c);
            }
        }

        return out.toString();
    }

    // Helper: a string of N spaces.
    private static String spaces(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(" ");
        return sb.toString();
    }
}