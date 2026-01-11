package prsa.egosoft.netmapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkGraph;
import prsa.egosoft.netmapper.model.NetworkGraph.GraphEdge;
import prsa.egosoft.netmapper.model.NetworkGraph.GraphNode;
import prsa.egosoft.netmapper.model.NetworkGraph.NodeType;
import prsa.egosoft.netmapper.model.NetworkGraph.EdgeType;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class StandaloneVerifier {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java StandaloneVerifier <path_to_network_map.json>");
            System.exit(1);
        }

        String jsonPath = args[0];
        ObjectMapper mapper = new ObjectMapper();
        Map<String, NetworkDevice> deviceMap = null;

        System.out.println("Loading JSON from: " + jsonPath);
        try {
            deviceMap = mapper.readValue(new File(jsonPath), new TypeReference<Map<String, NetworkDevice>>() {
            });
            System.out.println("Successfully loaded " + deviceMap.size() + " devices.");
        } catch (IOException e) {
            System.err.println("Error reading JSON: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("Building NetworkGraph with redundancy filter...");
        // true for strict/physical filter
        NetworkGraph graph = NetworkGraph.buildFromDevices(deviceMap, true);

        System.out.println("\nFinal Graph Statistics:");
        System.out.println("Total Nodes: " + graph.getNodes().size());
        System.out.println("Total Links: " + graph.getEdges().size());

        System.out.println("\n--- Physical Link Degree Analysis ---");

        Map<String, Integer> deviceDegree = new HashMap<>();

        // Initialize all device nodes with 0
        for (GraphNode node : graph.getNodes()) {
            if (node.getType() == NodeType.DEVICE) {
                deviceDegree.put(node.getId(), 0);
            }
        }

        int physicalLinksStart = 0;
        int physicalLinksEnd = 0;

        for (GraphEdge edge : graph.getEdges()) {
            if (edge.getType() == EdgeType.PHYSICAL) {
                // Ensure we only count Device <-> Device links
                boolean srcIsDevice = edge.getSourceId().startsWith("device_");
                boolean tgtIsDevice = edge.getTargetId().startsWith("device_");

                if (srcIsDevice && tgtIsDevice) {
                    deviceDegree.put(edge.getSourceId(), deviceDegree.getOrDefault(edge.getSourceId(), 0) + 1);
                    deviceDegree.put(edge.getTargetId(), deviceDegree.getOrDefault(edge.getTargetId(), 0) + 1);
                    physicalLinksStart++;
                }
            }
        }

        // Note: each edge increments 2 nodes, so sum of degrees = 2 * edges.

        System.out.println("Total Physical Device<->Device Edges: " + physicalLinksStart);

        int switchesWithManyLinks = 0;
        System.out.println("\nDevices with > 2 Physical Links (Detailed Connections):");

        // Build adjacency list for printing
        Map<String, List<String>> adj = new HashMap<>();
        for (GraphEdge edge : graph.getEdges()) {
            if (edge.getType() == EdgeType.PHYSICAL) {
                boolean srcIsDevice = edge.getSourceId().startsWith("device_");
                boolean tgtIsDevice = edge.getTargetId().startsWith("device_");
                if (srcIsDevice && tgtIsDevice) {
                    adj.computeIfAbsent(edge.getSourceId(), k -> new ArrayList<>()).add(edge.getTargetId());
                    adj.computeIfAbsent(edge.getTargetId(), k -> new ArrayList<>()).add(edge.getSourceId());
                }
            }
        }

        for (Map.Entry<String, Integer> entry : deviceDegree.entrySet()) {
            if (entry.getValue() > 2) {
                String id = entry.getKey();
                System.out.println(" - " + id.replace("device_", "") + " (" + entry.getValue() + " links): "
                        + adj.getOrDefault(id, new ArrayList<>()).stream().map(s -> s.replace("device_", ""))
                                .collect(java.util.stream.Collectors.joining(", ")));
                switchesWithManyLinks++;
            }
        }

        if (switchesWithManyLinks == 0) {
            System.out.println("(None)");
        }

        System.out.println("\nSummary:");
        System.out.println("Count of Switches with > 2 Physical Links: " + switchesWithManyLinks);

        System.out.println("\n-- Detailed Degrees --");
        for (Map.Entry<String, Integer> entry : deviceDegree.entrySet()) {
            System.out.println(entry.getKey().replace("device_", "") + " : " + entry.getValue());
        }

        System.out.println("\nDone.");
    }
}
