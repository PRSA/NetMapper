package prsa.egosoft.netmapper.gui;

import prsa.egosoft.netmapper.i18n.Messages;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkInterface;
import prsa.egosoft.netmapper.model.DetectedEndpoint;
import prsa.egosoft.netmapper.util.SubnetUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Map;
import java.util.HashMap;

public class DeviceTreePanel extends JPanel
{
    private JTree resultsTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private Map<String, DefaultMutableTreeNode> deviceNodeMap;
    
    public DeviceTreePanel()
    {
        setLayout(new BorderLayout());
        deviceNodeMap = new HashMap<>();
        
        initComponents();
        updateUITexts();
    }
    
    private void initComponents()
    {
        rootNode = new DefaultMutableTreeNode(Messages.getString("tree.root"));
        treeModel = new DefaultTreeModel(rootNode);
        resultsTree = new JTree(treeModel);
        
        add(new JScrollPane(resultsTree), BorderLayout.CENTER);
    }
    
    public void updateUITexts()
    {
        rootNode.setUserObject(Messages.getString("tree.root"));
        treeModel.nodeChanged(rootNode);
        
        // Re-display existing devices to update their group labels if necessary
        // (though model objects won't change strings magically,
        // static parts of the tree like "Interfaces (3)" are generated in
        // displayDevice)
        // For simplicity, we only update the root here.
    }
    
    public void clear()
    {
        rootNode.removeAllChildren();
        treeModel.reload();
        deviceNodeMap.clear();
    }
    
    public void addOrUpdateDevice(NetworkDevice device)
    {
        DefaultMutableTreeNode deviceNode;
        boolean isNew = false;
        
        if(deviceNodeMap.containsKey(device.getIpAddress()))
        {
            deviceNode = deviceNodeMap.get(device.getIpAddress());
            deviceNode.setUserObject(device);
            deviceNode.removeAllChildren();
        }
        else
        {
            deviceNode = new DefaultMutableTreeNode(device);
            deviceNodeMap.put(device.getIpAddress(), deviceNode);
            isNew = true;
        }
        
        buildDeviceNodes(device, deviceNode);
        
        if(isNew)
        {
            int index = findInsertionIndex(device.getIpAddress());
            treeModel.insertNodeInto(deviceNode, rootNode, index);
            resultsTree.scrollPathToVisible(new TreePath(deviceNode.getPath()));
        }
        else
        {
            treeModel.nodeStructureChanged(deviceNode);
        }
    }
    
    private void buildDeviceNodes(NetworkDevice device, DefaultMutableTreeNode deviceNode)
    {
        // System Info Node
        DefaultMutableTreeNode sysInfoNode = new DefaultMutableTreeNode(Messages.getString("tree.system_info"));
        sysInfoNode.add(new DefaultMutableTreeNode(Messages.getString("info.vendor") + ": " + device.getVendor()));
        sysInfoNode.add(new DefaultMutableTreeNode(Messages.getString("info.model") + ": " + device.getModel()));
        String typeDisplay = device.getDeviceType();
        if(device.getFormattedServices() != null && !device.getFormattedServices().isEmpty())
        {
            typeDisplay += " (" + device.getFormattedServices() + ")";
        }
        sysInfoNode.add(new DefaultMutableTreeNode(Messages.getString("info.type") + ": " + typeDisplay));
        sysInfoNode
                .add(new DefaultMutableTreeNode(Messages.getString("info.description") + ": " + device.getSysDescr()));
        sysInfoNode
                .add(new DefaultMutableTreeNode(Messages.getString("info.location") + ": " + device.getSysLocation()));
        sysInfoNode.add(new DefaultMutableTreeNode(Messages.getString("info.contact") + ": " + device.getSysContact()));
        sysInfoNode.add(new DefaultMutableTreeNode(Messages.getString("info.uptime") + ": " + device.getSysUpTime()));
        deviceNode.add(sysInfoNode);
        
        // Interfaces Node
        DefaultMutableTreeNode ifNode = new DefaultMutableTreeNode(
                Messages.getString("tree.interfaces", device.getInterfaces().size()));
        for(NetworkInterface ni : device.getInterfaces())
        {
            DefaultMutableTreeNode niNode = new DefaultMutableTreeNode(ni.toString());
            
            // Detalles de configuración
            niNode.add(new DefaultMutableTreeNode(
                    Messages.getString("interface.admin_status") + ": " + ni.getAdminStatus()));
            niNode.add(new DefaultMutableTreeNode(
                    Messages.getString("interface.oper_status") + ": " + ni.getOperStatus()));
            // Display MAC address with vendor information
            if(ni.getMacAddress() != null)
            {
                String macDisplay = Messages.getString("interface.mac") + ": " + ni.getMacAddress();
                String vendor = prsa.egosoft.netmapper.util.MacVendorUtils.getVendor(ni.getMacAddress());
                if(vendor != null && !vendor.isEmpty() && !"Unknown".equals(vendor))
                {
                    macDisplay += " (" + vendor + ")";
                }
                niNode.add(new DefaultMutableTreeNode(macDisplay));
            }
            else
            {
                niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.mac_na")));
            }
            
            if(ni.getType() != null)
            {
                niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.type") + ": " + ni.getType()));
            }
            if(ni.getMtu() > 0)
            {
                niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.mtu") + ": " + ni.getMtu()));
            }
            if(ni.getSpeed() != null)
            {
                niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.speed") + ": " + ni.getSpeed() + " "
                        + Messages.getString("interface.speed_unit")));
            }
            
