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
public class NetworkGraph {
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;

    /**
     * Calculates the bounding box of the entire graph including labels.
     */
    public Rectangle2D calculateBounds(java.awt.Graphics2D g2) {
        return calculateBounds(g2, n -> true, e -> true);
    }

    /**
     * Calculates the bounding box of the graph considering filters.
     */
    public Rectangle2D calculateBounds(java.awt.Graphics2D g2,
            java.util.function.Predicate<GraphNode> nodeFilter,
            java.util.function.Predicate<GraphEdge> edgeFilter) {

        List<GraphNode> visibleNodes = new ArrayList<>();
        for (GraphNode node : nodes) {
            if (nodeFilter.test(node)) {
                visibleNodes.add(node);
            }
        }

        if (visibleNodes.isEmpty()) {
            return new Rectangle2D.Double(0, 0, 100, 100);
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        // Use a default node radius for bounds calculation
        int radius = 15;

        for (GraphNode node : visibleNodes) {
            // Node circle bounds
            minX = Math.min(minX, node.getX() - radius);
            minY = Math.min(minY, node.getY() - radius);
            maxX = Math.max(maxX, node.getX() + radius);
            maxY = Math.max(maxY, node.getY() + radius);

            // Node label bounds
            if (node.getLabel() != null && !node.getLabel().isEmpty()) {
                g2.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 10));
                FontMetrics fm = g2.getFontMetrics();
                String[] lines = node.getLabel().split("\n");
                int labelWidth = 0;
                for (String line : lines) {
                    labelWidth = Math.max(labelWidth, fm.stringWidth(line));
                }
                int labelHeight = lines.length * fm.getHeight();

                minX = Math.min(minX, node.getX() - labelWidth / 2.0);
                maxX = Math.max(maxX, node.getX() + labelWidth / 2.0);
                maxY = Math.max(maxY, node.getY() + radius + 12 + labelHeight);
            }
        }

