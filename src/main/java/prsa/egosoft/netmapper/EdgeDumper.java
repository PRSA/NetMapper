package prsa.egosoft.netmapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkGraph;
import prsa.egosoft.netmapper.model.NetworkGraph.GraphEdge;

import java.io.File;
import java.util.Map;
import java.util.List;
import java.util.Arrays;

public class EdgeDumper {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, NetworkDevice> deviceMap = mapper.readValue(new File("network_map_CoCJ.json"),
                new TypeReference<Map<String, NetworkDevice>>() {
                });
        NetworkGraph graph = NetworkGraph.buildFromDevices(deviceMap, true);

        List<String> targetIps = Arrays.asList("10.81.128.1", "10.81.128.4", "10.81.128.5", "10.81.128.55",
                "10.81.128.135");

        System.out.println("--- All Edges involving Target IPs ---");
        for (GraphEdge edge : graph.getEdges()) {
            String src = edge.getSourceId().replace("device_", "").replace("endpoint_", "");
            String tgt = edge.getTargetId().replace("device_", "").replace("endpoint_", "");

            boolean match = false;
            for (String tip : targetIps) {
                if (src.contains(tip) || tgt.contains(tip)) {
                    match = true;
                    break;
                }
            }

            if (match) {
                System.out.println(
                        String.format("Edge: %s -> %s [%s] label=%s", src, tgt, edge.getType(), edge.getLabel()));
            }
        }
    }
}