            if(ni.getIpAddress() != null)
            {
                niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.ip") + ": " + ni.getIpAddress()));
                niNode.add(
                        new DefaultMutableTreeNode(Messages.getString("interface.mask") + ": " + ni.getSubnetMask()));
            }
            
            if(ni.getUntaggedVlanId() > 0)
            {
                niNode.add(new DefaultMutableTreeNode(
                        Messages.getString("interface.vlan_native_label") + ": " + ni.getUntaggedVlanId()));
            }
            
            if(!ni.getTaggedVlans().isEmpty())
            {
                niNode.add(new DefaultMutableTreeNode(
                        Messages.getString("interface.vlans_tagged_label") + ": " + ni.getTaggedVlans()));
            }
            
            // MACs aprendidas en este puerto
            java.util.List<DetectedEndpoint> learnedEndpoints = device.getMacAddressTable().get(ni.getIndex());
            if(learnedEndpoints != null && !learnedEndpoints.isEmpty())
            {
                DefaultMutableTreeNode learnedNode = new DefaultMutableTreeNode(
                        Messages.getString("interface.learned_endpoints", learnedEndpoints.size()));
                for(DetectedEndpoint endpoint : learnedEndpoints)
                {
                    learnedNode.add(new DefaultMutableTreeNode(endpoint.toString()));
                }
                niNode.add(learnedNode);
            }
            ifNode.add(niNode);
        }
        deviceNode.add(ifNode);
        
        // Routing Node
        if(!device.getRoutingTable().isEmpty())
        {
            DefaultMutableTreeNode routeNode = new DefaultMutableTreeNode(Messages.getString("tree.routes"));
            for(Map.Entry<String, String> route : device.getRoutingTable().entrySet())
            {
                routeNode.add(
                        new DefaultMutableTreeNode(Messages.getString("route.destination_label") + ": " + route.getKey()
                                + " -> " + Messages.getString("route.gateway_label") + ": " + route.getValue()));
            }
            deviceNode.add(routeNode);
        }
        
        // VLANs Node
        if(!device.getVlans().isEmpty())
        {
            DefaultMutableTreeNode vlanNode = new DefaultMutableTreeNode(
                    Messages.getString("tree.vlans", device.getVlans().size()));
            for(String vlan : device.getVlans())
            {
                vlanNode.add(new DefaultMutableTreeNode(vlan));
            }
            deviceNode.add(vlanNode);
        }
    }
    
    private int findInsertionIndex(String ip)
    {
        int count = rootNode.getChildCount();
        for(int i = 0; i < count; i++)
        {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            NetworkDevice cd = (NetworkDevice) child.getUserObject();
            if(SubnetUtils.compareIps(ip, cd.getIpAddress()) < 0)
            {
                return i;
            }
        }
        return count;
    }
}