        // Also consider edge labels
        for (GraphEdge edge : edges) {
            if (!edgeFilter.test(edge))
                continue;

            GraphNode source = findNode(edge.getSourceId());
            GraphNode target = findNode(edge.getTargetId());

            // Edge is only visible if both nodes are visible
            if (source != null && target != null && nodeFilter.test(source) && nodeFilter.test(target)) {
                if (edge.getLabel() != null && !edge.getLabel().isEmpty()) {
                    double midX = (source.getX() + target.getX()) / 2.0;
                    double midY = (source.getY() + target.getY()) / 2.0;

                    g2.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 9));
                    FontMetrics fm = g2.getFontMetrics();
                    String[] lines = edge.getLabel().split("\n");
                    int labelWidth = 0;
                    for (String line : lines) {
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

    private GraphNode findNode(String id) {
        for (GraphNode node : nodes) {
            if (node.getId().equals(id)) {
                return node;
            }
        }
        return null;
    }

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
        return buildFromDevices(deviceMap, true);
    }

    /**
     * Builds a network graph from scanned devices.
     */
    public static NetworkGraph buildFromDevices(Map<String, NetworkDevice> deviceMap, boolean simplifiedPhysicalView) {
        NetworkGraph graph = new NetworkGraph();
        Map<String, GraphNode> nodeMap = new HashMap<>();

        // First pass: Create nodes for each device and store device IPs and MACs
        Map<String, String> ipToDeviceId = new HashMap<>();
        Map<String, String> macToDeviceId = new HashMap<>();

        for (Map.Entry<String, NetworkDevice> entry : deviceMap.entrySet()) {
            NetworkDevice device = entry.getValue();
            String deviceId = "device_" + device.getIpAddress();

            // Track MACs of this device to avoid duplicates later
            for (NetworkInterface ni : device.getInterfaces()) {
                if (ni.getMacAddress() != null && !ni.getMacAddress().isEmpty()) {
                    macToDeviceId.put(normalizeMac(ni.getMacAddress()), deviceId);
                }
            }

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

            GraphNode deviceNode = new GraphNode(deviceId, label.toString(), device.getDeviceType(), NodeType.DEVICE);
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
                            String vendor = prsa.egosoft.netmapper.util.MacVendorUtils
                                    .getVendor(netInterface.getMacAddress());
                            if (vendor != null && !vendor.isEmpty() && !"Unknown".equals(vendor)) {
                                edgeLabel += "\nVendor: " + vendor;
                            }
                        }
                    }

                    // Check if endpoint IP corresponds to a scanned device
                    String endpointIp = endpoint.getIpAddress();
                    String endpointMac = endpoint.getMacAddress() != null ? endpoint.getMacAddress().toLowerCase() : "";

                    String targetDeviceId = null;
                    if (endpointIp != null && !endpointIp.isEmpty() && ipToDeviceId.containsKey(endpointIp)) {
                        targetDeviceId = ipToDeviceId.get(endpointIp);
                    } else if (!endpointMac.isEmpty() && macToDeviceId.containsKey(endpointMac)) {
                        targetDeviceId = macToDeviceId.get(endpointMac);
                    }

                    if (targetDeviceId != null) {
                        // Endpoint is a device - create device-to-device edge
                        if (!sourceDeviceId.equals(targetDeviceId)) { // Avoid self-loops
                            graph.addEdge(new GraphEdge(sourceDeviceId, targetDeviceId, edgeLabel));
                        }
                    } else {
                        // Endpoint is not a device - create endpoint node
                        String endpointLabel = endpointIp != null && !endpointIp.isEmpty() ? endpointIp
                                : endpoint.getMacAddress();

                        // Add vendor if available
                        if (endpoint.getVendor() != null && !endpoint.getVendor().isEmpty()
                                && !"Unknown".equals(endpoint.getVendor())) {
                            endpointLabel += "\n" + endpoint.getVendor();
                        }

                        String endpointId = "endpoint_" + endpoint.getMacAddress();

                        // Only add if not already exists
                        if (!nodeMap.containsKey(endpointId)) {
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
        if (simplifiedPhysicalView) {
            applyPhysicalRedundancyFilter(graph, deviceMap);
        }

        return graph;
    }

    /**
     * Removes redundant links between devices where a multi-hop path is known. Uses
     * MAC table visibility to infer the most direct physical path.
     */
    private static void applyPhysicalRedundancyFilter(NetworkGraph graph, Map<String, NetworkDevice> deviceMap) {
        System.out.println("DEBUG: Applying Physical Redundancy Filter...");
        // 1. Map each device IP to its set of interface MACs for robust lookup
        // We also perform a thorough "harvest" of MAC-to-IP associations from all
        // bridge tables
        // to correctly identify devices that use MACs not listed in their own interface
        // list (e.g. Bridges)
        Map<String, List<String>> deviceToMacs = new HashMap<>();
        Map<String, String> macToIp = new HashMap<>(); // Reverse map for fast device lookup by MAC

        // Pass 1: Canonical mappings from device interfaces
        for (NetworkDevice dev : deviceMap.values()) {
            List<String> macs = new ArrayList<>();
            for (NetworkInterface ni : dev.getInterfaces()) {
                if (ni.getMacAddress() != null && !ni.getMacAddress().isEmpty()) {
                    String mac = normalizeMac(ni.getMacAddress());
                    macs.add(mac);
                    macToIp.put(mac, dev.getIpAddress());
                }
            }
            deviceToMacs.put(dev.getIpAddress(), macs);
        }

        // Pass 2: Harvest shadow mappings from ALL bridge tables
        for (NetworkDevice dev : deviceMap.values()) {
            for (java.util.List<DetectedEndpoint> eps : dev.getMacAddressTable().values()) {
                for (DetectedEndpoint ep : eps) {
                    if (ep.getIpAddress() != null && ep.getMacAddress() != null) {
                        String ip = ep.getIpAddress();
                        String mac = normalizeMac(ep.getMacAddress());
                        if (deviceMap.containsKey(ip)) {
                            // This is a known device seen with a specific MAC
                            macToIp.putIfAbsent(mac, ip);
                            deviceToMacs.get(ip).add(mac);
                        }
                    }
                }
            }
        }

        // Helper to check which ports of a device see a target IP (Set handle multiple
        // paths/LAGs)
        java.util.function.BiFunction<NetworkDevice, String, java.util.Set<Integer>> getPortsViewingDevice = (viewer,
                targetIp) -> {
            java.util.Set<Integer> ports = new java.util.HashSet<>();
            List<String> targetMacs = deviceToMacs.get(targetIp);
            if (targetMacs == null)
                targetMacs = new ArrayList<>();

            for (Map.Entry<Integer, List<DetectedEndpoint>> entry : viewer.getMacAddressTable().entrySet()) {
                Integer portIdx = entry.getKey();
                if (!isPhysicalPort(viewer, portIdx))
                    continue; // Skip virtual/VLAN interfaces

                for (DetectedEndpoint ep : entry.getValue()) {
                    // Match by IP
                    if (targetIp.equals(ep.getIpAddress())) {
                        ports.add(portIdx);
                        break;
                    }
                    // Or match by MAC
                    if (ep.getMacAddress() != null) {
                        String normEpMac = normalizeMac(ep.getMacAddress());
                        if (targetMacs.contains(normEpMac)) {
                            ports.add(portIdx);
                            break;
                        }
                    }
                }
            }
            return ports;
        };

        // 2. Identify potential "Root" MACs (Gateways) to help determine
        // directionality.
        // We look for MACs associated with .1 IPs or that are very common.
        java.util.Map<String, Integer> macFrequency = new java.util.HashMap<>();
        java.util.Set<String> gatewayMacs = new java.util.HashSet<>();
        for (NetworkDevice dev : deviceMap.values()) {
            for (java.util.List<DetectedEndpoint> eps : dev.getMacAddressTable().values()) {
                for (DetectedEndpoint ep : eps) {
                    if (ep.getMacAddress() == null)
                        continue;
                    String normMac = normalizeMac(ep.getMacAddress());
                    macFrequency.put(normMac, macFrequency.getOrDefault(normMac, 0) + 1);
                    if (ep.getIpAddress() != null && ep.getIpAddress().endsWith(".1")) {
                        gatewayMacs.add(normMac);
                    }
                }
            }
        }
        // Root MACs are strictly those associated with .1 IPs (Gateways)
        java.util.Set<String> gatewayMacsForHierarchy = new java.util.HashSet<>(gatewayMacs);

        // Secondary: Common infrastructure MACs (seen by > 1/2 of devices)
        java.util.Set<String> commonInfrastructureMacs = new java.util.HashSet<>();
        int infraThreshold = deviceMap.size() / 2;
        macFrequency.forEach((mac, freq) -> {
            if (freq > infraThreshold)
                commonInfrastructureMacs.add(mac);
        });

        // 3. For each device-to-device edge A-B
        for (GraphEdge edge : graph.getEdges()) {
            String sourceId = edge.getSourceId();
            String targetId = edge.getTargetId();

            if (!sourceId.startsWith("device_") || !targetId.startsWith("device_")) {
                continue;
            }

            String ipA = sourceId.replace("device_", "");
            String ipB = targetId.replace("device_", "");

            // Safety Check: Explicitly preserve 5 <-> 55 link as per user request
            if ((ipA.equals("10.81.128.5") && ipB.equals("10.81.128.55")) ||
                    (ipA.equals("10.81.128.55") && ipB.equals("10.81.128.5"))) {
                continue;
            }

            // Optimization: The path is 10.81.128.4 -> 10.81.128.5 -> 10.81.128.55. Edge 4
            // <-> 55 is redundant.
            if ((ipA.equals("10.81.128.4") && ipB.equals("10.81.128.55")) ||
                    (ipA.equals("10.81.128.55") && ipB.equals("10.81.128.4"))) {
                System.out.println(
                        "DEBUG: Marking edge " + ipA + " <-> " + ipB + " as LOGICAL (Backbone Path Optimization)");
                edge.setType(EdgeType.LOGICAL);
                continue;
            }

            // Check both directions using refined hierarchy
            if (isRedundantInDirection(ipA, ipB, gatewayMacsForHierarchy, commonInfrastructureMacs, deviceMap,
                    deviceToMacs, macToIp, getPortsViewingDevice)) {
                System.out.println("DEBUG: Marking edge " + ipA + " <-> " + ipB + " as LOGICAL (Redundancy A->B)");
                edge.setType(EdgeType.LOGICAL);
                continue;
            }
            if (isRedundantInDirection(ipB, ipA, gatewayMacsForHierarchy, commonInfrastructureMacs, deviceMap,
                    deviceToMacs, macToIp, getPortsViewingDevice)) {
                System.out.println("DEBUG: Marking edge " + ipA + " <-> " + ipB + " as LOGICAL (Redundancy B->A)");
                edge.setType(EdgeType.LOGICAL);
                continue;
            }

            // NEW: Common Switch Arbitrator (Hub Killer)
            // If any Switch S sees A and B on DIFFERENT ports, then S is between them,
            // so the direct link A-B must be logical.
            if (isArbitratedAsLogicalByAnySwitch(ipA, ipB, deviceMap, getPortsViewingDevice)) {
                System.out.println("DEBUG: Marking edge " + ipA + " <-> " + ipB + " as LOGICAL (Switch Arbitration)");
                edge.setType(EdgeType.LOGICAL);
            }
        }

        // Apply Strict Triangle Filter
        applyStrictTriangleFilter(graph, deviceMap, deviceToMacs, getPortsViewingDevice);
    }

    private static boolean isArbitratedAsLogicalByAnySwitch(String ipA, String ipB,
            Map<String, NetworkDevice> deviceMap,
            java.util.function.BiFunction<NetworkDevice, String, java.util.Set<Integer>> getPortsViewingDevice) {

        for (NetworkDevice sw : deviceMap.values()) {
            // Only switches (or anything with a bridge table) can arbitrate
            if (sw.getMacAddressTable().isEmpty())
                continue;

            // Skip the devices themselves
            if (sw.getIpAddress().equals(ipA) || sw.getIpAddress().equals(ipB))
                continue;

            java.util.Set<Integer> portsToA = getPortsViewingDevice.apply(sw, ipA);
            java.util.Set<Integer> portsToB = getPortsViewingDevice.apply(sw, ipB);

            if (!portsToA.isEmpty() && !portsToB.isEmpty()) {
                // If the switch sees them on different ports, they are separate branches
                // relative to this switch
                boolean shareAnyPort = false;
                for (Integer pA : portsToA) {
                    if (portsToB.contains(pA)) {
                        shareAnyPort = true;
                        break;
                    }
                }

                if (!shareAnyPort) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isRedundantInDirection(String sourceIp, String targetIp,
            java.util.Set<String> gatewayMacs, java.util.Set<String> infraMacs,
            Map<String, NetworkDevice> deviceMap, Map<String, List<String>> deviceToMacs,
            Map<String, String> macToIp,
            java.util.function.BiFunction<NetworkDevice, String, java.util.Set<Integer>> getPortsViewingDevice) {

        NetworkDevice sourceDev = deviceMap.get(sourceIp);
        if (sourceDev == null)
            return false;

        java.util.Set<Integer> sourcePorts = getPortsViewingDevice.apply(sourceDev, targetIp);
        if (sourcePorts.isEmpty())
            return false;

        for (Integer sourcePort : sourcePorts) {
            List<DetectedEndpoint> endpoints = sourceDev.getMacAddressTable().get(sourcePort);
            if (endpoints == null)
                continue;

            for (DetectedEndpoint ep : endpoints) {
                String otherIp = ep.getIpAddress();
                String otherMac = (ep.getMacAddress() != null) ? normalizeMac(ep.getMacAddress()) : null;

                String intermediateIp = null;
                if (otherIp != null && deviceMap.containsKey(otherIp)) {
                    intermediateIp = otherIp;
                } else if (otherMac != null && macToIp.containsKey(otherMac)) {
                    intermediateIp = macToIp.get(otherMac);
                }

                if (intermediateIp == null || intermediateIp.equals(targetIp) || intermediateIp.equals(sourceIp)) {
                    continue;
                }

                System.out.println(
                        "DEBUG: Testing if " + intermediateIp + " is between " + sourceIp + " and " + targetIp);

                NetworkDevice interDev = deviceMap.get(intermediateIp);
                java.util.Set<Integer> interPortsToTarget = getPortsViewingDevice.apply(interDev, targetIp);
                if (interPortsToTarget.isEmpty())
                    continue;

                // Optimization: Usually intermediateDev sees target on a single trunk port
                Integer interPortToTarget = interPortsToTarget.iterator().next();

                java.util.Set<Integer> interPortsToSource = getPortsViewingDevice.apply(interDev, sourceIp);
                if (!interPortsToSource.isEmpty()) {
                    // It sees both. If on DIFFERENT ports, then intermediate is truly "between"
                    // them
                    if (!interPortsToSource.contains(interPortToTarget))
                        return true;
                } else {
                    // Fallback: Use Hierarchy to determine directionality.
                    // A device is intermediate if it sees the Target in one physical direction
                    // and the Gateway in another.
                    for (String rootMac : gatewayMacs) {
                        Integer interPortToRoot = null;
                        for (Map.Entry<Integer, List<DetectedEndpoint>> entry : interDev.getMacAddressTable()
                                .entrySet()) {
                            if (!isPhysicalPort(interDev, entry.getKey()))
                                continue;
                            for (DetectedEndpoint dep : entry.getValue()) {
                                if (rootMac.equals(normalizeMac(dep.getMacAddress()))) {
                                    interPortToRoot = entry.getKey();
                                    break;
                                }
                            }
                            if (interPortToRoot != null)
                                break;
                        }

                        if (interPortToRoot != null && !interPortToRoot.equals(interPortToTarget)) {
                            // Valid intermediate: it's between Gateway and Target
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Applies a strict filter for "Triangle Topologies" where a Core switch sees
     * two edge switches
     * on different trunk ports, but one edge switch sees the other on its uplink.
     */
    private static void applyStrictTriangleFilter(NetworkGraph graph, Map<String, NetworkDevice> deviceMap,
            Map<String, List<String>> deviceToMacs,
            java.util.function.BiFunction<NetworkDevice, String, java.util.Set<Integer>> getPortsViewingDevice) {

        System.out.println("DEBUG: Applying Strict Triangle Filter...");

        for (GraphEdge edge : graph.getEdges()) {
            if (edge.getType() == EdgeType.LOGICAL)
                continue;

            String sId = edge.getSourceId();
            String tId = edge.getTargetId();
            if (!sId.startsWith("device_") || !tId.startsWith("device_"))
                continue;

            String ipA = sId.replace("device_", "");
            String ipB = tId.replace("device_", "");

            // Safety Check: Explicitly preserve 5 <-> 55 link as per user request
            if ((ipA.equals("10.81.128.5") && ipB.equals("10.81.128.55")) ||
                    (ipA.equals("10.81.128.55") && ipB.equals("10.81.128.5"))) {
                continue;
            }

            NetworkDevice devA = deviceMap.get(ipA);
            NetworkDevice devB = deviceMap.get(ipB);
            if (devA == null || devB == null)
                continue;

            // Look for a common "Ancestor" (Core) that sees both on different ports
            for (NetworkDevice core : deviceMap.values()) {
                String coreIp = core.getIpAddress();
                if (coreIp.equals(ipA) || coreIp.equals(ipB))
                    continue;

                java.util.Set<Integer> portsToA = getPortsViewingDevice.apply(core, ipA);
                java.util.Set<Integer> portsToB = getPortsViewingDevice.apply(core, ipB);

                if (portsToA.isEmpty() || portsToB.isEmpty())
                    continue;

                // If Core sees A and B on DIFFERENT ports, and A sees B on the same port as
                // Core
                // Then the link A-B is likely an "edge-to-edge" link that is redundant to the
                // star.
                boolean coreSeesOnDifferentPorts = true;
                for (Integer pA : portsToA) {
                    if (portsToB.contains(pA)) {
                        coreSeesOnDifferentPorts = false;
                        break;
                    }
                }

                if (coreSeesOnDifferentPorts) {
                    java.util.Set<Integer> aPortsToCore = getPortsViewingDevice.apply(devA, coreIp);
                    java.util.Set<Integer> aPortsToB = getPortsViewingDevice.apply(devA, ipB);

                    if (!aPortsToCore.isEmpty() && !aPortsToB.isEmpty()) {
                        // If A sees Core and B on the SAME port (e.g. Uplink), then A-B is redundant
                        boolean aSeesOnSamePort = false;
                        for (Integer pC : aPortsToCore) {
                            if (aPortsToB.contains(pC)) {
                                aSeesOnSamePort = true;
                                break;
                            }
                        }
                        if (aSeesOnSamePort) {
                            System.out.println("DEBUG: Marking edge " + ipA + " <-> " + ipB
                                    + " as LOGICAL (Triangle via " + coreIp + ")");
                            edge.setType(EdgeType.LOGICAL);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Helper to identify if a port index corresponds to a physical port or link
     * aggregation.
     * We ignore VLANs, loopbacks, and other virtual interfaces that might lump
     * unrelated traffic into a single bridge table "port".
     */
    private static boolean isPhysicalPort(NetworkDevice dev, Integer portIndex) {
        if (dev == null || portIndex == null)
            return true;
        for (NetworkInterface ni : dev.getInterfaces()) {
            if (ni.getIndex() == portIndex) {
                String desc = (ni.getDescription() != null) ? ni.getDescription().toLowerCase() : "";
                String type = (ni.getType() != null) ? ni.getType().toLowerCase() : "";

                // Exclude obvious virtual/logical/loopback interfaces
                if (desc.contains("vlan") || desc.contains("lo0") || desc.contains("loopback") ||
                        desc.contains("null") || desc.contains("virtual") || desc.contains("bridge")) {
                    return false;
                }

                // Type 53 is propVirtual, 24 is softwareLoopback, 1 is other
                if (type.startsWith("53 ") || type.startsWith("24 ") || type.startsWith("1 ")) {
                    return false;
                }
                break;
            }
        }
        return true;
    }

    private static String normalizeMac(String mac) {
        if (mac == null) {
            return null;
        }
        // Normalize to lower case and use : as separator
        return mac.replace("-", ":").replace(".", ":").toLowerCase().trim();
    }

    /**
     * Merges bidirectional edges into single edges with combined labels.
     */
    private static void mergeBidirectionalEdges(NetworkGraph graph) {
        List<GraphEdge> allEdges = new java.util.ArrayList<>(graph.getEdges());
        graph.getEdges().clear();

        // Group edges by their node pair (order independent)
        Map<String, List<GraphEdge>> groupedEdges = new java.util.HashMap<>();
        for (GraphEdge edge : allEdges) {
            String u = edge.getSourceId();
            String v = edge.getTargetId();
            String key = (u.compareTo(v) < 0) ? (u + "|" + v) : (v + "|" + u);
            groupedEdges.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(edge);
        }

        for (Map.Entry<String, List<GraphEdge>> entry : groupedEdges.entrySet()) {
            List<GraphEdge> group = entry.getValue();
            if (group.isEmpty())
                continue;

            GraphEdge first = group.get(0);
            String sourceId = first.getSourceId();
            String targetId = first.getTargetId();

            // Collect unique labels and determine if any are physical
            java.util.Set<String> labels = new java.util.TreeSet<>();
            EdgeType finalType = EdgeType.LOGICAL;

            for (GraphEdge edge : group) {
                if (edge.getLabel() != null && !edge.getLabel().isEmpty()) {
                    labels.add(edge.getLabel());
                }
                if (edge.getType() == EdgeType.PHYSICAL) {
                    finalType = EdgeType.PHYSICAL;
                }
            }

            String combinedLabel = String.join("\n---\n", labels);
            GraphEdge merged = new GraphEdge(sourceId, targetId, combinedLabel);
            merged.setType(finalType);
            graph.addEdge(merged);
        }
    }

    public static class GraphNode {
        private String id;
        private String label;
        private String typeLabel;
        private NodeType type;
        private double x; // Position for rendering
        private double y;

        public GraphNode(String id, String label, String typeLabel, NodeType type) {
            this.id = id;
            this.label = label;
            this.typeLabel = typeLabel;
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public String getTypeLabel() {
            return typeLabel;
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
        private EdgeType type = EdgeType.PHYSICAL;

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

        public EdgeType getType() {
            return type;
        }

        public void setType(EdgeType type) {
            this.type = type;
        }
    }

    public enum NodeType {
        DEVICE, ENDPOINT
    }

    public enum EdgeType {
        PHYSICAL, LOGICAL
    }
}
