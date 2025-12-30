package prsa.egosoft.netmapper.model;

import java.awt.FontMetrics;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a network graph structure with nodes and edges.
 */
public class NetworkGraph
{
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
    
    /**
     * Calculates the bounding box of the entire graph including labels.
     */
    public Rectangle2D calculateBounds(java.awt.Graphics2D g2)
    {
        if(nodes.isEmpty())
        {
            return new Rectangle2D.Double(0, 0, 100, 100);
        }
        
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        
        // Use a default node radius for bounds calculation
        int radius = 15;
        
        for(GraphNode node : nodes)
        {
            // Node circle bounds
            minX = Math.min(minX, node.getX() - radius);
            minY = Math.min(minY, node.getY() - radius);
            maxX = Math.max(maxX, node.getX() + radius);
            maxY = Math.max(maxY, node.getY() + radius);
            
            // Node label bounds
            if(node.getLabel() != null && !node.getLabel().isEmpty())
            {
                g2.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 10));
                FontMetrics fm = g2.getFontMetrics();
                String[] lines = node.getLabel().split("\n");
                int labelWidth = 0;
                for(String line : lines)
                {
                    labelWidth = Math.max(labelWidth, fm.stringWidth(line));
                }
                int labelHeight = lines.length * fm.getHeight();
                
                minX = Math.min(minX, node.getX() - labelWidth / 2.0);
                maxX = Math.max(maxX, node.getX() + labelWidth / 2.0);
                maxY = Math.max(maxY, node.getY() + radius + 12 + labelHeight);
            }
        }
        
        // Also consider edge labels
        for(GraphEdge edge : edges)
        {
            if(edge.getLabel() != null && !edge.getLabel().isEmpty())
            {
                GraphNode source = findNode(edge.getSourceId());
                GraphNode target = findNode(edge.getTargetId());
                if(source != null && target != null)
                {
                    double midX = (source.getX() + target.getX()) / 2.0;
                    double midY = (source.getY() + target.getY()) / 2.0;
                    
                    g2.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 9));
                    FontMetrics fm = g2.getFontMetrics();
                    String[] lines = edge.getLabel().split("\n");
                    int labelWidth = 0;
                    for(String line : lines)
                    {
                        labelWidth = Math.max(labelWidth, fm.stringWidth(line));
                    }
                    int labelHeight = lines.length * fm.getHeight();
                    
                    minX = Math.min(minX, midX - labelWidth / 2.0);
                    maxX = Math.max(maxX, midX + labelWidth / 2.0);
                    minY = Math.min(minY, midY - labelHeight / 2.0);
                    maxY = Math.max(maxY, midY + labelHeight / 2.0);
                }
            }
        }
        
        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }
    
    private GraphNode findNode(String id)
    {
        for(GraphNode node : nodes)
        {
            if(node.getId().equals(id))
            {
                return node;
            }
        }
        return null;
    }
    
    public NetworkGraph()
    {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
    }
    
    public List<GraphNode> getNodes()
    {
        return nodes;
    }
    
    public List<GraphEdge> getEdges()
    {
        return edges;
    }
    
    public void addNode(GraphNode node)
    {
        nodes.add(node);
    }
    
    public void addEdge(GraphEdge edge)
    {
        edges.add(edge);
    }
    
    /**
     * Builds a network graph from scanned devices.
     */
    public static NetworkGraph buildFromDevices(Map<String, NetworkDevice> deviceMap)
    {
        return buildFromDevices(deviceMap, true);
    }
    
    /**
     * Builds a network graph from scanned devices.
     */
    public static NetworkGraph buildFromDevices(Map<String, NetworkDevice> deviceMap, boolean simplifiedPhysicalView)
    {
        NetworkGraph graph = new NetworkGraph();
        Map<String, GraphNode> nodeMap = new HashMap<>();
        
        // First pass: Create nodes for each device and store device IPs and MACs
        Map<String, String> ipToDeviceId = new HashMap<>();
        Map<String, String> macToDeviceId = new HashMap<>();
        
        for(Map.Entry<String, NetworkDevice> entry : deviceMap.entrySet())
        {
            NetworkDevice device = entry.getValue();
            String deviceId = "device_" + device.getIpAddress();
            
            // Track MACs of this device to avoid duplicates later
            for(NetworkInterface ni : device.getInterfaces())
            {
                if(ni.getMacAddress() != null && !ni.getMacAddress().isEmpty())
                {
                    macToDeviceId.put(ni.getMacAddress().toLowerCase(), deviceId);
                }
            }
            
            // Build detailed label for device
            StringBuilder label = new StringBuilder();
            if(device.getSysName() != null && !device.getSysName().isEmpty())
            {
                label.append(device.getSysName()).append("\n");
            }
            if(device.getVendor() != null && !device.getVendor().isEmpty())
            {
                label.append(device.getVendor());
                if(device.getModel() != null && !device.getModel().isEmpty())
                {
                    label.append(" ").append(device.getModel());
                }
                label.append("\n");
            }
            label.append(device.getIpAddress());
            
            GraphNode deviceNode = new GraphNode(deviceId, label.toString(), device.getDeviceType(), NodeType.DEVICE);
            graph.addNode(deviceNode);
            nodeMap.put(deviceId, deviceNode);
            ipToDeviceId.put(device.getIpAddress(), deviceId);
        }
        
        // Second pass: Create edges and endpoint nodes (only if endpoint is not a
        // device)
        for(Map.Entry<String, NetworkDevice> entry : deviceMap.entrySet())
        {
            NetworkDevice device = entry.getValue();
            String sourceDeviceId = "device_" + device.getIpAddress();
            
            for(Map.Entry<Integer, List<DetectedEndpoint>> macEntry : device.getMacAddressTable().entrySet())
            {
                Integer interfaceIndex = macEntry.getKey();
                // Find the interface by index
                NetworkInterface netInterface = null;
                for(NetworkInterface ni : device.getInterfaces())
                {
                    if(ni.getIndex() == interfaceIndex)
                    {
                        netInterface = ni;
                        break;
                    }
                }
                
                for(DetectedEndpoint endpoint : macEntry.getValue())
                {
                    // Build edge label
                    String edgeLabel = "";
                    if(netInterface != null)
                    {
                        edgeLabel = netInterface.getDescription();
                        if(netInterface.getMacAddress() != null && !netInterface.getMacAddress().isEmpty())
                        {
                            edgeLabel += "\nMAC: " + netInterface.getMacAddress();
                            // Add vendor information
                            String vendor = prsa.egosoft.netmapper.util.MacVendorUtils
                                    .getVendor(netInterface.getMacAddress());
                            if(vendor != null && !vendor.isEmpty() && !"Unknown".equals(vendor))
                            {
                                edgeLabel += "\nVendor: " + vendor;
                            }
                        }
                    }
                    
                    // Check if endpoint IP corresponds to a scanned device
                    String endpointIp = endpoint.getIpAddress();
                    String endpointMac = endpoint.getMacAddress() != null ? endpoint.getMacAddress().toLowerCase() : "";
                    
                    String targetDeviceId = null;
                    if(endpointIp != null && !endpointIp.isEmpty() && ipToDeviceId.containsKey(endpointIp))
                    {
                        targetDeviceId = ipToDeviceId.get(endpointIp);
                    }
                    else if(!endpointMac.isEmpty() && macToDeviceId.containsKey(endpointMac))
                    {
                        targetDeviceId = macToDeviceId.get(endpointMac);
                    }
                    
                    if(targetDeviceId != null)
                    {
                        // Endpoint is a device - create device-to-device edge
                        if(!sourceDeviceId.equals(targetDeviceId))
                        { // Avoid self-loops
                            graph.addEdge(new GraphEdge(sourceDeviceId, targetDeviceId, edgeLabel));
                        }
                    }
                    else
                    {
                        // Endpoint is not a device - create endpoint node
                        String endpointLabel = endpointIp != null && !endpointIp.isEmpty() ? endpointIp
                                : endpoint.getMacAddress();
                        
                        // Add vendor if available
                        if(endpoint.getVendor() != null && !endpoint.getVendor().isEmpty()
                                && !"Unknown".equals(endpoint.getVendor()))
                        {
                            endpointLabel += "\n" + endpoint.getVendor();
                        }
                        
                        String endpointId = "endpoint_" + endpoint.getMacAddress();
                        
                        // Only add if not already exists
                        if(!nodeMap.containsKey(endpointId))
                        {
                            GraphNode endpointNode = new GraphNode(endpointId, endpointLabel, "Desconocido",
                                    NodeType.ENDPOINT);
                            graph.addNode(endpointNode);
                            nodeMap.put(endpointId, endpointNode);
                        }
                        
                        // Create edge from device to endpoint
                        graph.addEdge(new GraphEdge(sourceDeviceId, endpointId, edgeLabel));
                    }
                }
            }
        }
        
        // Third pass: Merge bidirectional device-to-device edges
        mergeBidirectionalEdges(graph);
        
        // Fourth pass: Remove redundant (transitive) links between devices
        if(simplifiedPhysicalView)
        {
            applyPhysicalRedundancyFilter(graph, deviceMap);
        }
        
        return graph;
    }
    
    /**
     * Removes redundant links between devices where a multi-hop path is known. Uses
     * MAC table visibility to infer the most direct physical path.
     */
    private static void applyPhysicalRedundancyFilter(NetworkGraph graph, Map<String, NetworkDevice> deviceMap)
    {
        // 1. Map each device IP to its set of interface MACs for robust lookup
        Map<String, List<String>> deviceToMacs = new HashMap<>();
        Map<String, String> macToIp = new HashMap<>(); // Reverse map for fast device lookup by MAC
        
        for(NetworkDevice dev : deviceMap.values())
        {
            List<String> macs = new ArrayList<>();
            for(NetworkInterface ni : dev.getInterfaces())
            {
                if(ni.getMacAddress() != null && !ni.getMacAddress().isEmpty())
                {
                    String mac = ni.getMacAddress().toLowerCase();
                    macs.add(mac);
                    macToIp.put(mac, dev.getIpAddress());
                }
            }
            deviceToMacs.put(dev.getIpAddress(), macs);
        }
        
        List<GraphEdge> edgesToRemove = new ArrayList<>();
        
        // Helper to check if a device is seen on a port
        java.util.function.BiFunction<NetworkDevice, String, Integer> getPortViewingDevice = (viewer, targetIp) ->
        {
            List<String> targetMacs = deviceToMacs.get(targetIp);
            for(Map.Entry<Integer, List<DetectedEndpoint>> entry : viewer.getMacAddressTable().entrySet())
            {
                for(DetectedEndpoint ep : entry.getValue())
                {
                    // Match by IP
                    if(targetIp.equals(ep.getIpAddress()))
                    {
                        return entry.getKey();
                    }
                    // Or match by MAC
                    if(ep.getMacAddress() != null && targetMacs != null
                            && targetMacs.contains(ep.getMacAddress().toLowerCase()))
                    {
                        return entry.getKey();
                    }
                }
            }
            return null;
        };
        
        // For each device-to-device edge A-B
        for(GraphEdge edge : new ArrayList<>(graph.getEdges()))
        {
            String sourceId = edge.getSourceId();
            String targetId = edge.getTargetId();
            
            if(!sourceId.startsWith("device_") || !targetId.startsWith("device_"))
            {
                continue;
            }
            
            String sourceIp = sourceId.replace("device_", "");
            String targetIp = targetId.replace("device_", "");
            
            NetworkDevice sourceDev = deviceMap.get(sourceIp);
            if(sourceDev == null)
            {
                continue;
            }
            
            // Find which port of sourceDev sees targetIp
            Integer sourcePort = getPortViewingDevice.apply(sourceDev, targetIp);
            if(sourcePort == null)
            {
                continue;
            }
            
            // Look for intermediate nodes: devices seen on the SAME port as targetIp
            // but that are "between" sourceDev and targetIp.
            List<String> othersOnSamePort = new ArrayList<>();
            for(DetectedEndpoint ep : sourceDev.getMacAddressTable().get(sourcePort))
            {
                String otherIp = ep.getIpAddress();
                if(otherIp == null && ep.getMacAddress() != null)
                {
                    otherIp = macToIp.get(ep.getMacAddress().toLowerCase());
                }
                
                if(otherIp != null && !otherIp.equals(targetIp) && deviceMap.containsKey(otherIp))
                {
                    othersOnSamePort.add(otherIp);
                }
            }
            
            for(String intermediateIp : othersOnSamePort)
            {
                NetworkDevice interDev = deviceMap.get(intermediateIp);
                
                // If the intermediate device sees sourceDev and targetDev on DIFFERENT
                // interfaces,
                // then it is physically between them.
                Integer interPortToSource = getPortViewingDevice.apply(interDev, sourceIp);
                Integer interPortToTarget = getPortViewingDevice.apply(interDev, targetIp);
                
                if(interPortToSource != null && interPortToTarget != null
                        && !interPortToSource.equals(interPortToTarget))
                {
                    // Found a path sourceDev <-> interDev <-> targetDev
                    // So sourceDev <-> targetDev is redundant.
                    edgesToRemove.add(edge);
                    break;
                }
            }
        }
        
        graph.getEdges().removeAll(edgesToRemove);
    }
    
    /**
     * Merges bidirectional edges into single edges with combined labels.
     */
    private static void mergeBidirectionalEdges(NetworkGraph graph)
    {
        List<GraphEdge> edgesToRemove = new ArrayList<>();
        List<GraphEdge> edgesToAdd = new ArrayList<>();
        Map<String, GraphEdge> processedPairs = new HashMap<>();
        
        for(GraphEdge edge : graph.getEdges())
        {
            String pairKey = edge.getSourceId() + "-" + edge.getTargetId();
            String reversePairKey = edge.getTargetId() + "-" + edge.getSourceId();
            
            // Check if we already processed this pair
            if(processedPairs.containsKey(pairKey) || processedPairs.containsKey(reversePairKey))
            {
                continue;
            }
            
            // Look for reverse edge
            GraphEdge reverseEdge = null;
            for(GraphEdge e : graph.getEdges())
            {
                if(e.getSourceId().equals(edge.getTargetId()) && e.getTargetId().equals(edge.getSourceId()))
                {
                    reverseEdge = e;
                    break;
                }
            }
            
            if(reverseEdge != null)
            {
                // Found bidirectional edge - merge them
                String sourceLabel = edge.getLabel();
                String targetLabel = reverseEdge.getLabel();
                
                // Get device IPs for labeling
                String sourceIp = edge.getSourceId().replace("device_", "");
                String targetIp = edge.getTargetId().replace("device_", "");
                
                // Create combined label
                StringBuilder combinedLabel = new StringBuilder();
                if(sourceLabel != null && !sourceLabel.isEmpty())
                {
                    combinedLabel.append(sourceIp).append(":\n").append(sourceLabel);
                }
                if(targetLabel != null && !targetLabel.isEmpty())
                {
                    if(combinedLabel.length() > 0)
                    {
                        combinedLabel.append("\n---\n");
                    }
                    combinedLabel.append(targetIp).append(":\n").append(targetLabel);
                }
                
                // Create merged edge (keeping original direction for consistency)
                GraphEdge mergedEdge = new GraphEdge(edge.getSourceId(), edge.getTargetId(), combinedLabel.toString());
                
                edgesToRemove.add(edge);
                edgesToRemove.add(reverseEdge);
                edgesToAdd.add(mergedEdge);
                processedPairs.put(pairKey, mergedEdge);
            }
        }
        
        // Apply changes
        graph.getEdges().removeAll(edgesToRemove);
        graph.getEdges().addAll(edgesToAdd);
    }
    
    public static class GraphNode
    {
        private String id;
        private String label;
        private String typeLabel;
        private NodeType type;
        private double x; // Position for rendering
        private double y;
        
        public GraphNode(String id, String label, String typeLabel, NodeType type)
        {
            this.id = id;
            this.label = label;
            this.typeLabel = typeLabel;
            this.type = type;
        }
        
        public String getId()
        {
            return id;
        }
        
        public String getLabel()
        {
            return label;
        }
        
        public String getTypeLabel()
        {
            return typeLabel;
        }
        
        public NodeType getType()
        {
            return type;
        }
        
        public double getX()
        {
            return x;
        }
        
        public double getY()
        {
            return y;
        }
        
        public void setX(double x)
        {
            this.x = x;
        }
        
        public void setY(double y)
        {
            this.y = y;
        }
    }
    
    public static class GraphEdge
    {
        private String sourceId;
        private String targetId;
        private String label;
        
        public GraphEdge(String sourceId, String targetId)
        {
            this(sourceId, targetId, "");
        }
        
        public GraphEdge(String sourceId, String targetId, String label)
        {
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.label = label;
        }
        
        public String getSourceId()
        {
            return sourceId;
        }
        
        public String getTargetId()
        {
            return targetId;
        }
        
        public String getLabel()
        {
            return label;
        }
    }
    
    public enum NodeType
    {
        DEVICE, ENDPOINT
    }
}
