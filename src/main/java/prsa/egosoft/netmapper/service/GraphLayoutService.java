package prsa.egosoft.netmapper.service;

import prsa.egosoft.netmapper.model.NetworkGraph;
import prsa.egosoft.netmapper.model.NetworkGraph.GraphEdge;
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
    private static final double DEVICE_RADIUS_FACTOR = 0.7;
    
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
        
        double deviceLayoutRadius = Math.min(centerX, centerY) * DEVICE_RADIUS_FACTOR;
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
        
        Map<String, List<GraphNode>> deviceToEndpoints = new HashMap<>();
        for(GraphNode device : devices)
        {
            deviceToEndpoints.put(device.getId(), new ArrayList<>());
        }
        
        List<GraphNode> orphanEndpoints = new ArrayList<>();
        for(GraphNode endpoint : endpoints)
        {
            boolean connected = false;
            for(GraphEdge edge : graph.getEdges())
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
        
        for(GraphNode device : devices)
        {
            List<GraphNode> children = deviceToEndpoints.get(device.getId());
            int count = children.size();
            if(count == 0)
            {
                continue;
            }
            
            for(int i = 0; i < count; i++)
            {
                GraphNode child = children.get(i);
                double angle = 2 * Math.PI * i / count;
                child.setX(device.getX() + ENDPOINT_ORBIT_RADIUS * Math.cos(angle));
                child.setY(device.getY() + ENDPOINT_ORBIT_RADIUS * Math.sin(angle));
            }
        }
        
        if(!orphanEndpoints.isEmpty())
        {
            for(int i = 0; i < orphanEndpoints.size(); i++)
            {
                GraphNode orphan = orphanEndpoints.get(i);
                double angle = 2 * Math.PI * i / orphanEndpoints.size();
                orphan.setX(centerX + ORPHAN_RADIUS * Math.cos(angle));
                orphan.setY(centerY + ORPHAN_RADIUS * Math.sin(angle));
            }
        }
    }
}
