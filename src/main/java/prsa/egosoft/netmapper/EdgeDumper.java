package prsa.egosoft.netmapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkGraph;
import prsa.egosoft.netmapper.model.NetworkGraph.GraphEdge;

import java.io.File;
import java.util.Map;

public class EdgeDumper
{
    public static void main(String[] args) throws Exception
    {
        String filename = (args.length > 0) ? args[0] : "network_map.json";
        File file = new File(filename);
        if(!file.exists())
        {
            System.err.println("File not found: " + filename);
            return;
        }
        
        ObjectMapper mapper = new ObjectMapper();
        Map<String, NetworkDevice> deviceMap = mapper.readValue(file, new TypeReference<Map<String, NetworkDevice>>()
        {
        });
        NetworkGraph graph = NetworkGraph.buildFromDevices(deviceMap, true);
        
        System.out.println("--- Dumping All Edges ---");
        for(GraphEdge edge : graph.getEdges())
        {
            String src = edge.getSourceId().replace("device_", "").replace("endpoint_", "");
            String tgt = edge.getTargetId().replace("device_", "").replace("endpoint_", "");
            
            System.out.println(String.format("Edge: %s -> %s [%s] role=%s visible=%b label=%s", src, tgt,
                    edge.getType(), edge.getRole(), edge.isVisible(), edge.getLabel()));
        }
    }
}
