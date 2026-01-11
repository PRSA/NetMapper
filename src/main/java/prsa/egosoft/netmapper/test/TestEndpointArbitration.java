package prsa.egosoft.netmapper.test;

import prsa.egosoft.netmapper.model.NetworkGraph;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkGraph.GraphEdge;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

public class TestEndpointArbitration {
    public static void main(String[] args) {
        try {
            String filePath = "/opt/workspace/NetMapper/network_map_Gondomar.json";
            ObjectMapper mapper = new ObjectMapper();

            System.out.println("TEST START");
            System.out.println("Loading device map from: " + filePath);
            Map<String, NetworkDevice> deviceMap = mapper.readValue(new File(filePath),
                    new TypeReference<Map<String, NetworkDevice>>() {
                    });

            System.out.println("Devices loaded: " + deviceMap.size());
            System.out.println("Building graph with simplifiedPhysicalView=true...");
            NetworkGraph graph = NetworkGraph.buildFromDevices(deviceMap, true);

            System.out.println("Graph built. Edges: " + graph.getEdges().size());

            System.out.println("\nAnalyzing endpoints with multiple links:");
            Map<String, List<GraphEdge>> endpointEdges = new HashMap<>();

            for (GraphEdge edge : graph.getEdges()) {
                String targetId = edge.getTargetId();
                if (targetId.startsWith("endpoint_")) {
                    endpointEdges.computeIfAbsent(targetId, k -> new ArrayList<>()).add(edge);
                }
            }

            int multiHomedCount = 0;
            for (Map.Entry<String, List<GraphEdge>> entry : endpointEdges.entrySet()) {
                if (entry.getValue().size() > 1) {
                    long physicalCount = entry.getValue().stream()
                            .filter(e -> e.getType() == NetworkGraph.EdgeType.PHYSICAL).count();
                    if (physicalCount > 1) {
                        multiHomedCount++;
                        System.out.println("Endpoint: " + entry.getKey());
                        for (GraphEdge edge : entry.getValue()) {
                            System.out.println("  Linked to: " + edge.getSourceId() + " ["
                                    + edge.getLabel().replace("\n", " ") + "] Type: " + edge.getType());
                        }
                    }
                }
            }

            System.out.println("\nSummary:");
            System.out.println("Total endpoints with links: " + endpointEdges.size());
            System.out.println("Endpoints with MULTIPLE physical links: " + multiHomedCount);
            System.out.println("TEST END");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
