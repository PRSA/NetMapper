package com.netmapper.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a network graph structure with nodes and edges.
 */
public class NetworkGraph {
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;

    public NetworkGraph() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
    }

    public List<GraphNode> getNodes() {
        return nodes;
    }

    public List<GraphEdge> getEdges() {
        return edges;
    }

    public void addNode(GraphNode node) {
        nodes.add(node);
    }

    public void addEdge(GraphEdge edge) {
        edges.add(edge);
    }

    /**
     * Builds a network graph from scanned devices.
     */
    public static NetworkGraph buildFromDevices(Map<String, NetworkDevice> deviceMap) {
        NetworkGraph graph = new NetworkGraph();
        Map<String, GraphNode> nodeMap = new HashMap<>();

        // First pass: Create nodes for each device and store device IPs
        Map<String, String> ipToDeviceId = new HashMap<>();
        for (Map.Entry<String, NetworkDevice> entry : deviceMap.entrySet()) {
            NetworkDevice device = entry.getValue();
            String deviceId = "device_" + device.getIpAddress();

            // Build detailed label for device
            StringBuilder label = new StringBuilder();
            if (device.getSysName() != null && !device.getSysName().isEmpty()) {
                label.append(device.getSysName()).append("\n");
            }
            if (device.getVendor() != null && !device.getVendor().isEmpty()) {
                label.append(device.getVendor());
                if (device.getModel() != null && !device.getModel().isEmpty()) {
                    label.append(" ").append(device.getModel());
                }
                label.append("\n");
            }
            label.append(device.getIpAddress());

            GraphNode deviceNode = new GraphNode(deviceId, label.toString(), NodeType.DEVICE);
            graph.addNode(deviceNode);
            nodeMap.put(deviceId, deviceNode);
            ipToDeviceId.put(device.getIpAddress(), deviceId);
        }

        // Second pass: Create edges and endpoint nodes (only if endpoint is not a
        // device)
        for (Map.Entry<String, NetworkDevice> entry : deviceMap.entrySet()) {
            NetworkDevice device = entry.getValue();
            String sourceDeviceId = "device_" + device.getIpAddress();

            for (Map.Entry<Integer, List<DetectedEndpoint>> macEntry : device.getMacAddressTable().entrySet()) {
                Integer interfaceIndex = macEntry.getKey();
                // Find the interface by index
                NetworkInterface netInterface = null;
                for (NetworkInterface ni : device.getInterfaces()) {
                    if (ni.getIndex() == interfaceIndex) {
                        netInterface = ni;
                        break;
                    }
                }

                for (DetectedEndpoint endpoint : macEntry.getValue()) {
                    // Build edge label
                    String edgeLabel = "";
                    if (netInterface != null) {
                        edgeLabel = netInterface.getDescription();
                        if (netInterface.getMacAddress() != null && !netInterface.getMacAddress().isEmpty()) {
                            edgeLabel += "\nMAC: " + netInterface.getMacAddress();
                            // Add vendor information
                            String vendor = com.netmapper.util.MacVendorUtils.getVendor(netInterface.getMacAddress());
                            if (vendor != null && !vendor.isEmpty() && !"Unknown".equals(vendor)) {
                                edgeLabel += "\nVendor: " + vendor;
                            }
                        }
                    }

                    // Check if endpoint IP corresponds to a scanned device
                    String endpointIp = endpoint.getIpAddress();
                    if (endpointIp != null && !endpointIp.isEmpty() && ipToDeviceId.containsKey(endpointIp)) {
                        // Endpoint is a device - create device-to-device edge
                        String targetDeviceId = ipToDeviceId.get(endpointIp);
                        if (!sourceDeviceId.equals(targetDeviceId)) { // Avoid self-loops
                            graph.addEdge(new GraphEdge(sourceDeviceId, targetDeviceId, edgeLabel));
                        }
                    } else {
                        // Endpoint is not a device - create endpoint node
                        String endpointLabel = endpointIp != null && !endpointIp.isEmpty()
                                ? endpointIp
                                : endpoint.getMacAddress();

                        // Add vendor if available
                        if (endpoint.getVendor() != null && !endpoint.getVendor().isEmpty()
                                && !"Unknown".equals(endpoint.getVendor())) {
                            endpointLabel += "\n" + endpoint.getVendor();
                        }

                        String endpointId = "endpoint_" + endpoint.getMacAddress();

                        // Only add if not already exists
                        if (!nodeMap.containsKey(endpointId)) {
                            GraphNode endpointNode = new GraphNode(endpointId, endpointLabel, NodeType.ENDPOINT);
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

        return graph;
    }

    /**
     * Merges bidirectional edges into single edges with combined labels.
     */
    private static void mergeBidirectionalEdges(NetworkGraph graph) {
        List<GraphEdge> edgesToRemove = new ArrayList<>();
        List<GraphEdge> edgesToAdd = new ArrayList<>();
        Map<String, GraphEdge> processedPairs = new HashMap<>();

        for (GraphEdge edge : graph.getEdges()) {
            String pairKey = edge.getSourceId() + "-" + edge.getTargetId();
            String reversePairKey = edge.getTargetId() + "-" + edge.getSourceId();

            // Check if we already processed this pair
            if (processedPairs.containsKey(pairKey) || processedPairs.containsKey(reversePairKey)) {
                continue;
            }

            // Look for reverse edge
            GraphEdge reverseEdge = null;
            for (GraphEdge e : graph.getEdges()) {
                if (e.getSourceId().equals(edge.getTargetId()) && e.getTargetId().equals(edge.getSourceId())) {
                    reverseEdge = e;
                    break;
                }
            }

            if (reverseEdge != null) {
                // Found bidirectional edge - merge them
                String sourceLabel = edge.getLabel();
                String targetLabel = reverseEdge.getLabel();

                // Get device IPs for labeling
                String sourceIp = edge.getSourceId().replace("device_", "");
                String targetIp = edge.getTargetId().replace("device_", "");

                // Create combined label
                StringBuilder combinedLabel = new StringBuilder();
                if (sourceLabel != null && !sourceLabel.isEmpty()) {
                    combinedLabel.append(sourceIp).append(":\n").append(sourceLabel);
                }
                if (targetLabel != null && !targetLabel.isEmpty()) {
                    if (combinedLabel.length() > 0) {
                        combinedLabel.append("\n---\n");
                    }
                    combinedLabel.append(targetIp).append(":\n").append(targetLabel);
                }

                // Create merged edge (keeping original direction for consistency)
                GraphEdge mergedEdge = new GraphEdge(edge.getSourceId(), edge.getTargetId(),
                        combinedLabel.toString());

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

    public static class GraphNode {
        private String id;
        private String label;
        private NodeType type;
        private double x; // Position for rendering
        private double y;

        public GraphNode(String id, String label, NodeType type) {
            this.id = id;
            this.label = label;
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public NodeType getType() {
            return type;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public void setX(double x) {
            this.x = x;
        }

        public void setY(double y) {
            this.y = y;
        }
    }

    public static class GraphEdge {
        private String sourceId;
        private String targetId;
        private String label;

        public GraphEdge(String sourceId, String targetId) {
            this(sourceId, targetId, "");
        }

        public GraphEdge(String sourceId, String targetId, String label) {
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.label = label;
        }

        public String getSourceId() {
            return sourceId;
        }

        public String getTargetId() {
            return targetId;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum NodeType {
        DEVICE,
        ENDPOINT
    }
}
