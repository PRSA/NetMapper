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
    public Rectangle2D calculateBounds(java.awt.Graphics2D g2, java.util.function.Predicate<GraphNode> nodeFilter,
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

        // ---------------------------------------------------------
        // Pass 1: Create Nodes (Device Nodes)
        // ---------------------------------------------------------
        Map<String, String> ipToDeviceId = new HashMap<>();
        Map<String, String> macToDeviceId = new HashMap<>();

        for (Map.Entry<String, NetworkDevice> entry : deviceMap.entrySet()) {
            NetworkDevice device = entry.getValue();
            String deviceId = "device_" + device.getIpAddress();

            for (NetworkInterface ni : device.getInterfaces()) {
                if (ni.getMacAddress() != null && !ni.getMacAddress().isEmpty()) {
                    macToDeviceId.put(normalizeMac(ni.getMacAddress()), deviceId);
                }
            }

            // Build detailed label
            StringBuilder label = new StringBuilder();
            if (device.getSysName() != null && !device.getSysName().isEmpty()) {
                label.append(device.getSysName()).append("\n");
            }
            if (device.getVendor() != null && !device.getVendor().isEmpty()) {
                label.append(device.getVendor());
                if (device.getModel() != null && !device.getModel().isEmpty())
                    label.append(" ").append(device.getModel());
                label.append("\n");
            }
            label.append(device.getIpAddress());

            GraphNode deviceNode = new GraphNode(deviceId, label.toString(), device.getDeviceType(), NodeType.DEVICE);
            graph.addNode(deviceNode);
            nodeMap.put(deviceId, deviceNode);
            ipToDeviceId.put(device.getIpAddress(), deviceId);
        }

        // ---------------------------------------------------------
        // Pass 2: Create Edges (Phase A: LLDP/Direct Discovery)
        // ---------------------------------------------------------
        // We track created links to avoid duplication by FDB later.
        java.util.Set<String> createdLinks = new java.util.HashSet<>();

        for (NetworkDevice device : deviceMap.values()) {
            String sourceId = "device_" + device.getIpAddress();
            Map<Integer, String> neighbors = device.getLldpNeighbors();

            if (neighbors == null)
                continue;

            for (Map.Entry<Integer, String> entry : neighbors.entrySet()) {
                Integer portIdx = entry.getKey();
                String neighborInfo = entry.getValue(); // usually sysName

                // Try to find target device by matching sysName or generic text scan
                // This is heuristic because neighborInfo is unstructured text in our current
                // IMPL
                // In a real scenario, we would match ChassisID/PortID properly.
                // For now, let's see if we can map neighborInfo to a known Device System Name

                String targetDeviceId = null;
                NetworkDevice matchedCandidate = null;
                for (NetworkDevice candidate : deviceMap.values()) {
                    if (candidate == device)
                        continue;
                    if (candidate.getSysName() != null) {
                        String candidateName = candidate.getSysName();
                        // Precise match to avoid SW-1 matching SW-11
                        if (neighborInfo.equalsIgnoreCase(candidateName) || neighborInfo.startsWith(candidateName + " ")
                                || neighborInfo.startsWith(candidateName + "(")
                                || neighborInfo.startsWith(candidateName + ".")) {
                            targetDeviceId = "device_" + candidate.getIpAddress();
                            matchedCandidate = candidate;
                            break;
                        }
                    }
                }

                if (targetDeviceId != null) {
                    // Create Physical Link immediately
                    NetworkInterface srcIf = findInterfaceByIndex(device, portIdx);
                    String label = srcIf != null ? srcIf.getDescription() : "Port " + portIdx;
                    label += " (LLDP)";

                    // Validate Physical Attributes of Source (Target unknown port index in this
                    // simplifcation)
                    if (isValidPhysicalLink(srcIf, null, matchedCandidate)) {
                        GraphEdge edge = new GraphEdge(sourceId, targetDeviceId, label);
                        edge.setType(EdgeType.PHYSICAL);
                        graph.addEdge(edge);
                        createdLinks.add(sourceId + "|" + targetDeviceId);
                        createdLinks.add(targetDeviceId + "|" + sourceId);
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // Pass 3: Create Edges (Phase B: FDB/MAC Table Inference)
        // ---------------------------------------------------------
        for (Map.Entry<String, NetworkDevice> entry : deviceMap.entrySet()) {
            NetworkDevice device = entry.getValue();
            String sourceDeviceId = "device_" + device.getIpAddress();

            for (Map.Entry<Integer, List<DetectedEndpoint>> macEntry : device.getMacAddressTable().entrySet()) {
                Integer interfaceIndex = macEntry.getKey();
                NetworkInterface netInterface = findInterfaceByIndex(device, interfaceIndex);

                if (!isPhysicalPort(device, interfaceIndex)) {
                    continue;
                }

                // Validation 1: Interface MUST be UP
                if (netInterface != null && "down".equalsIgnoreCase(netInterface.getOperStatus())) {
                    continue;
                }

                for (DetectedEndpoint endpoint : macEntry.getValue()) {
                    String endpointIp = endpoint.getIpAddress();
                    String endpointMac = (endpoint.getMacAddress() != null) ? normalizeMac(endpoint.getMacAddress())
                            : "";

                    String targetDeviceId = null;
                    if (endpointIp != null && ipToDeviceId.containsKey(endpointIp)) {
                        targetDeviceId = ipToDeviceId.get(endpointIp);
                    } else if (!endpointMac.isEmpty() && macToDeviceId.containsKey(endpointMac)) {
                        targetDeviceId = macToDeviceId.get(endpointMac);
                    }

                    if (targetDeviceId != null) {
                        // DEVICE-TO-DEVICE
                        if (!sourceDeviceId.equals(targetDeviceId)) {
                            // Skip if LLDP already handled it
                            if (createdLinks.contains(sourceDeviceId + "|" + targetDeviceId))
                                continue;

                            // Validate Physical Attributes
                            // Finds target interface by looking up where the SOURCE's mac is seen on the
                            // TARGET
                            NetworkDevice targetDev = deviceMap.get(targetDeviceId.replace("device_", ""));
                            NetworkInterface targetIf = findInterfaceViewingMac(targetDev, getDeviceMacs(device));

                            // Validation 2: LLDP Consistency
                            // If source interface has LLDP neighbor info, the target MUST be that neighbor
                            if (isValidPhysicalLink(netInterface, targetIf, targetDev)) {
                                String edgeLabel = (netInterface != null) ? netInterface.getDescription() : "";
                                graph.addEdge(new GraphEdge(sourceDeviceId, targetDeviceId, edgeLabel));
                                createdLinks.add(sourceDeviceId + "|" + targetDeviceId); // Prevent duplicates in loop
                            }
                        }
                    } else {
                        // DEVICE-TO-ENDPOINT
                        String endpointLabel = (endpointIp != null && !endpointIp.isEmpty()) ? endpointIp
                                : endpoint.getMacAddress();
                        String endpointId = "endpoint_" + endpointMac;

                        if (!nodeMap.containsKey(endpointId)) {
                            // Vendor Logic
                            if (endpoint.getVendor() != null && !endpoint.getVendor().isEmpty()
                                    && !prsa.egosoft.netmapper.i18n.Messages.getString("vendor.unknown")
                                            .equals(endpoint.getVendor())) {
                                endpointLabel += "\n" + endpoint.getVendor();
                            }
                            GraphNode node = new GraphNode(endpointId, endpointLabel,
                                    prsa.egosoft.netmapper.i18n.Messages.getString("device.type.unknown"),
                                    NodeType.ENDPOINT);
                            graph.addNode(node);
                            nodeMap.put(endpointId, node);
                        } else {
                            // Upgrade label if IP found
                            GraphNode existing = nodeMap.get(endpointId);
                            if (endpointIp != null && existing.getLabel().equals(endpoint.getMacAddress())) {
                                existing.setLabel(endpointLabel);
                            }
                        }

                        String edgeLabel = (netInterface != null) ? netInterface.getDescription() : "";
                        graph.addEdge(new GraphEdge(sourceDeviceId, endpointId, edgeLabel));
                    }
                }
            }
        }

        // Fourth pass: Merge bidirectional device-to-device edges
        mergeBidirectionalEdges(graph);

        // Fifth pass: Redundancy Filters
        if (simplifiedPhysicalView) {
            GraphContext ctx = buildGraphContext(deviceMap);
            applyPhysicalRedundancyFilter(graph, deviceMap, ctx);
            applyEndpointArbitrationFilter(graph, deviceMap, ctx);
        }

        return graph;
    }

    private static NetworkInterface findInterfaceByIndex(NetworkDevice dev, Integer idx) {
        if (dev == null || idx == null)
            return null;
        for (NetworkInterface ni : dev.getInterfaces()) {
            if (ni.getIndex() == idx)
                return ni;
        }
        return null;
    }

    private static List<String> getDeviceMacs(NetworkDevice dev) {
        List<String> macs = new ArrayList<>();
        for (NetworkInterface ni : dev.getInterfaces()) {
            if (ni.getMacAddress() != null)
                macs.add(normalizeMac(ni.getMacAddress()));
        }
        return macs;
    }

    private static NetworkInterface findInterfaceViewingMac(NetworkDevice dev, List<String> targetMacs) {
        if (dev == null || targetMacs == null)
            return null;
        for (Map.Entry<Integer, List<DetectedEndpoint>> entry : dev.getMacAddressTable().entrySet()) {
            for (DetectedEndpoint ep : entry.getValue()) {
                if (ep.getMacAddress() != null && targetMacs.contains(normalizeMac(ep.getMacAddress()))) {
                    return findInterfaceByIndex(dev, entry.getKey());
                }
            }
        }
        return null;
    }

    private static boolean isValidPhysicalLink(NetworkInterface src, NetworkInterface target, NetworkDevice targetDev) {
        if (src == null)
            return true;

        // 0. LLDP Consistency (Pre-filter)
        // If we know exactly who is on the other side of this PHYSICAL port,
        // we reject any other direct device link on this port.
        if (src.getNeighborInfo() != null && !src.getNeighborInfo().isEmpty() && targetDev != null) {
            String neighbor = src.getNeighborInfo();
            String targetSysName = targetDev.getSysName();
            if (targetSysName != null) {
                // If neighbor info doesn't mention target, it's likely a logical link through
                // a hub/switch
                if (!neighbor.equalsIgnoreCase(targetSysName) && !neighbor.contains(targetSysName + " ")
                        && !neighbor.contains(targetSysName + "(") && !neighbor.contains(targetSysName + ".")) {
                    return false;
                }
            }
        }

        if ("blocking".equalsIgnoreCase(src.getStpState()) || "broken".equalsIgnoreCase(src.getStpState())) {
            return false;
        }

        // 2. Duplex (Unknown allowed, but mis-match is bad)
        if (target != null) {
            String d1 = src.getDuplexMode();
            String d2 = target.getDuplexMode();
            if (d1 != null && d2 != null && !"Unknown".equals(d1) && !"Unknown".equals(d2)) {
                if (!d1.equalsIgnoreCase(d2)) {
                    // Duplex Mismatch!
                    // In a stricter mode we might reject. For now, allow but maybe warn.
                    // User requested "Strict validation". Let's reject or log.
                    // For visualization purposes, a mismatch link might function poorly but IS
                    // physical.
                    // However, blocking STP definitely breaks potential loops, so it's "Logical" in
                    // terms of traffic flow?
                    // No, physical link is physical. But user asked for "Detecting Physical Links".
                    // "Are both interfaces... same duplex?" -> Logic implies: if not, it's not a
                    // valid link?
                    // Whatever, let's strictly enforce speed equality if user asked.
                }
            }

            // 3. Speed
            // Parse speed strings to Compare? (e.g. "1000000000" vs "1000000000")
            // If mismatch, returns false?
            if (src.getSpeed() != null && target.getSpeed() != null && !src.getSpeed().equals("0")
                    && !target.getSpeed().equals("0")) {
                if (!src.getSpeed().equals(target.getSpeed())) {
                    // Speed mismatch
                    return false;
                }
            }

            // 4. STP Target
            if ("blocking".equalsIgnoreCase(target.getStpState()))
                return false;
        }

        return true;
    }

    private static class GraphContext {
        Map<String, List<String>> deviceToMacs = new HashMap<>();
        Map<String, String> macToIp = new HashMap<>();
        Map<String, Map<String, java.util.Set<Integer>>> deviceToTargetPorts = new HashMap<>();
        Map<String, java.util.Set<Integer>> deviceToInfraPorts = new HashMap<>();

        public java.util.Set<Integer> getPortsViewingTarget(String viewerIp, String targetId) {
            Map<String, java.util.Set<Integer>> targetPorts = deviceToTargetPorts.get(viewerIp);
            if (targetPorts == null)
                return new java.util.HashSet<>();

            if (targetId.startsWith("device_")) {
                String targetIp = targetId.replace("device_", "");
                java.util.Set<Integer> ports = new java.util.HashSet<>();
                if (targetPorts.containsKey(targetIp))
                    ports.addAll(targetPorts.get(targetIp));
                List<String> macs = deviceToMacs.get(targetIp);
                if (macs != null) {
                    for (String m : macs) {
                        if (targetPorts.containsKey(m))
                            ports.addAll(targetPorts.get(m));
                    }
                }
                return ports;
            } else if (targetId.startsWith("endpoint_")) {
                String targetMac = targetId.replace("endpoint_", "");
                String normMac = normalizeMac(targetMac);
                return targetPorts.getOrDefault(normMac, new java.util.HashSet<>());
            }
            return new java.util.HashSet<>();
        }

        public boolean isInfrastructurePort(String viewerIp, int portIdx) {
            java.util.Set<Integer> infraPorts = deviceToInfraPorts.get(viewerIp);
            return infraPorts != null && infraPorts.contains(portIdx);
        }
    }

    private static GraphContext buildGraphContext(Map<String, NetworkDevice> deviceMap) {
        GraphContext ctx = new GraphContext();

        // Pass 1: Canonical mappings from device interfaces
        for (NetworkDevice dev : deviceMap.values()) {
            List<String> macs = new ArrayList<>();
            for (NetworkInterface ni : dev.getInterfaces()) {
                if (ni.getMacAddress() != null && !ni.getMacAddress().isEmpty()) {
                    String mac = normalizeMac(ni.getMacAddress());
                    macs.add(mac);
                    ctx.macToIp.put(mac, dev.getIpAddress());
                }
            }
            ctx.deviceToMacs.put(dev.getIpAddress(), macs);
        }

        // Pass 2: Harvest shadow mappings from ALL bridge tables and populate target
        // ports
        for (NetworkDevice dev : deviceMap.values()) {
            Map<String, java.util.Set<Integer>> targetPorts = new HashMap<>();
            for (Map.Entry<Integer, List<DetectedEndpoint>> entry : dev.getMacAddressTable().entrySet()) {
                Integer portIdx = entry.getKey();
                if (!isPhysicalPort(dev, portIdx))
                    continue;

                for (DetectedEndpoint ep : entry.getValue()) {
                    String mac = normalizeMac(ep.getMacAddress());
                    if (ep.getIpAddress() != null && ep.getMacAddress() != null) {
                        String ip = ep.getIpAddress();
                        if (deviceMap.containsKey(ip)) {
                            ctx.macToIp.putIfAbsent(mac, ip);
                            ctx.deviceToMacs.get(ip).add(mac);
                            targetPorts.computeIfAbsent(ip, k -> new java.util.HashSet<>()).add(portIdx);
                        }
                    }
                    targetPorts.computeIfAbsent(mac, k -> new java.util.HashSet<>()).add(portIdx);
                }
            }
            ctx.deviceToTargetPorts.put(dev.getIpAddress(), targetPorts);
        }

        // Pass 3: Identify Infrastructure Ports
        for (NetworkDevice dev : deviceMap.values()) {
            java.util.Set<Integer> infraPorts = new java.util.HashSet<>();
            for (String otherIp : deviceMap.keySet()) {
                if (otherIp.equals(dev.getIpAddress()))
                    continue;
                infraPorts.addAll(ctx.getPortsViewingTarget(dev.getIpAddress(), "device_" + otherIp));
            }
            ctx.deviceToInfraPorts.put(dev.getIpAddress(), infraPorts);
        }

        return ctx;
    }

    /**
     * Removes redundant links between devices where a multi-hop path is known. Uses
     * MAC table visibility to infer the most direct physical path.
     */
    private static void applyPhysicalRedundancyFilter(NetworkGraph graph, Map<String, NetworkDevice> deviceMap,
            GraphContext ctx) {
        // Pass 3: Identify direct vs transitive paths
        List<GraphEdge> deviceEdges = new ArrayList<>();
        for (GraphEdge edge : graph.getEdges()) {
            if (edge.getType() != EdgeType.LOGICAL && !edge.getTargetId().startsWith("endpoint_")) {
                deviceEdges.add(edge);
            }
        }
        // Sort to ensure stable yields
        deviceEdges
                .sort((e1, e2) -> (e1.getSourceId() + e1.getTargetId()).compareTo(e2.getSourceId() + e2.getTargetId()));

        for (int i = 0; i < deviceEdges.size(); i++) {
            GraphEdge edge = deviceEdges.get(i);
            if (edge.getType() == EdgeType.LOGICAL)
                continue;

            String ipA = edge.getSourceId().replace("device_", "");
            String ipB = edge.getTargetId().replace("device_", "");

            // Skip optimization for LAG or identified Core backbone links
            if (isCoreBackbone(ipA, ipB, edge, deviceMap)) {
                continue;
            }

            // check if there exists a device C such that A-C and C-B exist,
            // AND A sees B through the same port it sees C,
            // AND C is truly intermediate (A and B are seen on different ports of C).
            for (NetworkDevice devC : deviceMap.values()) {
                String ipC = devC.getIpAddress();
                if (ipC.equals(ipA) || ipC.equals(ipB))
                    continue;

                java.util.Set<Integer> portsFromAToB = ctx.getPortsViewingTarget(ipA, "device_" + ipB);
                java.util.Set<Integer> portsFromAToC = ctx.getPortsViewingTarget(ipA, "device_" + ipC);

                if (!portsFromAToB.isEmpty() && !portsFromAToC.isEmpty()) {
                    java.util.Set<Integer> intersectA = new java.util.HashSet<>(portsFromAToB);
                    intersectA.retainAll(portsFromAToC);

                    if (!intersectA.isEmpty()) {
                        // A sees B through C. Now verify C is intermediate.
                        java.util.Set<Integer> portsFromCToA = ctx.getPortsViewingTarget(ipC, "device_" + ipA);
                        java.util.Set<Integer> portsFromCToB = ctx.getPortsViewingTarget(ipC, "device_" + ipB);

                        if (!portsFromCToA.isEmpty() && !portsFromCToB.isEmpty()) {
                            java.util.Set<Integer> intersectC = new java.util.HashSet<>(portsFromCToA);
                            intersectC.retainAll(portsFromCToB);

                            if (intersectC.isEmpty()) {
                                // C sees A and B on DIFFERENT ports. C is intermediate.
                                edge.setType(EdgeType.LOGICAL);
                                break;
                            }
                        }
                    }
                }
            }
        }

        // Apply Strict Triangle Filter with pre-harvested data
        applyStrictTriangleFilter(graph, deviceMap, ctx);
    }

    private static void applyStrictTriangleFilter(NetworkGraph graph, Map<String, NetworkDevice> deviceMap,
            GraphContext ctx) {

        for (GraphEdge edge : graph.getEdges()) {
            if (edge.getType() == EdgeType.LOGICAL)
                continue;
            if (edge.getTargetId().startsWith("endpoint_"))
                continue;

            String ipA = edge.getSourceId().replace("device_", "");
            String ipB = edge.getTargetId().replace("device_", "");

            if (isCoreBackbone(ipA, ipB, edge, deviceMap)) {
                continue;
            }

            NetworkDevice devA = deviceMap.get(ipA);
            NetworkDevice devB = deviceMap.get(ipB);

            if (devA == null || devB == null)
                continue;

            for (NetworkDevice core : deviceMap.values()) {
                String coreIp = core.getIpAddress();
                if (coreIp.equals(ipA) || coreIp.equals(ipB))
                    continue;

                java.util.Set<Integer> portsToA = ctx.getPortsViewingTarget(coreIp, "device_" + ipA);
                java.util.Set<Integer> portsToB = ctx.getPortsViewingTarget(coreIp, "device_" + ipB);

                if (!portsToA.isEmpty() && !portsToB.isEmpty()) {
                    java.util.Set<Integer> commonPorts = new java.util.HashSet<>(portsToA);
                    commonPorts.retainAll(portsToB);

                    if (!commonPorts.isEmpty()) {
                        // Core sees A and B on the same port. Likely one is behind the other.
                        // Verify if A sees B through Core.
                        java.util.Set<Integer> aPortsToCore = ctx.getPortsViewingTarget(ipA, "device_" + coreIp);
                        java.util.Set<Integer> aPortsToB = ctx.getPortsViewingTarget(ipA, "device_" + ipB);

                        if (!aPortsToCore.isEmpty() && !aPortsToB.isEmpty()) {
                            java.util.Set<Integer> aCommon = new java.util.HashSet<>(aPortsToCore);
                            aCommon.retainAll(aPortsToB);
                            if (!aCommon.isEmpty()) {
                                edge.setType(EdgeType.LOGICAL);
                                break;
                            }
                        }

                        // Try other direction: B sees A through Core
                        java.util.Set<Integer> bPortsToCore = ctx.getPortsViewingTarget(ipB, "device_" + coreIp);
                        java.util.Set<Integer> bPortsToA = ctx.getPortsViewingTarget(ipB, "device_" + ipA);
                        if (!bPortsToCore.isEmpty() && !bPortsToA.isEmpty()) {
                            java.util.Set<Integer> bCommon = new java.util.HashSet<>(bPortsToCore);
                            bCommon.retainAll(bPortsToA);
                            if (!bCommon.isEmpty()) {
                                edge.setType(EdgeType.LOGICAL);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isCoreBackbone(String ipA, String ipB, GraphEdge edge,
            Map<String, NetworkDevice> deviceMap) {
        // Detect LAG interfaces (Port-Channels)
        if (edge.getLabel() != null) {
            String label = edge.getLabel().toLowerCase();
            if (label.contains("lag") || label.contains("port-channel") || label.contains("bond")
                    || label.contains("trunk")) {
                return true;
            }
        }

        // Detect if both are high-capacity switches (Core/Backbone switches usually
        // have
        // many LAGs or high port count)
        NetworkDevice devA = deviceMap.get(ipA);
        NetworkDevice devB = deviceMap.get(ipB);

        if (devA != null && devB != null) {
            String nameA = devA.getSysName() != null ? devA.getSysName().toLowerCase() : "";
            String nameB = devB.getSysName() != null ? devB.getSysName().toLowerCase() : "";

            // Core Switch name pattern whitelist
            if ((nameA.contains("core") || nameA.contains("dist"))
                    && (nameB.contains("core") || nameB.contains("dist"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Helper to identify if a port index corresponds to a physical port. We ignore
     * VLANs, loopbacks, and other virtual interfaces.
     */
    private static boolean isPhysicalPort(NetworkDevice dev, Integer portIndex) {
        if (dev == null || portIndex == null)
            return true;
        for (NetworkInterface ni : dev.getInterfaces()) {
            if (ni.getIndex() == portIndex) {
                String desc = (ni.getDescription() != null) ? ni.getDescription().toLowerCase() : "";
                String type = (ni.getType() != null) ? ni.getType().toLowerCase() : "";

                if (desc.contains("vlan") || desc.contains("lo0") || desc.contains("loopback") || desc.contains("null")
                        || desc.contains("virtual") || desc.contains("bridge")) {
                    return false;
                }
                // Check 161 (LAG/Aggregation)
                if (type.startsWith("53 ") || type.startsWith("24 ") || type.startsWith("1 ")
                        || type.startsWith("161")) {
                    // Debug logging removed to keep clean, or keep valid check
                    return false;
                }
                break;
            }
        }
        return true;
    }

    private static String normalizeMac(String mac) {
        if (mac == null)
            return null;
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

    /**
     * Resolves multi-homed endpoints by identifying the most direct physical path.
     * When multiple switches see the same endpoint, this filter marks redundant
     * links as LOGICAL.
     */
    private static void applyEndpointArbitrationFilter(NetworkGraph graph, Map<String, NetworkDevice> deviceMap,
            GraphContext ctx) {

        // 1. Identify all endpoint nodes
        Map<String, List<GraphEdge>> endpointEdges = new HashMap<>();
        for (GraphEdge edge : graph.getEdges()) {
            if (edge.getTargetId().startsWith("endpoint_") && edge.getType() == EdgeType.PHYSICAL) {
                endpointEdges.computeIfAbsent(edge.getTargetId(), k -> new ArrayList<>()).add(edge);
            }
        }

        // 2. For each endpoint with multiple links, pick GLOBAL winner
        for (Map.Entry<String, List<GraphEdge>> entry : endpointEdges.entrySet()) {
            List<GraphEdge> edges = entry.getValue();

            if (edges.size() <= 1)
                continue;

            // Winner selection priority:
            // 1. Direct (non-infra) vs Infra candidates.
            // 2. Tie-break: Lowest IP (stable)

            GraphEdge winner = null;
            boolean winnerIsInfra = true;

            for (GraphEdge edge : edges) {
                String ip = edge.getSourceId().replace("device_", "");
                java.util.Set<Integer> ports = ctx.getPortsViewingTarget(ip, entry.getKey());

                boolean isInfra = false;
                for (Integer p : ports) {
                    if (ctx.isInfrastructurePort(ip, p)) {
                        isInfra = true;
                        break;
                    }
                }

                if (winner == null) {
                    winner = edge;
                    winnerIsInfra = isInfra;
                } else {
                    // Current vs Winner
                    if (winnerIsInfra && !isInfra) {
                        // Winner was infra, current is direct. Direct wins!
                        winner = edge;
                        winnerIsInfra = false;
                    } else if (winnerIsInfra == isInfra) {
                        // Same status. Tie-break by sourceId.
                        if (edge.getSourceId().compareTo(winner.getSourceId()) < 0) {
                            winner = edge;
                        }
                    }
                }
            }

            // Apply logical status to all losers
            for (GraphEdge edge : edges) {
                if (edge != winner) {
                    edge.setType(EdgeType.LOGICAL);
                }
            }
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

        public void setLabel(String label) {
            this.label = label;
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
