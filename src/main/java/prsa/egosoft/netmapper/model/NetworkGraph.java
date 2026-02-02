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
        return calculateBounds(g2, n -> true, e -> true);
    }
    
    /**
     * Calculates the bounding box of the graph considering filters.
     */
    public Rectangle2D calculateBounds(java.awt.Graphics2D g2, java.util.function.Predicate<GraphNode> nodeFilter,
            java.util.function.Predicate<GraphEdge> edgeFilter)
    {
        
        List<GraphNode> visibleNodes = new ArrayList<>();
        for(GraphNode node : nodes)
        {
            if(nodeFilter.test(node))
            {
                visibleNodes.add(node);
            }
        }
        
        if(visibleNodes.isEmpty())
        {
            return new Rectangle2D.Double(0, 0, 100, 100);
        }
        
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        
        // Use a default node radius for bounds calculation
        int radius = 15;
        
        for(GraphNode node : visibleNodes)
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
            if(!edgeFilter.test(edge))
                continue;
            
            GraphNode source = findNode(edge.getSourceId());
            GraphNode target = findNode(edge.getTargetId());
            
            // Edge is only visible if both nodes are visible
            if(source != null && target != null && nodeFilter.test(source) && nodeFilter.test(target))
            {
                if(edge.getLabel() != null && !edge.getLabel().isEmpty())
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
        
        // ---------------------------------------------------------
        // Pass 0: Pre-build Graph Context (Discovery & Mappings)
        // ---------------------------------------------------------
        GraphContext ctx = buildGraphContext(deviceMap);
        
        // ---------------------------------------------------------
        // Pass 1: Create Nodes (Device Nodes)
        // ---------------------------------------------------------
        Map<String, String> ipToDeviceId = new HashMap<>();
        Map<String, String> macToDeviceId = new HashMap<>();
        
        for(Map.Entry<String, NetworkDevice> entry : deviceMap.entrySet())
        {
            NetworkDevice device = entry.getValue();
            if(device.getIpAddress() == null)
                device.setIpAddress(entry.getKey());
            String deviceId = "device_" + device.getIpAddress();
            
            for(NetworkInterface ni : device.getInterfaces())
            {
                if(ni.getMacAddress() != null && !ni.getMacAddress().isEmpty())
                {
                    macToDeviceId.put(normalizeMac(ni.getMacAddress()), deviceId);
                }
            }
            
            // Build detailed label
            StringBuilder label = new StringBuilder();
            if(device.getSysName() != null && !device.getSysName().isEmpty())
            {
                label.append(device.getSysName()).append("\n");
            }
            if(device.getVendor() != null && !device.getVendor().isEmpty())
            {
                label.append(device.getVendor());
                if(device.getModel() != null && !device.getModel().isEmpty())
                    label.append(" ").append(device.getModel());
                label.append("\n");
            }
            label.append(device.getIpAddress());
            
            GraphNode deviceNode = new GraphNode(deviceId, label.toString(), device.getDeviceType(), NodeType.DEVICE);
            deviceNode.setLayer(device.getLayer());
            // Adjust device confidence based on stability
            device.setConfidence(device.getConfidence() * device.getStabilityScore());
            graph.addNode(deviceNode);
            nodeMap.put(deviceId, deviceNode);
            ipToDeviceId.put(device.getIpAddress(), deviceId);
        }
        
        // Pass 1.1: Create Nodes for discovered Gateways (if not already present)
        for(String gwIp : ctx.gateways)
        {
            String deviceId = "device_" + gwIp;
            if(!nodeMap.containsKey(deviceId))
            {
                GraphNode gwNode = new GraphNode(deviceId, "Gateway\n" + gwIp, "Firewall", NodeType.DEVICE);
                graph.addNode(gwNode);
                nodeMap.put(deviceId, gwNode);
                ipToDeviceId.put(gwIp, deviceId);
            }
        }
        
        // Pass 1.1b: Populate macToDeviceId from Context (for unscanned devices like
        // Gateways)
        for(Map.Entry<String, String> entry : ctx.macToIp.entrySet())
        {
            String mac = entry.getKey();
            String ip = entry.getValue();
            macToDeviceId.putIfAbsent(mac, "device_" + ip);
        }
        
        // ---------------------------------------------------------
        // Pass 2: Create Edges (Phase A: LLDP/Direct Discovery)
        // ---------------------------------------------------------
        // We track created links to avoid duplication by FDB later.
        java.util.Set<String> createdLinks = new java.util.HashSet<>();
        
        for(NetworkDevice device : deviceMap.values())
        {
            String sourceId = "device_" + device.getIpAddress();
            Map<Integer, String> neighbors = device.getLldpNeighbors();
            
            if(neighbors == null)
                continue;
            
            for(Map.Entry<Integer, String> entry : neighbors.entrySet())
            {
                Integer portIdx = entry.getKey();
                String neighborInfo = entry.getValue(); // usually sysName
                
                // Try to find target device by matching sysName or generic text scan
                // This is heuristic because neighborInfo is unstructured text in our current
                // IMPL
                // In a real scenario, we would match ChassisID/PortID properly.
                // For now, let's see if we can map neighborInfo to a known Device System Name
                
                String targetDeviceId = null;
                NetworkDevice matchedCandidate = null;
                for(NetworkDevice candidate : deviceMap.values())
                {
                    if(candidate == device)
                        continue;
                    if(candidate.getSysName() != null)
                    {
                        String candidateName = candidate.getSysName();
                        // Precise match to avoid SW-1 matching SW-11
                        if(neighborInfo.equalsIgnoreCase(candidateName) || neighborInfo.startsWith(candidateName + " ")
                                || neighborInfo.startsWith(candidateName + "(")
                                || neighborInfo.startsWith(candidateName + "."))
                        {
                            targetDeviceId = "device_" + candidate.getIpAddress();
                            matchedCandidate = candidate;
                            break;
                        }
                    }
                }
                
                if(targetDeviceId != null)
                {
                    // Create Physical Link immediately
                    NetworkInterface srcIf = findInterfaceByIndex(device, portIdx);
                    String label = srcIf != null ? srcIf.getDescription() : "Port " + portIdx;
                    label += " (LLDP)";
                    
                    // Validate Physical Attributes of Source (Target unknown port index in this
                    // simplifcation)
                    if(isValidPhysicalLink(srcIf, null, matchedCandidate))
                    {
                        GraphEdge edge = new GraphEdge(sourceId, targetDeviceId, label);
                        edge.setType(EdgeType.PHYSICAL);
                        edge.addDiscoverySource("LLDP");
                        edge.setConfidence(1.0); // LLDP is high confidence
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
        for(Map.Entry<String, NetworkDevice> entry : deviceMap.entrySet())
        {
            NetworkDevice device = entry.getValue();
            String sourceDeviceId = "device_" + device.getIpAddress();
            
            for(Map.Entry<Integer, List<DetectedEndpoint>> macEntry : device.getMacAddressTable().entrySet())
            {
                Integer interfaceIndex = macEntry.getKey();
                NetworkInterface netInterface = findInterfaceByIndex(device, interfaceIndex);
                
                if(!isPhysicalPort(device, interfaceIndex))
                {
                    continue;
                }
                
                // Validation 1: Interface MUST be UP
                if(netInterface != null && "down".equalsIgnoreCase(netInterface.getOperStatus()))
                {
                    continue;
                }
                
                for(DetectedEndpoint endpoint : macEntry.getValue())
                {
                    String endpointIp = endpoint.getIpAddress();
                    String endpointMac = (endpoint.getMacAddress() != null) ? normalizeMac(endpoint.getMacAddress())
                            : "";
                    
                    String targetDeviceId = null;
                    if(endpointIp != null && ipToDeviceId.containsKey(endpointIp))
                    {
                        targetDeviceId = ipToDeviceId.get(endpointIp);
                    }
                    else if(endpointIp != null && ctx.gateways.contains(endpointIp))
                    {
                        targetDeviceId = "device_" + endpointIp;
                    }
                    else if(!endpointMac.isEmpty() && macToDeviceId.containsKey(endpointMac))
                    {
                        targetDeviceId = macToDeviceId.get(endpointMac);
                    }
                    
                    if(targetDeviceId != null)
                    {
                        
                        // DEVICE-TO-DEVICE
                        if(!sourceDeviceId.equals(targetDeviceId))
                        {
                            // Skip if LLDP already handled it
                            if(createdLinks.contains(sourceDeviceId + "|" + targetDeviceId))
                                continue;
                                
                            // Validate Physical Attributes
                            // Finds target interface by looking up where the SOURCE's mac is seen on the
                            // TARGET
                            NetworkDevice targetDev = deviceMap.get(targetDeviceId.replace("device_", ""));
                            NetworkInterface targetIf = findInterfaceViewingMac(targetDev, getDeviceMacs(device));
                            
                            // Validation 2: LLDP Consistency
                            // If source interface has LLDP neighbor info, the target MUST be that neighbor
                            if(isValidPhysicalLink(netInterface, targetIf, targetDev))
                            {
                                String edgeLabel = (netInterface != null) ? netInterface.getDescription() : "";
                                GraphEdge fdbEdge = new GraphEdge(sourceDeviceId, targetDeviceId, edgeLabel);
                                
                                // FDB Hardening:
                                // 1. Role-based Source Restriction: Only infrastructure devices can claim
                                // physical links.
                                // 2. Trunk Hardening: Only downgrade to LOGICAL if the port is a TRUE backbone
                                // trunk.
                                // (A true trunk sees multiple infra devices or leads to a different LLDP
                                // neighbor).
                                if(!isInfrastructureDevice(device.getDeviceType()))
                                {
                                    fdbEdge.setType(EdgeType.LOGICAL_DIRECT);
                                }
                                else if(ctx.isInfrastructurePort(device.getIpAddress(), interfaceIndex))
                                {
                                    // Verify it is not just a direct link to the target
                                    java.util.Set<Integer> portsToTarget = ctx
                                            .getPortsViewingTarget(device.getIpAddress(), targetDeviceId);
                                    boolean destOnly = isDestinedToOnlyTarget(device, interfaceIndex, targetDeviceId,
                                            ctx, deviceMap);
                                    if(device.getIpAddress().endsWith(".5") && targetDeviceId.endsWith("128.55"))
                                    {
                                        System.out.println("DEBUG-FDB-H: x.5->x.55 Port:" + interfaceIndex
                                                + " IsInfra:true PortsToTarget:" + portsToTarget.size() + " DestOnly:"
                                                + destOnly);
                                    }
                                    if(portsToTarget.size() > 1 || !destOnly)
                                    {
                                        fdbEdge.setType(EdgeType.LOGICAL_DIRECT);
                                        fdbEdge.setConfidence(0.5); // Shared FDB is lower confidence
                                    }
                                    else
                                    {
                                        fdbEdge.setConfidence(0.8); // Direct FDB is medium-high
                                    }
                                }
                                
                                fdbEdge.addDiscoverySource("FDB");
                                graph.addEdge(fdbEdge);
                                createdLinks.add(sourceDeviceId + "|" + targetDeviceId); // Prevent duplicates in loop
                            }
                        }
                    }
                    else if(macEntry.getValue().size() > 10
                            && !ctx.isInfrastructurePort(device.getIpAddress(), interfaceIndex))
                    {
                        // Phase 4: Unmanaged Switch detection (Threshold increased to 10)
                        String usId = "device_us_" + device.getIpAddress() + "_" + interfaceIndex;
                        if(!nodeMap.containsKey(usId))
                        {
                            GraphNode usNode = new GraphNode(usId, "Unmanaged Switch\n(Inferred)", "Switch",
                                    NodeType.DEVICE);
                            usNode.setLayer("access");
                            graph.addNode(usNode);
                            nodeMap.put(usId, usNode);
                            
                            GraphEdge edge = new GraphEdge(sourceDeviceId, usId,
                                    netInterface != null ? netInterface.getDescription() : "");
                            edge.setType(EdgeType.PHYSICAL);
                            edge.addDiscoverySource("FDB_INFERENCE");
                            edge.setConfidence(0.7);
                            graph.addEdge(edge);
                        }
                        
                        // Map the endpoint to the unmanaged switch instead of the device
                        targetDeviceId = usId;
                    }
                    else
                    {
                        // DEVICE-TO-ENDPOINT
                        String endpointLabel = (endpointIp != null && !endpointIp.isEmpty()) ? endpointIp
                                : endpoint.getMacAddress();
                        String endpointId = "endpoint_" + endpointMac;
                        
                        if(!nodeMap.containsKey(endpointId))
                        {
                            // Vendor Logic
                            if(endpoint.getVendor() != null && !endpoint.getVendor().isEmpty()
                                    && !prsa.egosoft.netmapper.i18n.Messages.getString("vendor.unknown")
                                            .equals(endpoint.getVendor()))
                            {
                                endpointLabel += "\n" + endpoint.getVendor();
                            }
                            GraphNode node = new GraphNode(endpointId, endpointLabel,
                                    prsa.egosoft.netmapper.i18n.Messages.getString("device.type.unknown"),
                                    NodeType.ENDPOINT);
                            graph.addNode(node);
                            nodeMap.put(endpointId, node);
                        }
                        else
                        {
                            // Upgrade label if IP found
                            GraphNode existing = nodeMap.get(endpointId);
                            if(endpointIp != null && existing.getLabel().equals(endpoint.getMacAddress()))
                            {
                                existing.setLabel(endpointLabel);
                            }
                        }
                        
                        String edgeLabel = (netInterface != null) ? netInterface.getDescription() : "";
                        GraphEdge epEdge = new GraphEdge(sourceDeviceId, endpointId, edgeLabel);
                        
                        // Endpoint Physical Restriction:
                        // 1. Source must be infrastructure
                        // 2. Trunk Hardening: Only if it is a TRUE backbone trunk.
                        if(!isInfrastructureDevice(device.getDeviceType()))
                        {
                            epEdge.setType(EdgeType.LOGICAL_DIRECT);
                        }
                        else if(ctx.isInfrastructurePort(device.getIpAddress(), interfaceIndex))
                        {
                            // Verificación quirúrgica: ¿Es un auténtico trunk o solo un host en puerto
                            // virtual?
                            if(!isDestinedToOnlyTarget(device, interfaceIndex, endpointId, ctx, deviceMap))
                            {
                                epEdge.setType(EdgeType.LOGICAL_DIRECT);
                            }
                        }
                        
                        graph.addEdge(epEdge);
                    }
                }
            }
        }
        
        // Fourth pass: Merge bidirectional device-to-device edges
        mergeBidirectionalEdges(graph);
        
        // Fifth pass: Redundancy Filters
        if(simplifiedPhysicalView)
        {
            applyPhysicalRedundancyFilter(graph, deviceMap, ctx);
            applyEndpointArbitrationFilter(graph, deviceMap, ctx);
        }
        
        return graph;
    }
    
    private static NetworkInterface findInterfaceByIndex(NetworkDevice dev, Integer idx)
    {
        if(dev == null || idx == null)
            return null;
        for(NetworkInterface ni : dev.getInterfaces())
        {
            if(ni.getIndex() == idx)
                return ni;
        }
        return null;
    }
    
    private static List<String> getDeviceMacs(NetworkDevice dev)
    {
        List<String> macs = new ArrayList<>();
        for(NetworkInterface ni : dev.getInterfaces())
        {
            if(ni.getMacAddress() != null)
                macs.add(normalizeMac(ni.getMacAddress()));
        }
        return macs;
    }
    
    private static NetworkInterface findInterfaceViewingMac(NetworkDevice dev, List<String> targetMacs)
    {
        if(dev == null || targetMacs == null)
            return null;
        for(Map.Entry<Integer, List<DetectedEndpoint>> entry : dev.getMacAddressTable().entrySet())
        {
            for(DetectedEndpoint ep : entry.getValue())
            {
                if(ep.getMacAddress() != null && targetMacs.contains(normalizeMac(ep.getMacAddress())))
                {
                    return findInterfaceByIndex(dev, entry.getKey());
                }
            }
        }
        return null;
    }
    
    private static boolean isValidPhysicalLink(NetworkInterface src, NetworkInterface target, NetworkDevice targetDev)
    {
        if(src == null)
            return true;
            
        // 0. LLDP Consistency (Pre-filter)
        // If we know exactly who is on the other side of this PHYSICAL port,
        // we reject any other direct device link on this port.
        if(src.getNeighborInfo() != null && !src.getNeighborInfo().isEmpty() && targetDev != null)
        {
            String neighbor = src.getNeighborInfo();
            String targetSysName = targetDev.getSysName();
            if(targetSysName != null)
            {
                // If neighbor info doesn't mention target, it's likely a logical link through
                // a hub/switch
                if(!neighbor.equalsIgnoreCase(targetSysName) && !neighbor.contains(targetSysName + " ")
                        && !neighbor.contains(targetSysName + "(") && !neighbor.contains(targetSysName + "."))
                {
                    return false;
                }
            }
        }
        
        if("blocking".equalsIgnoreCase(src.getStpState()) || "broken".equalsIgnoreCase(src.getStpState()))
        {
            return false;
        }
        if(target != null)
        {
            if(src.getSpeed() != null && target.getSpeed() != null && !src.getSpeed().equals("0")
                    && !target.getSpeed().equals("0"))
            {
                if(!src.getSpeed().equals(target.getSpeed()))
                {
                    src.setPhysicalValid(false);
                    src.setMismatchReason("speed_mismatch");
                    target.setPhysicalValid(false);
                    target.setMismatchReason("speed_mismatch");
                }
            }
            
            // Duplex
            String d1 = src.getDuplexMode();
            String d2 = target.getDuplexMode();
            if(d1 != null && d2 != null && !"Unknown".equalsIgnoreCase(d1) && !"Unknown".equalsIgnoreCase(d2))
            {
                if(!d1.equalsIgnoreCase(d2))
                {
                    src.setPhysicalValid(false);
                    src.setMismatchReason("duplex_mismatch");
                    target.setPhysicalValid(false);
                    target.setMismatchReason("duplex_mismatch");
                }
            }
            
            // MTU
            if(src.getMtu() > 0 && target.getMtu() > 0 && src.getMtu() != target.getMtu())
            {
                src.setPhysicalValid(false);
                src.setMismatchReason("mtu_mismatch");
                target.setPhysicalValid(false);
                target.setMismatchReason("mtu_mismatch");
            }
            
            // 4. STP Target
            if("blocking".equalsIgnoreCase(target.getStpState()))
                return false;
        }
        
        return true;
    }
    
    private static int getEffectiveRank(String ip, NetworkDevice dev, GraphContext ctx)
    {
        if(ctx != null && ctx.gateways.contains(ip))
        {
            return 40;
        }
        if(dev == null)
            return 0;
        return getCoreRank(dev);
    }
    
    private static boolean isEffectiveInfra(String ip, NetworkDevice dev, GraphContext ctx)
    {
        if(ctx != null && ctx.gateways.contains(ip))
        {
            return true;
        }
        if(dev == null)
            return false;
        return isInfrastructureDevice(dev.getDeviceType());
    }
    
    private static class GraphContext
    {
        Map<String, List<String>> deviceToMacs = new HashMap<>();
        Map<String, String> macToIp = new HashMap<>();
        Map<String, Map<String, java.util.Set<Integer>>> deviceToTargetPorts = new HashMap<>();
        Map<String, java.util.Set<Integer>> deviceToInfraPorts = new HashMap<>();
        java.util.Set<String> gateways = new java.util.HashSet<>();
        
        public java.util.Set<Integer> getPortsViewingTarget(String viewerIp, String targetId)
        {
            Map<String, java.util.Set<Integer>> targetPorts = deviceToTargetPorts.get(viewerIp);
            if(targetPorts == null)
            {
                return new java.util.HashSet<>();
            }
            
            if(targetId.startsWith("device_"))
            {
                String targetIp = targetId.replace("device_", "");
                java.util.Set<Integer> ports = new java.util.HashSet<>();
                if(targetPorts.containsKey(targetIp))
                {
                    ports.addAll(targetPorts.get(targetIp));
                }
                List<String> macs = deviceToMacs.get(targetIp);
                if(macs != null)
                {
                    for(String m : macs)
                    {
                        if(targetPorts.containsKey(m))
                        {
                            ports.addAll(targetPorts.get(m));
                        }
                    }
                }
                return ports;
            }
            else if(targetId.startsWith("endpoint_"))
            {
                String targetMac = targetId.replace("endpoint_", "");
                String normMac = normalizeMac(targetMac);
                return targetPorts.getOrDefault(normMac, new java.util.HashSet<>());
            }
            return new java.util.HashSet<>();
        }
        
        public Integer getSpecificPort(String viewerIp, String targetId)
        {
            java.util.Set<Integer> ports = getPortsViewingTarget(viewerIp, targetId);
            if(ports != null && !ports.isEmpty())
            {
                return ports.iterator().next();
            }
            return null;
        }
        
        public boolean isInfrastructurePort(String viewerIp, int portIdx)
        {
            java.util.Set<Integer> infraPorts = deviceToInfraPorts.get(viewerIp);
            return infraPorts != null && infraPorts.contains(portIdx);
        }
    }
    
    private static int getCoreRank(NetworkDevice dev)
    {
        if(dev == null)
            return 0;
        String name = dev.getSysName() != null ? dev.getSysName().toLowerCase() : "";
        String type = dev.getDeviceType() != null ? dev.getDeviceType().toLowerCase() : "";
        String ip = dev.getIpAddress() != null ? dev.getIpAddress() : "";
        String layer = dev.getLayer();
        
        // High priority: Network Edge / Security Gateways
        if("edge".equalsIgnoreCase(layer) || type.contains("firewall")
                || (type.contains("gateway") && !ip.endsWith(".1")) || name.contains("fw"))
        {
            return 40;
        }
        
        // Core Switches
        if("core".equalsIgnoreCase(layer) || dev.getInterfaces().size() > 200)
        {
            return 35;
        }
        
        // Distribution Switches
        if("distribution".equalsIgnoreCase(layer))
        {
            return 30;
        }
        
        // Access Switches / Routers
        if("access".equalsIgnoreCase(layer) || name.contains("cocj") || type.contains("switch")
                || type.contains("router"))
        {
            if(dev.getInterfaces().size() > 48)
                return 30;
            return 25;
        }
        
        // Default Leaf devices: Hosts, Servers, MSA
        return 10;
    }
    
    private static boolean isInfrastructureDevice(String type)
    {
        if(type == null)
            return false;
        String t = type.toLowerCase();
        return t.contains("switch") || t.contains("router") || t.contains("firewall") || t.contains("gateway")
                || t.contains("bridge") || t.contains("hub") || t.contains("storage") || t.contains("msa")
                || t.contains("san") || t.contains("server") || t.contains("esxi");
    }
    
    public static GraphContext buildGraphContext(Map<String, NetworkDevice> deviceMap)
    {
        GraphContext ctx = new GraphContext();
        
        // Pass 0: Identity Restoration
        for(Map.Entry<String, NetworkDevice> entry : deviceMap.entrySet())
        {
            if(entry.getValue().getIpAddress() == null)
            {
                entry.getValue().setIpAddress(entry.getKey());
            }
        }
        
        // Pass 1: Canonical mappings from device interfaces
        for(NetworkDevice dev : deviceMap.values())
        {
            List<String> macs = new ArrayList<>();
            for(NetworkInterface ni : dev.getInterfaces())
            {
                if(ni.getMacAddress() != null && !ni.getMacAddress().isEmpty())
                {
                    String mac = normalizeMac(ni.getMacAddress());
                    macs.add(mac);
                    ctx.macToIp.put(mac, dev.getIpAddress());
                }
            }
            ctx.deviceToMacs.put(dev.getIpAddress(), macs);
        }
        
        // Pass 1.5: Discover gateways from routing tables
        Map<String, Integer> gatewayHits = new HashMap<>();
        for(NetworkDevice dev : deviceMap.values())
        {
            for(String gw : dev.getRoutingTable().values())
            {
                if(gw != null && !gw.equals("0.0.0.0") && !gw.equals("127.0.0.1"))
                {
                    gatewayHits.put(gw, gatewayHits.getOrDefault(gw, 0) + 1);
                }
            }
        }
        for(Map.Entry<String, Integer> entry : gatewayHits.entrySet())
        {
            if(entry.getValue() >= 1)
            {
                ctx.gateways.add(entry.getKey());
            }
        }
        
        // Pass 2: Harvest shadow mappings from ALL bridge tables and populate target
        // ports
        for(NetworkDevice dev : deviceMap.values())
        {
            Map<String, java.util.Set<Integer>> targetPorts = new HashMap<>();
            for(Map.Entry<Integer, List<DetectedEndpoint>> entry : dev.getMacAddressTable().entrySet())
            {
                Integer portIdx = entry.getKey();
                if(!isPhysicalPort(dev, portIdx))
                    continue;
                
                for(DetectedEndpoint ep : entry.getValue())
                {
                    String mac = normalizeMac(ep.getMacAddress());
                    if(ep.getIpAddress() != null && ep.getMacAddress() != null)
                    {
                        String ip = ep.getIpAddress();
                        if(deviceMap.containsKey(ip))
                        {
                            ctx.macToIp.putIfAbsent(mac, ip);
                            ctx.deviceToMacs.get(ip).add(mac);
                            targetPorts.computeIfAbsent(ip, k -> new java.util.HashSet<>()).add(portIdx);
                        }
                        else if(ctx.gateways.contains(ip))
                        {
                            // Fix: Allow gateways to be identified by MAC even if not scanned
                            ctx.macToIp.putIfAbsent(mac, ip);
                            ctx.deviceToMacs.computeIfAbsent(ip, k -> new ArrayList<>()).add(mac);
                            targetPorts.computeIfAbsent(ip, k -> new java.util.HashSet<>()).add(portIdx);
                        }
                    }
                    targetPorts.computeIfAbsent(mac, k -> new java.util.HashSet<>()).add(portIdx);
                }
            }
            ctx.deviceToTargetPorts.put(dev.getIpAddress(), targetPorts);
        }
        
        // Pass 2.1: Populate target ports from LLDP neighbors
        for(NetworkDevice device : deviceMap.values())
        {
            Map<Integer, String> neighbors = device.getLldpNeighbors();
            if(neighbors == null)
                continue;
            
            for(Map.Entry<Integer, String> entry : neighbors.entrySet())
            {
                Integer portIdx = entry.getKey();
                String neighborInfo = entry.getValue();
                
                for(NetworkDevice candidate : deviceMap.values())
                {
                    if(candidate == device)
                        continue;
                    if(candidate.getSysName() != null)
                    {
                        String candName = candidate.getSysName();
                        if(neighborInfo.equalsIgnoreCase(candName) || neighborInfo.startsWith(candName + " ")
                                || neighborInfo.startsWith(candName + "(") || neighborInfo.startsWith(candName + "."))
                        {
                            ctx.deviceToTargetPorts.computeIfAbsent(device.getIpAddress(), k -> new HashMap<>())
                                    .computeIfAbsent(candidate.getIpAddress(), k -> new java.util.HashSet<>())
                                    .add(portIdx);
                            break;
                        }
                    }
                }
            }
        }
        
        // Pass 3: Identify Infrastructure Ports (ports that see other network
        // infrastructure)
        for(NetworkDevice dev : deviceMap.values())
        {
            java.util.Set<Integer> infraPorts = new java.util.HashSet<>();
            for(Map.Entry<String, NetworkDevice> otherEntry : deviceMap.entrySet())
            {
                String otherIp = otherEntry.getKey();
                NetworkDevice otherDev = otherEntry.getValue();
                
                if(otherIp.equals(dev.getIpAddress()))
                    continue;
                
                // Any infrastructure device contributes to port classification
                if(isInfrastructureDevice(otherDev.getDeviceType()))
                {
                    infraPorts.addAll(ctx.getPortsViewingTarget(dev.getIpAddress(), "device_" + otherIp));
                }
            }
            // NOTE: We no longer treat "external gateways" as infrastructure for port
            // classification.
            // A port seeing just a gateway is often an access port or a dedicated WAN port,
            // not a backbone trunk that should prune other endpoints.
            ctx.deviceToInfraPorts.put(dev.getIpAddress(), infraPorts);
        }
        
        return ctx;
    }
    
    /**
     * Removes redundant links between devices where a multi-hop path is known. Uses
     * MAC table visibility to infer the most direct physical path.
     */
    private static void applyPhysicalRedundancyFilter(NetworkGraph graph, Map<String, NetworkDevice> deviceMap,
            GraphContext ctx)
    {
        // Pass 1: Identify direct vs transitive paths (Generic L2 Pruning)
        List<GraphEdge> deviceEdges = new ArrayList<>();
        java.util.Set<String> existingPhysicalLinks = new java.util.HashSet<>();
        
        for(GraphEdge edge : graph.getEdges())
        {
            if(edge.getType() != EdgeType.LOGICAL_DIRECT && !edge.getTargetId().startsWith("endpoint_"))
            {
                deviceEdges.add(edge);
                String src = edge.getSourceId().replace("device_", "");
                String tgt = edge.getTargetId().replace("device_", "");
                existingPhysicalLinks.add(src + "|" + tgt);
                if((src.endsWith(".5") && tgt.endsWith(".55")) || (src.endsWith(".55") && tgt.endsWith(".5")))
                {
                    System.out
                            .println("DEBUG-FILTER-INPUT: x.5->x.55 Link in physicalLinks! EdgeType:" + edge.getType());
                }
                // Also add reverse for finding "any link"
                // existingPhysicalLinks.add(tgt + "|" + src);
            }
        }
        // Sort to ensure stable yields
        deviceEdges
                .sort((e1, e2) -> (e1.getSourceId() + e1.getTargetId()).compareTo(e2.getSourceId() + e2.getTargetId()));
        
        for(int i = 0; i < deviceEdges.size(); i++)
        {
            GraphEdge edge = deviceEdges.get(i);
            if(edge.getType() == EdgeType.LOGICAL_DIRECT)
                continue;
            String ipA = edge.getSourceId().replace("device_", "");
            String ipB = edge.getTargetId().replace("device_", "");
            
            NetworkDevice devA = deviceMap.get(ipA);
            NetworkDevice devB = deviceMap.get(ipB);
            
            // CHANGED: Allow processing if one device is missing but is a known
            // Gateway/Infra
            boolean infraA = isEffectiveInfra(ipA, devA, ctx);
            boolean infraB = isEffectiveInfra(ipB, devB, ctx);
            
            if((devA == null && !infraA) || (devB == null && !infraB))
                continue;
            
            // Protection: Never prune an LLDP-verified link using FDB data.
            boolean isLldp = edge.getLabel() != null && edge.getLabel().contains("LLDP");
            
            int rankA = getEffectiveRank(ipA, devA, ctx);
            int rankB = getEffectiveRank(ipB, devB, ctx);
            int distA = Math.abs(rankA - rankB);
            
            // Find better parent C globally
            for(NetworkDevice devC : deviceMap.values())
            {
                if(!isInfrastructureDevice(devC.getDeviceType()))
                    continue;
                String ipC = devC.getIpAddress();
                if(ipC.equals(ipA) || ipC.equals(ipB))
                    continue;
                    
                // CRITICAL CHECK: C can only prune A if C ITSELF has a physical link to B.
                // Otherwise, we allow a "worse" parent A to keep the link because C is not a
                // valid alternative.
                boolean cHasLink = existingPhysicalLinks.contains(ipC + "|" + ipB);
                
                // Allow "Gateway" exception? If ipB is Gateway, x.4 might be parent without
                // explicit edge?
                // No, we want explicit edge. If x.4 connects to x.1, it should have an edge.
                // If it doesn't, allow x.55 (which has edge) to win.
                if(!cHasLink)
                    continue;
                
                java.util.Set<Integer> portsToB = ctx.getPortsViewingTarget(ipC, "device_" + ipB);
                if(portsToB.isEmpty())
                    continue;
                
                int rankC = getCoreRank(devC);
                int distC = Math.abs(rankC - rankB);
                
                // Tie-breaker: MAC count is the best signal for 'local' vs 'remote'
                // A switch that sees a host with 1 MAC is much better than one seeing it with
                // 100.
                int minMacA = 1000;
                java.util.Set<Integer> pA = ctx.getPortsViewingTarget(ipA, "device_" + ipB);
                if(devA != null)
                {
                    for(Integer p : pA)
                    {
                        List<DetectedEndpoint> es = devA.getMacAddressTable().get(p);
                        if(es != null)
                            minMacA = Math.min(minMacA, es.size());
                    }
                }
                int minMacC = 1000;
                for(Integer p : portsToB)
                {
                    List<DetectedEndpoint> es = devC.getMacAddressTable().get(p);
                    if(es != null)
                        minMacC = Math.min(minMacC, es.size());
                }
                
                // HIERARCHY PROTECTION:
                // If C is hierarchically FURTHER (Worse Rank Distance) than A,
                // C should NEVER prune A unless C has a DIRECT connection (Mac=1).
                // This prevents Core (Dist 10) from pruning Distribution (Dist 0) just because
                // Core sees fewer MACs on trunk.
                if(distC > distA && minMacC > 1)
                {
                    continue;
                }
                
                // Access Port Preference Logic
                String targetDevId = "device_" + ipB;
                Integer portA = ctx.getSpecificPort(ipA, targetDevId); // ipA seeing B
                boolean isInfraPortA = (portA != null) && ctx.isInfrastructurePort(ipA, portA);
                Integer portC = ctx.getSpecificPort(ipC, targetDevId); // ipC seeing B
                boolean isInfraPortC = (portC != null) && ctx.isInfrastructurePort(ipC, portC);
                
                boolean betterRank = (distC < distA);
                
                if(!isInfraPortC && isInfraPortA)
                {
                    betterRank = true; // C is Access (Direct), A is Trunk. C wins.
                }
                else if(isInfraPortC && !isInfraPortA)
                {
                    continue; // C is Trunk, A is Access. C DISQUALIFIED. A wins.
                }
                // Fix: Rank should not override physical reality (MAC count)
                // If A sees 1 MAC, it is likely the direct parent.
                // If C sees many MACs, it is an aggregation point.
                if(minMacA == 1 && minMacC > 1)
                {
                    betterRank = false; // Disable rank advantage if A is direct and C is not
                }
                
                boolean betterLocal = (minMacC < minMacA); // prioritized over rank if mismatch
                
                // CRITICAL FIX: For Infrastructure Targets (Switches), STRICTLY respect
                // Topology Rank.
                // A Core switch (Higher Rank) is a better parent for a Dist switch than another
                // Dist switch.
                if(devB != null && isInfrastructureDevice(devB.getDeviceType()))
                {
                    // If A and B are same rank (e.g. Access 25), and C is higher (Dist 30),
                    // then C is the better parent for the trunk link.
                    if(rankC > rankA && isInfraPortA)
                    {
                        betterRank = true;
                    }
                    else if(distC > distA)
                    {
                        continue; // C is further away in rank-distance. Disqualified.
                    }
                    betterLocal = false;
                }
                
                // If C is direct (1) and A is not, C wins automatically (Only if NOT infra or
                // if C really is better)
                if((devB == null || !isInfrastructureDevice(devB.getDeviceType())) && minMacC == 1 && minMacA > 1)
                {
                    betterRank = true;
                    betterLocal = true;
                }
                
                boolean tieBreak = false;
                if(distC == distA && minMacC == minMacA)
                {
                    long ipValC = ipToLong(ipC);
                    long ipValA = ipToLong(ipA);
                    tieBreak = ipValC < ipValA;
                }
                
                if(betterRank || betterLocal || tieBreak)
                {
                    // Logic: C is a better parent for B than A is.
                    // But if A-B is LLDP and C is merely FDB, A wins.
                    boolean otherIsLldp = false;
                    if(devB != null && devB.getSysName() != null)
                    {
                        String targetName = devB.getSysName();
                        otherIsLldp = devC.getLldpNeighbors().values().stream().anyMatch(n -> n.contains(targetName));
                    }
                    
                    if(isLldp && !otherIsLldp)
                        continue;
                    
                    // Logic: C is better. A is redundant.
                    edge.setVisible(false);
                    edge.setRole("redundant");
                    edge.setConfidence(0.3);
                    break;
                }
            }
        }
        // Apply Strict Triangle Filter with pre-harvested data
        applyStrictTriangleFilter(graph, deviceMap, ctx);
        applyEndpointRedundancyFilter(graph, deviceMap, ctx);
    }
    
    private static void applyStrictTriangleFilter(NetworkGraph graph, Map<String, NetworkDevice> deviceMap,
            GraphContext ctx)
    {
        for(GraphEdge edge : graph.getEdges())
        {
            if(edge.getType() == EdgeType.LOGICAL_DIRECT)
                continue;
            if(edge.getTargetId().startsWith("endpoint_"))
                continue;
            
            String ipA = edge.getSourceId().replace("device_", "");
            String ipB = edge.getTargetId().replace("device_", "");
            
            NetworkDevice devA = deviceMap.get(ipA);
            NetworkDevice devB = deviceMap.get(ipB);
            
            if(devA == null || devB == null)
                continue;
            
            for(NetworkDevice core : deviceMap.values())
            {
                String coreIp = core.getIpAddress();
                if(coreIp.equals(ipA) || coreIp.equals(ipB))
                    continue;
                
                java.util.Set<Integer> portsToA = ctx.getPortsViewingTarget(coreIp, "device_" + ipA);
                java.util.Set<Integer> portsToB = ctx.getPortsViewingTarget(coreIp, "device_" + ipB);
                
                if(!portsToA.isEmpty() && !portsToB.isEmpty())
                {
                    java.util.Set<Integer> commonPorts = new java.util.HashSet<>(portsToA);
                    commonPorts.retainAll(portsToB);
                    
                    if(!commonPorts.isEmpty())
                    {
                        // Core sees A and B on the same port.
                        // 1. Check if A sees B through Core.
                        java.util.Set<Integer> aPortsToCore = ctx.getPortsViewingTarget(ipA, "device_" + coreIp);
                        java.util.Set<Integer> aPortsToB = ctx.getPortsViewingTarget(ipA, "device_" + ipB);
                        
                        if(!aPortsToCore.isEmpty() && !aPortsToB.isEmpty())
                        {
                            java.util.Set<Integer> aCommon = new java.util.HashSet<>(aPortsToCore);
                            aCommon.retainAll(aPortsToB);
                            if(!aCommon.isEmpty())
                            {
                                int rankA = getCoreRank(devA);
                                int rankB = getCoreRank(devB);
                                int rankCore = getCoreRank(core);
                                
                                int distA = Math.abs(rankA - rankB);
                                int distCore = Math.abs(rankCore - rankB);
                                
                                boolean cIsBetter = (distCore < distA);
                                boolean isPeerTie = (distCore == distA && coreIp.compareTo(ipA) < 0);
                                
                                if(cIsBetter || isPeerTie)
                                {
                                    edge.setVisible(false);
                                    edge.setRole("redundant");
                                    edge.setConfidence(0.3);
                                    break;
                                }
                            }
                        }
                        
                        // 2. Try other direction: B sees A through Core
                        java.util.Set<Integer> bPortsToCore = ctx.getPortsViewingTarget(ipB, "device_" + coreIp);
                        java.util.Set<Integer> bPortsToA = ctx.getPortsViewingTarget(ipB, "device_" + ipA);
                        
                        if(!bPortsToCore.isEmpty() && !bPortsToA.isEmpty())
                        {
                            java.util.Set<Integer> bCommon = new java.util.HashSet<>(bPortsToCore);
                            bCommon.retainAll(bPortsToA);
                            if(!bCommon.isEmpty())
                            {
                                int rankA = getCoreRank(devA);
                                int rankB = getCoreRank(devB);
                                int rankCore = getCoreRank(core);
                                
                                // Core prunes A-B (B seen through Core)
                                int distB = Math.abs(rankB - rankA);
                                int distCore = Math.abs(rankCore - rankA);
                                
                                boolean cIsBetter = (distCore < distB);
                                boolean isPeerTie = (distCore == distB && coreIp.compareTo(ipB) < 0);
                                
                                if(cIsBetter || isPeerTie)
                                {
                                    edge.setVisible(false);
                                    edge.setRole("redundant");
                                    edge.setConfidence(0.3);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private static boolean isDestinedToOnlyTarget(NetworkDevice source, int portIdx, String targetDeviceId,
            GraphContext ctx, Map<String, NetworkDevice> deviceMap)
    {
        // 1. Check LLDP neighbors
        Map<Integer, String> neighbors = source.getLldpNeighbors();
        if(neighbors != null && neighbors.containsKey(portIdx))
        {
            // If it has LLDP, it is a point-to-point backbone link.
            // We trust Pass 2 handled it. Pass 3 (FDB) should be logical here.
            return false;
        }
        
        // 2. Check FDB breadth
        // A port is a trunk IF it sees an infrastructure device that is NOT the target.
        List<DetectedEndpoint> entries = source.getMacAddressTable().get(portIdx);
        if(entries != null)
        {
            for(DetectedEndpoint de : entries)
            {
                String ip = de.getIpAddress();
                if(ip != null)
                {
                    NetworkDevice otherDev = deviceMap.get(ip);
                    if(otherDev != null)
                    {
                        String oType = otherDev.getDeviceType() != null ? otherDev.getDeviceType().toLowerCase() : "";
                        // A port lead to a backbone trunk if it sees a Switch or Router.
                        // Firewalls and Gateways are often leaf devices or have shared access ports.
                        if(oType.contains("switch") || oType.contains("router"))
                        {
                            // If this infra device IS the target (or part of it), ignore it for trunk
                            // detection
                            if(targetDeviceId.contains(ip))
                            {
                                continue;
                            }
                            // Sees a DIFFERENT backbone device. This is a Trunk.
                            return false;
                        }
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * Helper to identify if a port index corresponds to a physical port. We ignore
     * VLANs, loopbacks, and other virtual interfaces.
     */
    private static boolean isPhysicalPort(NetworkDevice dev, Integer portIndex)
    {
        if(dev == null || portIndex == null)
            return true;
        for(NetworkInterface ni : dev.getInterfaces())
        {
            if(ni.getIndex() == portIndex)
            {
                String desc = (ni.getDescription() != null) ? ni.getDescription().toLowerCase() : "";
                String type = (ni.getType() != null) ? ni.getType().toLowerCase() : "";
                
                // High-priority exclusion for purely logical/virtual constructs
                if(desc.contains("lo0") || desc.contains("loopback") || desc.contains("null") || desc.contains("tun")
                        || desc.contains("tap") || desc.contains("cpu") || desc.contains("stack"))
                {
                    return false;
                }
                
                // Type-based exclusion
                // 24: loopback, 1: other/internal
                if(type.startsWith("24") || type.startsWith("1 "))
                {
                    return false;
                }
                
                // Explicitly exclude Virtual/VLAN interfaces (53, 135) to prevent false
                // physical links
                if(type.startsWith("53") || type.startsWith("135"))
                {
                    return false;
                }
                
                // Explicitly allow LAGs/Trunks (161, 54), Ethernet (6), and Virtual/VLAN (53,
                // 135) - NO, 53/135 removed
                if(type.startsWith("161") || type.startsWith("54") || type.startsWith("6") || type.startsWith("117"))
                {
                    return true;
                }
                
                // Default: Filter out wireless
                if(type.contains("wireless") || type.contains("802.11"))
                {
                    return false;
                }
                break;
            }
        }
        return true;
    }
    
    private static String normalizeMac(String mac)
    {
        if(mac == null)
            return null;
        return mac.replace("-", ":").replace(".", ":").toLowerCase().trim();
    }
    
    /**
     * Merges bidirectional edges into single edges with combined labels.
     */
    private static void mergeBidirectionalEdges(NetworkGraph graph)
    {
        List<GraphEdge> allEdges = new java.util.ArrayList<>(graph.getEdges());
        graph.getEdges().clear();
        
        // Group edges by their node pair (order independent)
        Map<String, List<GraphEdge>> groupedEdges = new java.util.HashMap<>();
        for(GraphEdge edge : allEdges)
        {
            String u = edge.getSourceId();
            String v = edge.getTargetId();
            String key = (u.compareTo(v) < 0) ? (u + "|" + v) : (v + "|" + u);
            groupedEdges.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(edge);
        }
        
        for(Map.Entry<String, List<GraphEdge>> entry : groupedEdges.entrySet())
        {
            List<GraphEdge> group = entry.getValue();
            if(group.isEmpty())
                continue;
            
            GraphEdge first = group.get(0);
            String sourceId = first.getSourceId();
            String targetId = first.getTargetId();
            
            // Collect unique labels and determine if any are physical
            java.util.Set<String> labels = new java.util.TreeSet<>();
            EdgeType finalType = EdgeType.LOGICAL_DIRECT;
            
            for(GraphEdge edge : group)
            {
                if(edge.getLabel() != null && !edge.getLabel().isEmpty())
                {
                    labels.add(edge.getLabel());
                }
                if(edge.getType() == EdgeType.PHYSICAL)
                {
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
            GraphContext ctx)
    {
        
        // 1. Identify all endpoint nodes
        Map<String, List<GraphEdge>> endpointEdges = new HashMap<>();
        for(GraphEdge edge : graph.getEdges())
        {
            if(edge.getTargetId().startsWith("endpoint_") && edge.getType() == EdgeType.PHYSICAL)
            {
                endpointEdges.computeIfAbsent(edge.getTargetId(), k -> new ArrayList<>()).add(edge);
            }
        }
        
        // 2. For each endpoint with multiple links, pick GLOBAL winner
        for(Map.Entry<String, List<GraphEdge>> entry : endpointEdges.entrySet())
        {
            List<GraphEdge> edges = entry.getValue();
            
            if(edges.size() <= 1)
                continue;
                
            // Winner selection priority:
            // 1. Direct (non-infra) vs Infra candidates.
            // 2. Tie-break: Lowest IP (stable)
            
            GraphEdge winner = null;
            boolean winnerIsInfra = true;
            
            for(GraphEdge edge : edges)
            {
                String ip = edge.getSourceId().replace("device_", "");
                java.util.Set<Integer> ports = ctx.getPortsViewingTarget(ip, entry.getKey());
                
                boolean isInfra = false;
                for(Integer p : ports)
                {
                    if(ctx.isInfrastructurePort(ip, p))
                    {
                        isInfra = true;
                        break;
                    }
                }
                
                if(winner == null)
                {
                    winner = edge;
                    winnerIsInfra = isInfra;
                }
                else
                {
                    // Current vs Winner
                    if(winnerIsInfra && !isInfra)
                    {
                        // Winner was infra, current is direct. Direct wins!
                        winner = edge;
                        winnerIsInfra = false;
                    }
                    else if(winnerIsInfra == isInfra)
                    {
                        // Same status. Tie-break by sourceId.
                        if(edge.getSourceId().compareTo(winner.getSourceId()) < 0)
                        {
                            winner = edge;
                        }
                    }
                }
            }
            
            // Apply logical status to all losers
            for(GraphEdge edge : edges)
            {
                if(edge != winner)
                {
                    edge.setVisible(false);
                    edge.setRole("redundant");
                }
            }
        }
    }
    
    /**
     * Filters redundant endpoint links. If an endpoint is seen by multiple devices,
     * this filter attempts to identify the most direct path and mark others as
     * LOGICAL.
     */
    private static void applyEndpointRedundancyFilter(NetworkGraph graph, Map<String, NetworkDevice> deviceMap,
            GraphContext ctx)
    {
        // Group edges by endpoint
        Map<String, List<GraphEdge>> endpointToEdges = new HashMap<>();
        for(GraphEdge edge : graph.getEdges())
        {
            if(edge.getTargetId().startsWith("endpoint_") && edge.getType() == EdgeType.PHYSICAL)
            {
                endpointToEdges.computeIfAbsent(edge.getTargetId(), k -> new ArrayList<>()).add(edge);
            }
        }
        
        for(Map.Entry<String, List<GraphEdge>> entry : endpointToEdges.entrySet())
        {
            List<GraphEdge> edges = entry.getValue();
            if(edges.size() <= 1)
                continue; // No redundancy to resolve
                
            String endpointIp = entry.getKey().replace("endpoint_", "");
            
            // For each endpoint, determine the "best" link
            // Criteria:
            // 1. Direct link (not through an infrastructure port)
            // 2. If all are direct or all are indirect, prefer the device with the lowest
            // IP (for stability)
            
            GraphEdge bestEdge = null;
            boolean bestEdgeIsDirect = false;
            
            for(GraphEdge edge : edges)
            {
                String deviceIp = edge.getSourceId().replace("device_", "");
                NetworkDevice dev = deviceMap.get(deviceIp);
                if(dev == null)
                    continue;
                
                // Check if this device sees the endpoint via a direct port (not an infra port)
                boolean directPort = false;
                boolean infraPort = false;
                java.util.Set<Integer> ports = ctx.getPortsViewingTarget(deviceIp, entry.getKey());
                for(Integer portIdx : ports)
                {
                    if(!ctx.isInfrastructurePort(deviceIp, portIdx))
                    {
                        directPort = true;
                    }
                    else
                    {
                        infraPort = true;
                    }
                }
                
                if(endpointIp != null && (endpointIp.endsWith(".135") || endpointIp.endsWith(".254")))
                {
                    System.out.println("ARBITRATING-EP: " + endpointIp + " seen by " + dev.getIpAddress() + " port="
                            + ports + " infra=" + infraPort + " direct=" + directPort + " isInfraDev="
                            + isInfrastructureDevice(dev.getDeviceType()));
                }
                
                // FILTER: If this port sees OTHER infrastructure, it's likely a trunk
                // We only show endpoints on ACCESS ports (leaf ports)
                if(infraPort && !directPort)
                {
                    if(endpointIp != null && (endpointIp.endsWith(".135") || endpointIp.endsWith(".254")))
                    {
                        System.out.println(
                                "  -> PRUNED-EP: " + endpointIp + " by " + dev.getIpAddress() + " (Trunk View)");
                    }
                    edge.setType(EdgeType.LOGICAL_DIRECT);
                    continue; // This edge is pruned, don't consider it for "best"
                }
                
                if(bestEdge == null)
                {
                    bestEdge = edge;
                    bestEdgeIsDirect = directPort;
                }
                else
                {
                    if(directPort && !bestEdgeIsDirect)
                    {
                        // Current edge is direct, bestEdge was indirect. Current wins.
                        bestEdge = edge;
                        bestEdgeIsDirect = true;
                    }
                    else if(directPort == bestEdgeIsDirect)
                    {
                        // Both are direct or both are indirect. Tie-break by IP.
                        if(deviceIp.compareTo(bestEdge.getSourceId().replace("device_", "")) < 0)
                        {
                            bestEdge = edge;
                        }
                    }
                }
            }
            
            // Mark all non-best edges as LOGICAL
            for(GraphEdge edge : edges)
            {
                if(edge != bestEdge)
                {
                    if(endpointIp != null && (endpointIp.endsWith(".135") || endpointIp.endsWith(".254")))
                    {
                        System.out.println("  -> PRUNED-EP: " + endpointIp + " by "
                                + edge.getSourceId().replace("device_", "") + " (Redundant View)");
                    }
                    edge.setType(EdgeType.LOGICAL_DIRECT);
                }
            }
        }
    }
    
    public static class GraphNode
    {
        private String id;
        private String label;
        private String typeLabel;
        private NodeType type;
        private String layer; // core, distribution, access, edge, endpoint
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
        
        public void setLabel(String label)
        {
            this.label = label;
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
        
        public String getLayer()
        {
            return layer;
        }
        
        public void setLayer(String layer)
        {
            this.layer = layer;
        }
    }
    
    public static class GraphEdge
    {
        private String sourceId;
        private String targetId;
        private String label;
        private EdgeType type = EdgeType.PHYSICAL;
        
        // Methodology Attributes
        private java.util.List<String> discoverySources = new java.util.ArrayList<>();
        private double confidence = 1.0;
        private boolean visible = true;
        private String role = "functional"; // functional, redundant, blocked, etc.
        
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
        
        public EdgeType getType()
        {
            return type;
        }
        
        public void setType(EdgeType type)
        {
            this.type = type;
        }
        
        public java.util.List<String> getDiscoverySources()
        {
            return discoverySources;
        }
        
        public void addDiscoverySource(String source)
        {
            if(!this.discoverySources.contains(source))
            {
                this.discoverySources.add(source);
            }
        }
        
        public double getConfidence()
        {
            return confidence;
        }
        
        public void setConfidence(double confidence)
        {
            this.confidence = confidence;
        }
        
        public boolean isVisible()
        {
            return visible;
        }
        
        public void setVisible(boolean visible)
        {
            this.visible = visible;
        }
        
        public String getRole()
        {
            return role;
        }
        
        public void setRole(String role)
        {
            this.role = role;
        }
    }
    
    public enum NodeType
    {
        DEVICE, ENDPOINT
    }
    
    public enum EdgeType
    {
        PHYSICAL, LOGICAL_DIRECT
    }
    
    private static long ipToLong(String ipAddress)
    {
        long result = 0;
        String[] ipAddressInArray = ipAddress.split("\\.");
        for(int i = 3; i >= 0; i--)
        {
            long ip = Long.parseLong(ipAddressInArray[3 - i]);
            result |= ip << (i * 8);
        }
        return result;
    }
}
