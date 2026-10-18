import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class LocationLoader {

    private CampusGraph graph;
    private String dataFolder;   // where to look for the JSON files

    public LocationLoader(CampusGraph graph, String dataFolder) {
        this.graph = graph;
        this.dataFolder = (dataFolder == null || dataFolder.length() == 0) ? "data" : dataFolder;
    }

    // ---------------------------------------------------------------
    // Load everything (locations + edges) and print a summary.
    // ---------------------------------------------------------------
    public void loadAll() throws IOException {

        int locationCount = loadLocations();
        int edgeCount = loadEdges();

        System.out.println("[LocationLoader] Loaded " + locationCount
                + " locations and " + edgeCount + " edges");
        System.out.println("[CampusGraph] Nodes = " + graph.getNodeCount()
                + ",  Edges = " + graph.getEdgeCount());
    }

    // ---------------------------------------------------------------
    // Load locations.json.
    // Format: { "id" : { "name": ..., "x": ..., "y": ..., ... }, ... }
    //
    // For each block we do:
    //   1. find quoted string  -> id
    //   2. find { after it
    //   3. find matching }
    //   4. read fields from the body
    // ---------------------------------------------------------------
    public int loadLocations() throws IOException {

        String rawText = readFile("locations.json");
        String json = stripComments(rawText);

        int count = 0;
        int position = 0;

        while (position < json.length()) {

            // Step 1: find the next quoted string (the id).
            int openQuote = json.indexOf("\"", position);
            if (openQuote < 0) break;

            int closeQuote = json.indexOf("\"", openQuote + 1);
            if (closeQuote < 0) break;

            String id = json.substring(openQuote + 1, closeQuote);

            // Step 2: find the opening brace { after the id.
            int openBrace = json.indexOf("{", closeQuote + 1);
            if (openBrace < 0) break;

            // Step 3: find the matching closing brace }.
            int closeBrace = json.indexOf("}", openBrace + 1);
            if (closeBrace < 0) break;

            // Step 4: extract the body and read its fields.
            String body = json.substring(openBrace + 1, closeBrace);

            String name = getStringValue(body, "name");
            int x = getIntValue(body, "x", 0);
            int y = getIntValue(body, "y", 0);
            String type = getStringValue(body, "type");
            String hours = getStringValue(body, "hours");

            if (name == null) name = id;
            if (type == null) type = "unknown";

            graph.addLocation(new CampusGraph.Location(id, name, x, y, type, hours));

            count++;
            position = closeBrace + 1;
        }

        return count;
    }

    // ---------------------------------------------------------------
    // Load edges.json.
    // Format: [ { "from": ..., "to": ..., "distance": ... }, ... ]
    //
    // For each { ... } block we read "from", "to", "distance".
    // ---------------------------------------------------------------
    public int loadEdges() throws IOException {

        String rawText = readFile("edges.json");
        String json = stripComments(rawText);

        int count = 0;
        int position = 0;

        while (position < json.length()) {

            // Find the next opening brace.
            int openBrace = json.indexOf("{", position);
            if (openBrace < 0) break;

            // Find the matching closing brace.
            int closeBrace = json.indexOf("}", openBrace + 1);
            if (closeBrace < 0) break;

            // Extract the body.
            String body = json.substring(openBrace + 1, closeBrace);

            String from = getStringValue(body, "from");
            String to = getStringValue(body, "to");
            int distance = getIntValue(body, "distance", 0);

            // Only add if both ends are known.
            if (from != null && to != null) {
                if (distance <= 0) distance = 10;   // sensible default
                graph.addEdge(from, to, distance);
                count++;
            }

            position = closeBrace + 1;
        }

        return count;
    }

    // ---------------------------------------------------------------
    // Read a file. Try several likely paths so the app works
    // whether it is run from the project root, backend/, etc.
    // ---------------------------------------------------------------
    private String readFile(String filename) throws IOException {

        ArrayList<String> candidates = new ArrayList<String>();
        candidates.add(dataFolder + "/" + filename);
        candidates.add("data/" + filename);
        candidates.add("backend/data/" + filename);
        candidates.add("../backend/data/" + filename);
        candidates.add("src/backend/data/" + filename);

        for (String pathText : candidates) {
            Path path = Paths.get(pathText);
            if (Files.exists(path) && Files.isRegularFile(path)) {
                byte[] bytes = Files.readAllBytes(path);
                String content = new String(bytes, StandardCharsets.UTF_8);
                System.out.println("[LocationLoader] Reading: " + path.toAbsolutePath());
                return content;
            }
        }

        throw new IOException("Could not find file '" + filename
                + "'. Tried: " + candidates);
    }

    // ---------------------------------------------------------------
    // Remove // line comments and /* ... */ block comments.
    // JSON normally does not allow comments, but our data files do.
    // ---------------------------------------------------------------
    private String stripComments(String text) {

        StringBuilder result = new StringBuilder();
        int i = 0;

        while (i < text.length()) {

            char c = text.charAt(i);

            // Block comment: /* ... */
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                i += 2;                             // skip /*
                while (i + 1 < text.length()) {
                    if (text.charAt(i) == '*' && text.charAt(i + 1) == '/') {
                        i += 2;                     // skip */
                        break;
                    }
                    i++;
                }
            }
            // Line comment: // ...
            else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                while (i < text.length() && text.charAt(i) != '\n') i++;
            }
            // Normal character.
            else {
                result.append(c);
                i++;
            }
        }

        return result.toString();
    }

    // ---------------------------------------------------------------
    // Extract a string field.
    // Example body: "name" : "Main Entrance", "x" : 80
    // For key "name"  -> "Main Entrance".
    // Returns null if the key is not found.
    // ---------------------------------------------------------------
    private String getStringValue(String body, String key) {

        String searchText = "\"" + key + "\"";

        int keyPosition = body.indexOf(searchText);
        if (keyPosition < 0) return null;

        int colonPosition = body.indexOf(":", keyPosition + searchText.length());
        if (colonPosition < 0) return null;

        int openQuote = body.indexOf("\"", colonPosition + 1);
        if (openQuote < 0) return null;

        int closeQuote = body.indexOf("\"", openQuote + 1);
        if (closeQuote < 0) return null;

        return body.substring(openQuote + 1, closeQuote);
    }

    // ---------------------------------------------------------------
    // Extract an integer field.
    // Example body: "name" : "X", "x" : 80
    // For key "x"  -> 80.
    // Returns defaultValue if the key or the number is missing.
    // ---------------------------------------------------------------
    private int getIntValue(String body, String key, int defaultValue) {

        String searchText = "\"" + key + "\"";
        int keyPosition = body.indexOf(searchText);
        if (keyPosition < 0) return defaultValue;

        int colonPosition = body.indexOf(":", keyPosition + searchText.length());
        if (colonPosition < 0) return defaultValue;

        // Skip any spaces after the colon.
        int start = colonPosition + 1;
        while (start < body.length() && body.charAt(start) == ' ') start++;

        // Optional minus sign.
        int end = start;
        if (end < body.length() && body.charAt(end) == '-') end++;

        // Read the digits.
        while (end < body.length() && Character.isDigit(body.charAt(end))) end++;

        if (end == start) return defaultValue;      // no digits found

        String numberText = body.substring(start, end);
        try {
            return Integer.parseInt(numberText);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}