package co.edu.unicesar.rutavida.traffic;

import co.edu.unicesar.rutavida.graph.Edge;
import co.edu.unicesar.rutavida.graph.Node;
import co.edu.unicesar.rutavida.graph.RoadGraph;

import java.util.ArrayList;
import java.util.List;

/** Hand-made 6-node test graph (fictitious streets, test data only). */
final class SampleGraph {

    private SampleGraph() {
    }

    static RoadGraph sixNodes() {
        List<Node> nodes = List.of(
                new Node(0, 1, 10.4600, -73.2600),
                new Node(1, 2, 10.4600, -73.2550),
                new Node(2, 3, 10.4600, -73.2500),
                new Node(3, 4, 10.4650, -73.2600),
                new Node(4, 5, 10.4650, -73.2550),
                new Node(5, 6, 10.4650, -73.2500));

        List<Edge> edges = new ArrayList<>();
        twoWay(edges, 0, 1, 550, 30, "Calle de prueba");
        twoWay(edges, 1, 2, 550, 30, "Calle de prueba");
        twoWay(edges, 3, 4, 550, 60, "Avenida de prueba");
        twoWay(edges, 4, 5, 550, 60, "Avenida de prueba");
        twoWay(edges, 0, 3, 560, 40, "Cruce A");
        twoWay(edges, 1, 4, 560, 40, "Cruce B");
        twoWay(edges, 2, 5, 560, 40, "Cruce C");
        return new RoadGraph(nodes, edges);
    }

    private static void twoWay(List<Edge> edges, int a, int b, double meters, double kmh, String name) {
        edges.add(new Edge(edges.size(), 100 + edges.size(), a, b, meters, kmh, name));
        edges.add(new Edge(edges.size(), 100 + edges.size(), b, a, meters, kmh, name));
    }
}