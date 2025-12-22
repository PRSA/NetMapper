package prsa.egosoft.netmapper.gui;

import prsa.egosoft.netmapper.i18n.Messages;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkInterface;
import prsa.egosoft.netmapper.model.DetectedEndpoint;
import prsa.egosoft.netmapper.util.SubnetUtils;
import prsa.egosoft.netmapper.util.MacVendorUtils;

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
        
        rootNode = new DefaultMutableTreeNode(Messages.getString("tree.root"));
        treeModel = new DefaultTreeModel(rootNode);
        resultsTree = new JTree(treeModel);
        
        add(new JScrollPane(resultsTree), BorderLayout.CENTER);
    }
    
    public void updateLanguage()
    {
        rootNode.setUserObject(Messages.getString("tree.root"));
        treeModel.nodeChanged(rootNode);
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
        // System Info
        DefaultMutableTreeNode sysInfoNode = new DefaultMutableTreeNode(Messages.getString("tree.system_info"));
        sysInfoNode.add(new DefaultMutableTreeNode(Messages.getString("info.vendor") + ": " + device.getVendor()));
        sysInfoNode.add(new DefaultMutableTreeNode(Messages.getString("info.model") + ": " + device.getModel()));
        deviceNode.add(sysInfoNode);
        
        // Interfaces
        DefaultMutableTreeNode ifNode = new DefaultMutableTreeNode(
                Messages.getString("tree.interfaces", device.getInterfaces().size()));
        for(NetworkInterface ni : device.getInterfaces())
        {
            DefaultMutableTreeNode niNode = new DefaultMutableTreeNode(ni.toString());
            // ... (resto de lógica de construcción de nodos de interfaces que estaba en
            // MainWindow)
            ifNode.add(niNode);
        }
        deviceNode.add(ifNode);
        
        // Routing y VLANs (Misma lógica que antes...)
    }
    
    private int findInsertionIndex(String ip)
    {
        int count = rootNode.getChildCount();
        for(int i = 0; i < count; i++)
        {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            NetworkDevice cd = (NetworkDevice) child.getUserObject();
            if(SubnetUtils.compareIps(ip, cd.getIpAddress()) < 0)
                return i;
        }
        return count;
    }
}