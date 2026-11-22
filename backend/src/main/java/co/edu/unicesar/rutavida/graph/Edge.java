package co.edu.unicesar.rutavida.graph;

public record Edge(int index, long dbId, int from, int to, double lengthM, double freeFlowKmh, String name) {

    public Edge {
        if (index < 0 || dbId < 0 || from < 0 || to < 0) {
            throw new IllegalArgumentException("index, dbId, from and to must be >= 0");
        }
        if (from == to) {
            throw new IllegalArgumentException("an edge cannot start and end at the same node");
        }
        if (!(lengthM > 0) || !(freeFlowKmh > 0)) {
            throw new IllegalArgumentException("length and speed must be > 0");
        }
    }
}
