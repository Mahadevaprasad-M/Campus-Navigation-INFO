import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class ApiHandler implements HttpHandler {

    // Walking speed: 72 metres per minute.
    private static final double WALKING_SPEED = 72.0;

    // Maximum number of steps we ever produce.
    private static final int MAX_STEPS = 200;

    private CampusGraph graph;

    public ApiHandler(CampusGraph graph) {
        this.graph = graph;
    }

    // ---------------------------------------------------------------
    // Main entry: every request to /api/* comes here.
    // ---------------------------------------------------------------
    public void handle(HttpExchange exchange) throws IOException {

        // CORS — allow the frontend to call us.
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        String method = exchange.getRequestMethod();

        // Browsers send OPTIONS first — answer and stop.
        if (method.equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        // Only GET is allowed.
        if (!method.equalsIgnoreCase("GET")) {
            sendJson(exchange, 405, errorJson("method_not_allowed", "Only GET is allowed on /api/*"));
            return;
        }

        String path = exchange.getRequestURI().getPath();

        try {
            if (path.equals("/api/health")) {
                handleHealth(exchange);
            } else if (path.equals("/api/locations")) {
                handleLocations(exchange);
            } else if (path.startsWith("/api/location/")) {
                handleSingleLocation(exchange, path.substring("/api/location/".length()));
            } else if (path.equals("/api/path")) {
                handlePath(exchange);
            } else {
                sendJson(exchange, 404, errorJson("unknown_endpoint", "No handler for " + path));
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, errorJson("internal_error", "Server error: " + e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    // GET /api/health
    // ---------------------------------------------------------------
    private void handleHealth(HttpExchange exchange) throws IOException {
        LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("status", "ok");
        body.put("nodes", graph.getNodeCount());
        body.put("edges", graph.getEdgeCount());
        body.put("algorithm", "dijkstra");
        sendJson(exchange, 200, JsonHelper.obj(body));
    }

    // ---------------------------------------------------------------
    // GET /api/locations — all locations, sorted by name.
    // ---------------------------------------------------------------
    private void handleLocations(HttpExchange exchange) throws IOException {

        ArrayList<CampusGraph.Location> list = graph.getAllLocations();

        // Sort alphabetically (case-insensitive).
        list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));

        // Build a JSON array.
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(locationToJson(list.get(i)));
        }
        sb.append("]");

        sendJson(exchange, 200, sb.toString());
    }

    // ---------------------------------------------------------------
    // GET /api/location/{id}
    // ---------------------------------------------------------------
    private void handleSingleLocation(HttpExchange exchange, String rawId) throws IOException {

        String id = urlDecode(rawId);

        if (id.length() == 0 || !graph.hasNode(id)) {
            sendJson(exchange, 404, errorJson("unknown_location", "No location with id '" + id + "'"));
            return;
        }

        sendJson(exchange, 200, locationToJson(graph.getLocation(id)));
    }

    // ---------------------------------------------------------------
    // GET /api/path?from=X&to=Y[&algo=bfs|dijkstra]
    // ---------------------------------------------------------------
    private void handlePath(HttpExchange exchange) throws IOException {

        HashMap<String, String> query = parseQuery(exchange.getRequestURI().getQuery());

        String from = query.get("from");
        String to = query.get("to");
        String algo = query.get("algo");

        // Must have both 'from' and 'to'.
        if (from == null || from.trim().length() == 0
                || to == null || to.trim().length() == 0) {
            sendJson(exchange, 400, errorJson("missing_params",
                    "Both 'from' and 'to' query parameters are required."));
            return;
        }

        from = from.trim();
        to = to.trim();

        // Both ids must exist in the graph.
        if (!graph.hasNode(from)) {
            sendJson(exchange, 404, errorJson("unknown_from", "Unknown start location: '" + from + "'"));
            return;
        }
        if (!graph.hasNode(to)) {
            sendJson(exchange, 404, errorJson("unknown_to", "Unknown destination: '" + to + "'"));
            return;
        }

        // Pick the algorithm.
        boolean useBfs = (algo != null && algo.equalsIgnoreCase("bfs"));
        CampusGraph.PathResult result = useBfs ? graph.bfs(from, to) : graph.dijkstra(from, to);

        // No route? Send a friendly message.
        if (!result.found) {
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("error", "no_path");
            body.put("message", "No walking route exists between these locations.");
            body.put("from", from);
            body.put("to", to);
            sendJson(exchange, 200, JsonHelper.obj(body));
            return;
        }

        // Build the list of steps.
        ArrayList<Object> steps = buildSteps(result.path, result.totalDistance);

        // Compose the final response.
        LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("from", from);
        body.put("to", to);
        body.put("algorithm", useBfs ? "bfs" : "dijkstra");
        body.put("totalDistance", result.totalDistance);
        body.put("estimatedTimeMinutes", estimateMinutes(result.totalDistance));
        body.put("path", result.path);
        body.put("steps", steps);

        CampusGraph.Location startLoc = graph.getLocation(from);
        CampusGraph.Location endLoc = graph.getLocation(to);
        body.put("startName", startLoc != null ? startLoc.name : from);
        body.put("endName", endLoc != null ? endLoc.name : to);

        sendJson(exchange, 200, JsonHelper.obj(body));
    }

    // ---------------------------------------------------------------
    // Turn a path (list of ids) into a list of human steps.
    // ---------------------------------------------------------------
    private ArrayList<Object> buildSteps(ArrayList<String> path, int totalDistance) {

        ArrayList<Object> steps = new ArrayList<Object>();

        if (path == null || path.size() == 0) return steps;

        // Special case: start and end are the same.
        if (path.size() == 1) {
            String onlyId = path.get(0);
            CampusGraph.Location loc = graph.getLocation(onlyId);
            String name = loc != null ? loc.name : onlyId;

            LinkedHashMap<String, Object> step = new LinkedHashMap<String, Object>();
            step.put("step", 1);
            step.put("instruction", "You are already at " + name + ".");
            step.put("distance", 0);
            step.put("direction", "arrived");
            step.put("nodeId", onlyId);
            step.put("nodeName", name);

            steps.add(step);
            return steps;
        }

        // Normal case: walk each leg.
        int stepNumber = 1;

        for (int i = 0; i < path.size() - 1; i++) {

            if (stepNumber > MAX_STEPS) break;

            String currentId = path.get(i);
            String nextId = path.get(i + 1);

            CampusGraph.Location currentLoc = graph.getLocation(currentId);
            CampusGraph.Location nextLoc = graph.getLocation(nextId);
            String currentName = currentLoc != null ? currentLoc.name : currentId;
            String nextName = nextLoc != null ? nextLoc.name : nextId;

            int legDistance = graph.getEdgeWeight(currentId, nextId);
            if (legDistance < 0) legDistance = 0;

            String direction;
            String instruction;

            if (i == 0) {
                // First leg — no previous node.
                direction = "straight";
                instruction = "Start at " + currentName
                        + " and walk " + legDistance + "m towards " + nextName + ".";
            } else {
                // Middle leg — check the turn.
                String prevId = path.get(i - 1);
                direction = graph.getTurn(prevId, currentId, nextId);

                String turnText;
                if (direction.equals("left")) {
                    turnText = "Turn left at " + currentName;
                } else if (direction.equals("right")) {
                    turnText = "Turn right at " + currentName;
                } else {
                    turnText = "Continue straight past " + currentName;
                }
                instruction = turnText + ", then walk " + legDistance
                        + "m towards " + nextName + ".";
            }

            LinkedHashMap<String, Object> step = new LinkedHashMap<String, Object>();
            step.put("step", stepNumber);
            step.put("instruction", instruction);
            step.put("distance", legDistance);
            step.put("direction", direction);
            step.put("nodeId", nextId);
            step.put("nodeName", nextName);

            steps.add(step);
            stepNumber++;
        }

        // Final arrival step.
        String endId = path.get(path.size() - 1);
        CampusGraph.Location endLoc = graph.getLocation(endId);
        String endName = endLoc != null ? endLoc.name : endId;

        LinkedHashMap<String, Object> arrival = new LinkedHashMap<String, Object>();
        arrival.put("step", stepNumber);
        arrival.put("instruction", "You have arrived at " + endName + ".");
        arrival.put("distance", 0);
        arrival.put("direction", "arrived");
        arrival.put("nodeId", endId);
        arrival.put("nodeName", endName);

        steps.add(arrival);
        return steps;
    }

    // Convert metres to minutes (rounded up). Minimum 1 if distance > 0.
    private int estimateMinutes(int metres) {
        if (metres <= 0) return 0;
        int minutes = (int) Math.ceil(metres / WALKING_SPEED);
        return minutes < 1 ? 1 : minutes;
    }

    // Turn a Location into a JSON object string.
    private String locationToJson(CampusGraph.Location loc) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", loc.id);
        m.put("name", loc.name);
        m.put("x", loc.x);
        m.put("y", loc.y);
        m.put("type", loc.type);
        m.put("hours", loc.hours);
        return JsonHelper.obj(m);
    }

    // Build a simple {error, message} JSON string.
    private String errorJson(String code, String message) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("error", code);
        m.put("message", message);
        return JsonHelper.obj(m);
    }

    // Send a JSON response.
    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.close();
    }

    // Parse "from=entrance&to=library" into a HashMap.
    private HashMap<String, String> parseQuery(String query) {

        HashMap<String, String> result = new HashMap<String, String>();

        if (query == null || query.length() == 0) return result;

        for (String pair : query.split("&")) {
            if (pair.length() == 0) continue;

            int eq = pair.indexOf("=");
            String key, value;

            if (eq < 0) {
                key = pair;
                value = "";
            } else {
                key = pair.substring(0, eq);
                value = pair.substring(eq + 1);
            }

            result.put(urlDecode(key), urlDecode(value));
        }

        return result;
    }

    // URL-decode a string, safe on null.
    private String urlDecode(String text) {
        if (text == null) return "";
        try {
            return URLDecoder.decode(text, "UTF-8");
        } catch (Exception e) {
            return text;
        }
    }
}