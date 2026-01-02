package prsa.egosoft.netmapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkGraph;
import prsa.egosoft.netmapper.model.NetworkGraph.GraphEdge;

import java.io.File;
import java.io.IOException;
import java.util.Map;

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
        NetworkGraph graph = NetworkGraph.buildFromDevices(deviceMap, true);

        System.out.println("\nFinal Graph Statistics:");
        System.out.println("Total Nodes: " + graph.getNodes().size());
        System.out.println("Total Links: " + graph.getEdges().size());

        System.out.println("\n--- Link Verification ---");

        // Define relevant IPs
        String x4 = "device_10.81.128.4";
        String x5 = "device_10.81.128.5";
        String x55 = "device_10.81.128.55";
        String x40 = "device_10.81.128.40";
        String x41 = "device_10.81.128.41";

        // Check specific links
        checkLinkPresence(graph, x40, x41, false);
        checkLinkPresence(graph, x4, x40, true);
        checkLinkPresence(graph, x4, x41, true);
        checkLinkPresence(graph, x4, x5, true);

        // Strict Filter Verification:
        // x.5 <-> x.55 should be PRESENT (Physical Path)
        // x.4 <-> x.55 should be ABSENT (Redundant/Logical)
        checkLinkPresence(graph, x5, x55, true);
        checkLinkPresence(graph, x4, x55, false);

        System.out.println("\nVerification complete.");
    }

    private static void checkLinkPresence(NetworkGraph graph, String id1, String id2, boolean shouldExist) {
        boolean exists = false;
        for (GraphEdge edge : graph.getEdges()) {
            if ((edge.getSourceId().equals(id1) && edge.getTargetId().equals(id2)) ||
                    (edge.getSourceId().equals(id2) && edge.getTargetId().equals(id1))) {
                exists = true;
                break;
            }
        }

        String status = exists ? "PRESENT" : "ABSENT";
        String expected = shouldExist ? "PRESENT" : "ABSENT";
        String result = (exists == shouldExist) ? "[OK]" : "[FAIL]";

        System.out.println(result + " Link " + id1.replace("device_", "") + " <-> " +
                id2.replace("device_", "") + " is " + status + " (Expected: " + expected + ")");
    }
}
