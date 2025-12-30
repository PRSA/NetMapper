package prsa.egosoft.netmapper.service;

import prsa.egosoft.netmapper.model.NetworkGraph;
import prsa.egosoft.netmapper.model.NetworkGraph.GraphNode;
import prsa.egosoft.netmapper.model.NetworkGraph.NodeType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to calculate the layout (node positions) of a NetworkGraph.
 */
public class GraphLayoutService
{
    private static final double DEFAULT_WIDTH = 800;
    private static final double DEFAULT_HEIGHT = 600;
    private static final double ENDPOINT_ORBIT_RADIUS = 100;
    private static final double ORPHAN_RADIUS = 50;
    
    public void calculateLayout(NetworkGraph graph)
    {
        calculateLayout(graph, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }
    
    public void calculateLayout(NetworkGraph graph, double width, double height)
    {
        List<GraphNode> devices = new ArrayList<>();
        List<GraphNode> endpoints = new ArrayList<>();
        
        for(GraphNode node : graph.getNodes())
        {
            if(node.getType() == NodeType.DEVICE)
            {
                devices.add(node);
            }
            else
            {
                endpoints.add(node);
            }
        }
        
        if(devices.isEmpty() && endpoints.isEmpty())
        {
            return;
        }
        
        double centerX = width / 2.0;
        double centerY = height / 2.0;
        
        // 1. Layout Devices in a main circle
        // Increase radius if there are many devices
        double baseDeviceRadius = Math.min(width, height) * 0.3;
        double deviceLayoutRadius = baseDeviceRadius + (devices.size() > 5 ? (devices.size() - 5) * 40 : 0);
        
        if(devices.size() == 1)
        {
            devices.get(0).setX(centerX);
            devices.get(0).setY(centerY);
        }
        else
        {
            for(int i = 0; i < devices.size(); i++)
            {
                GraphNode device = devices.get(i);
                double angle = 2 * Math.PI * i / devices.size();
                device.setX(centerX + deviceLayoutRadius * Math.cos(angle));
                device.setY(centerY + deviceLayoutRadius * Math.sin(angle));
            }
        }
        
        // 2. Map endpoints to their parent devices
        Map<String, List<GraphNode>> deviceToEndpoints = new HashMap<>();
        for(GraphNode device : devices)
        {
            deviceToEndpoints.put(device.getId(), new ArrayList<>());
        }
        
        List<GraphNode> orphanEndpoints = new ArrayList<>();
        for(GraphNode endpoint : endpoints)
        {
            boolean connected = false;
            for(NetworkGraph.GraphEdge edge : graph.getEdges())
            {
                if(edge.getTargetId().equals(endpoint.getId()))
                {
                    if(deviceToEndpoints.containsKey(edge.getSourceId()))
                    {
                        deviceToEndpoints.get(edge.getSourceId()).add(endpoint);
                        connected = true;
                        break;
                    }
                }
                else if(edge.getSourceId().equals(endpoint.getId()))
                {
                    if(deviceToEndpoints.containsKey(edge.getTargetId()))
                    {
                        deviceToEndpoints.get(edge.getTargetId()).add(endpoint);
                        connected = true;
                        break;
                    }
                }
            }
            if(!connected)
            {
                orphanEndpoints.add(endpoint);
            }
        }
        
        // 3. Layout endpoints around devices
        for(GraphNode device : devices)
        {
            List<GraphNode> children = deviceToEndpoints.get(device.getId());
            int count = children.size();
            if(count == 0)
            {
                continue;
            }
            
            // Calculate orbit radius based on count to avoid congestion
            // Also consider the angle from the center to spread endpoints "outside" the
            // device ring
            double angleToCenter = Math.atan2(device.getY() - centerY, device.getX() - centerX);
            
            // If only one device, center is (centerX, centerY), we can orbit 360 degrees
            double startAngle;
            double sweepAngle;
            if(devices.size() <= 1)
            {
                startAngle = 0;
                sweepAngle = 2 * Math.PI;
            }
            else
            {
                // Orbit "outwards" from the center in a 180-degree arc
                startAngle = angleToCenter - Math.PI / 2;
                sweepAngle = Math.PI;
            }
            
            double orbitRadius = ENDPOINT_ORBIT_RADIUS;
            if(count > 8)
            {
                orbitRadius += (count - 8) * 5; // Grow radius if many endpoints
            }
            
            for(int i = 0; i < count; i++)
            {
                GraphNode child = children.get(i);
                double angle;
                if(sweepAngle >= 2 * Math.PI)
                {
                    angle = startAngle + (2 * Math.PI * i / count);
                }
                else
                {
                    // Arc-based distribution
                    angle = startAngle + (sweepAngle * i / Math.max(1, count - 1));
                }
                
                child.setX(device.getX() + orbitRadius * Math.cos(angle));
                child.setY(device.getY() + orbitRadius * Math.sin(angle));
            }
        }
        
        // 4. Layout orphan endpoints in the center or a separate ring
        if(!orphanEndpoints.isEmpty())
        {
            double orphanRingRadius = devices.size() > 0 ? ORPHAN_RADIUS : 100;
            for(int i = 0; i < orphanEndpoints.size(); i++)
            {
                GraphNode orphan = orphanEndpoints.get(i);
                double angle = 2 * Math.PI * i / orphanEndpoints.size();
                orphan.setX(centerX + orphanRingRadius * Math.cos(angle));
                orphan.setY(centerY + orphanRingRadius * Math.sin(angle));
            }
        }
    }
}
