import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class CampusGraph {

    // ---- Simple data holders ----

    // A place on campus (library, canteen, ...).
    public static class Location {
        public String id;      // short code like "library"
        public String name;    // full name like "Central Library"
        public int x;          // x coordinate on map
        public int y;          // y coordinate on map
        public String type;    // gate, building, lab, office, facility
        public String hours;   // like "9AM-6PM", may be null

        public Location(String id, String name, int x, int y, String type, String hours) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.type = type;
            this.hours = hours;
        }
    }

    // A path from one place to another (distance in metres).
    public static class Edge {
        public String to;      // destination id
        public int distance;   // metres

        public Edge(String to, int distance) {
            this.to = to;
            this.distance = distance;
        }
    }

    // The answer to "find shortest path".
    public static class PathResult {
        public ArrayList<String> path;   // ordered list of ids
        public int totalDistance;        // metres (or hops for BFS)
        public boolean found;            // true if a path exists

        public PathResult(ArrayList<String> path, int totalDistance, boolean found) {
            this.path = path;
            this.totalDistance = totalDistance;
            this.found = found;
        }
    }

    // ---- Data storage ----
    // locations  : id -> Location
    // adjacency  : id -> list of edges leaving that place

    private HashMap<String, Location> locations;
    private HashMap<String, ArrayList<Edge>> adjacency;

    public CampusGraph() {
        locations = new HashMap<String, Location>();
        adjacency = new HashMap<String, ArrayList<Edge>>();
    }

    // ---- Adding data ----

    // Add a place to the graph.
    public void addLocation(Location loc) {
        if (loc == null || loc.id == null) return;

        locations.put(loc.id, loc);

        if (!adjacency.containsKey(loc.id)) {
            adjacency.put(loc.id, new ArrayList<Edge>());
        }
    }

    // Add a path between two places (both directions).
    public void addEdge(String from, String to, int distance) {
        if (from == null || to == null) return;
        if (from.equals(to)) return;       // no self loops
        if (distance < 1) distance = 1;    // distance must be positive

        // Make sure both endpoints exist.
        if (!adjacency.containsKey(from)) {
            addLocation(new Location(from, from, 0, 0, "unknown", null));
        }
        if (!adjacency.containsKey(to)) {
            addLocation(new Location(to, to, 0, 0, "unknown", null));
        }

        adjacency.get(from).add(new Edge(to, distance));
        adjacency.get(to).add(new Edge(from, distance));
    }

    // ---- Lookup helpers ----

    public boolean hasNode(String id) {
        if (id == null) return false;
        return adjacency.containsKey(id);
    }

    public Location getLocation(String id) {
        return locations.get(id);
    }

    public ArrayList<Location> getAllLocations() {
        // A fresh list, so callers can sort it safely.
        return new ArrayList<Location>(locations.values());
    }

    public ArrayList<Edge> getNeighbours(String id) {
        ArrayList<Edge> list = adjacency.get(id);
        if (list == null) return new ArrayList<Edge>();
        return list;
    }

    public int getNodeCount() {
        return adjacency.size();
    }

    public int getEdgeCount() {
        // Each edge is stored twice (once per direction).
        int total = 0;
        for (String id : adjacency.keySet()) {
            total += adjacency.get(id).size();
        }
        return total / 2;
    }

    // Direct distance between two neighbours, or -1 if not connected.
    public int getEdgeWeight(String a, String b) {
        ArrayList<Edge> list = adjacency.get(a);
        if (list == null) return -1;

        for (Edge e : list) {
            if (e.to.equals(b)) return e.distance;
        }
        return -1;
    }

    // ---- BFS: shortest path by number of stops ----
    public PathResult bfs(String start, String end) {

        if (!hasNode(start) || !hasNode(end)) {
            return new PathResult(new ArrayList<String>(), 0, false);
        }

        if (start.equals(end)) {
            ArrayList<String> single = new ArrayList<String>();
            single.add(start);
            return new PathResult(single, 0, true);
        }

        Queue<String> queue = new LinkedList<String>();
        Set<String> visited = new HashSet<String>();
        HashMap<String, String> parent = new HashMap<String, String>();

        queue.add(start);
        visited.add(start);

        boolean reached = false;

        while (!queue.isEmpty()) {
            String current = queue.remove();

            if (current.equals(end)) {
                reached = true;
                break;
            }

            for (Edge edge : getNeighbours(current)) {
                if (!visited.contains(edge.to)) {
                    visited.add(edge.to);
                    parent.put(edge.to, current);
                    queue.add(edge.to);
                }
            }
        }

        if (!reached) {
            return new PathResult(new ArrayList<String>(), 0, false);
        }

        // Rebuild the path by walking backwards through parent.
        ArrayList<String> path = new ArrayList<String>();
        String step = end;
        while (step != null) {
            path.add(0, step);
            step = parent.get(step);
        }

        int hops = path.size() - 1;
        return new PathResult(path, hops, true);
    }

    // ---- Dijkstra: shortest path by distance in metres ----
    public PathResult dijkstra(String start, String end) {

        if (!hasNode(start) || !hasNode(end)) {
            return new PathResult(new ArrayList<String>(), 0, false);
        }

        if (start.equals(end)) {
            ArrayList<String> single = new ArrayList<String>();
            single.add(start);
            return new PathResult(single, 0, true);
        }

        final int INFINITY = 1000000000;

        HashMap<String, Integer> dist = new HashMap<String, Integer>();
        HashMap<String, String> parent = new HashMap<String, String>();
        Set<String> visited = new HashSet<String>();

        // Every node starts at INFINITY, except the start.
        for (String id : adjacency.keySet()) {
            dist.put(id, INFINITY);
        }
        dist.put(start, 0);

        while (true) {

            // Pick the unvisited node with the smallest distance.
            String current = null;
            int bestDist = INFINITY;

            for (String id : adjacency.keySet()) {
                if (visited.contains(id)) continue;

                int d = dist.get(id);
                if (d < bestDist) {
                    bestDist = d;
                    current = id;
                }
            }

            if (current == null) break;
            if (current.equals(end)) break;

            visited.add(current);

            // Relax the neighbours of 'current'.
            for (Edge edge : getNeighbours(current)) {
                if (visited.contains(edge.to)) continue;

                int newDist = dist.get(current) + edge.distance;
                if (newDist < dist.get(edge.to)) {
                    dist.put(edge.to, newDist);
                    parent.put(edge.to, current);
                }
            }
        }

        // Still INFINITY means we never reached the end.
        if (dist.get(end) == INFINITY) {
            return new PathResult(new ArrayList<String>(), 0, false);
        }

        ArrayList<String> path = new ArrayList<String>();
        String step = end;
        while (step != null) {
            path.add(0, step);
            step = parent.get(step);
        }

        return new PathResult(path, dist.get(end), true);
    }

    // ---- Turn direction using the cross product ----
    // For A -> B -> C:
    //   cross >  4  -> "left"
    //   cross < -4  -> "right"
    //   otherwise   -> "straight"
    public String getTurn(String aId, String bId, String cId) {

        Location a = locations.get(aId);
        Location b = locations.get(bId);
        Location c = locations.get(cId);

        if (a == null || b == null || c == null) return "straight";

        int v1x = b.x - a.x;
        int v1y = b.y - a.y;
        int v2x = c.x - b.x;
        int v2y = c.y - b.y;

        int cross = v1x * v2y - v1y * v2x;

        if (cross > 4)  return "left";
        if (cross < -4) return "right";
        return "straight";
    }
}