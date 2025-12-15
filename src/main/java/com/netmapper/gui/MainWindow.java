package com.netmapper.gui;

import com.netmapper.i18n.Messages;
import com.netmapper.model.NetworkDevice;
import com.netmapper.model.NetworkInterface;
import com.netmapper.model.DetectedEndpoint;
import com.netmapper.service.NetworkScannerService;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Map;

public class MainWindow extends JFrame {
    private NetworkScannerService scannerService;
    private JTextField ipField;
    private JTextField communityField;
    private JTextArea logArea;
    private JTree resultsTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;

    private Map<String, DefaultMutableTreeNode> deviceNodeMap;

    public MainWindow() {
        super(Messages.getString("window.title"));
        this.scannerService = new NetworkScannerService();
        this.deviceNodeMap = new java.util.HashMap<>();

        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
    }

    private void initComponents() {
        // Panel Superior: Configuración
        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JLabel ipLabel = new JLabel(Messages.getString("label.target") + ":");
        String tooltipText = Messages.getString("tooltip.target.formats");
        ipLabel.setToolTipText(tooltipText);
        configPanel.add(ipLabel);

        ipField = new JTextField("192.168.1.0/24", 15);
        ipField.setToolTipText(tooltipText);
        configPanel.add(ipField);

        configPanel.add(new JLabel(Messages.getString("label.community") + ":"));
        communityField = new JTextField("public", 10);
        configPanel.add(communityField);

        JButton scanButton = new JButton(Messages.getString("button.scan"));
        scanButton.addActionListener(e -> startScan());
        configPanel.add(scanButton);

        JButton clearButton = new JButton(Messages.getString("button.clear"));
        clearButton.addActionListener(e -> resetApp());
        configPanel.add(clearButton);

        JButton mapButton = new JButton(Messages.getString("button.map"));
        mapButton.addActionListener(e -> showNetworkMap());
        configPanel.add(mapButton);

        // Panel Central: Árbol de resultados y Detalles
        rootNode = new DefaultMutableTreeNode(Messages.getString("tree.root"));
        treeModel = new DefaultTreeModel(rootNode);
        resultsTree = new JTree(treeModel);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(resultsTree), new JPanel());
        splitPane.setDividerLocation(300);

        // Panel Inferior: Logs
        logArea = new JTextArea(5, 50);
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);

        // Layout Principal
        setLayout(new BorderLayout());
        add(configPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(logScroll, BorderLayout.SOUTH);
    }

    private void startScan() {
        String ip = ipField.getText().trim();
        String community = communityField.getText().trim();

        // Validate input format
        if (!isValidTargetInput(ip)) {
            JOptionPane.showMessageDialog(this,
                    Messages.getString("validation.invalid_format") + "\n\n" +
                            Messages.getString("validation.formats_title") + "\n" +
                            Messages.getString("validation.format.single_ip") + "\n" +
                            Messages.getString("validation.format.cidr") + "\n" +
                            Messages.getString("validation.format.netmask") + "\n" +
                            Messages.getString("validation.format.range") + "\n" +
                            Messages.getString("validation.format.list"),
                    Messages.getString("validation.error_title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        logArea.append(Messages.getString("message.scan_start", ip) + "\n");

        scannerService.scanNetwork(ip, community,
                device -> SwingUtilities.invokeLater(() -> displayDevice(device)),
                error -> SwingUtilities.invokeLater(() -> logArea.append(error + "\n"))); // Simplified error log
    }

    private boolean isValidTargetInput(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        // Check for comma-separated list
        if (input.contains(",")) {
            String[] parts = input.split(",");
            for (String part : parts) {
                if (!isValidSingleTarget(part.trim())) {
                    return false;
                }
            }
            return true;
        }

        return isValidSingleTarget(input);
    }

    private boolean isValidSingleTarget(String target) {
        // Check for CIDR notation or IP/Netmask
        if (target.contains("/")) {
            String[] parts = target.split("/");
            if (parts.length != 2)
                return false;
            if (!isValidIpAddress(parts[0]))
                return false;

            // Check if it's CIDR (numeric prefix) or netmask (IP address)
            if (parts[1].contains(".")) {
                // IP/Netmask format (e.g., 192.168.1.0/255.255.255.0)
                return isValidIpAddress(parts[1]);
            } else {
                // CIDR format (e.g., 192.168.1.0/24)
                try {
                    int prefix = Integer.parseInt(parts[1]);
                    return prefix >= 0 && prefix <= 32;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }

        // Check for range notation (e.g., 192.168.1.1-192.168.1.50)
        if (target.contains("-")) {
            String[] parts = target.split("-");
            if (parts.length != 2)
                return false;
            return isValidIpAddress(parts[0].trim()) && isValidIpAddress(parts[1].trim());
        }

        // Single IP address
        return isValidIpAddress(target);
    }

    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty())
            return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4)
            return false;

        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255)
                    return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private void displayDevice(NetworkDevice device) {
        logArea.append(Messages.getString("message.scan_complete", device.toString()) + "\n");

        DefaultMutableTreeNode deviceNode;
        boolean isNew = false;

        if (deviceNodeMap.containsKey(device.getIpAddress())) {
            deviceNode = deviceNodeMap.get(device.getIpAddress());
            deviceNode.setUserObject(device); // Store object, not string
            deviceNode.removeAllChildren();
        } else {
            deviceNode = new DefaultMutableTreeNode(device); // Store object
            deviceNodeMap.put(device.getIpAddress(), deviceNode);
            isNew = true;
        }

        // System Info Node
        DefaultMutableTreeNode sysInfoNode = new DefaultMutableTreeNode(Messages.getString("tree.system_info"));
        sysInfoNode.add(new DefaultMutableTreeNode(Messages.getString("info.vendor") + ": " + device.getVendor()));
        sysInfoNode.add(new DefaultMutableTreeNode(Messages.getString("info.model") + ": " + device.getModel()));
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
        for (NetworkInterface ni : device.getInterfaces()) {
            DefaultMutableTreeNode niNode = new DefaultMutableTreeNode(ni.toString());

            // Detalles de configuración
            niNode.add(new DefaultMutableTreeNode("Estado Admin: " + ni.getAdminStatus()));
            niNode.add(new DefaultMutableTreeNode("Estado Oper: " + ni.getOperStatus()));
            // Display MAC address with vendor information
            if (ni.getMacAddress() != null) {
                String macDisplay = "MAC: " + ni.getMacAddress();
                String vendor = com.netmapper.util.MacVendorUtils.getVendor(ni.getMacAddress());
                if (vendor != null && !vendor.isEmpty() && !"Unknown".equals(vendor)) {
                    macDisplay += " (" + vendor + ")";
                }
                niNode.add(new DefaultMutableTreeNode(macDisplay));
            } else {
                niNode.add(new DefaultMutableTreeNode("MAC: N/A"));
            }

            if (ni.getType() != null)
                niNode.add(new DefaultMutableTreeNode("Tipo: " + ni.getType()));
            if (ni.getMtu() > 0)
                niNode.add(new DefaultMutableTreeNode("MTU: " + ni.getMtu()));
            if (ni.getSpeed() != null)
                niNode.add(new DefaultMutableTreeNode("Velocidad: " + ni.getSpeed() + " bps"));

            if (ni.getIpAddress() != null) {
                niNode.add(new DefaultMutableTreeNode("IP: " + ni.getIpAddress()));
                niNode.add(new DefaultMutableTreeNode("Máscara: " + ni.getSubnetMask()));
            }

            if (ni.getUntaggedVlanId() > 0) {
                niNode.add(new DefaultMutableTreeNode("VLAN Nativa: " + ni.getUntaggedVlanId()));
            }

            if (!ni.getTaggedVlans().isEmpty()) {
                niNode.add(new DefaultMutableTreeNode("VLANs Etiquetadas: " + ni.getTaggedVlans()));
            }

            // MACs aprendidas en este puerto
            java.util.List<DetectedEndpoint> learnedEndpoints = device.getMacAddressTable().get(ni.getIndex());
            if (learnedEndpoints != null && !learnedEndpoints.isEmpty()) {
                DefaultMutableTreeNode learnedNode = new DefaultMutableTreeNode(
                        "Equipos Detectados (" + learnedEndpoints.size() + ")");
                for (DetectedEndpoint endpoint : learnedEndpoints) {
                    learnedNode.add(new DefaultMutableTreeNode(endpoint.toString()));
                }
                niNode.add(learnedNode);
            }

            ifNode.add(niNode);
        }
        deviceNode.add(ifNode);

        // Routing Node
        if (!device.getRoutingTable().isEmpty()) {
            DefaultMutableTreeNode routeNode = new DefaultMutableTreeNode("Tabla de Rutas");
            for (Map.Entry<String, String> route : device.getRoutingTable().entrySet()) {
                routeNode.add(new DefaultMutableTreeNode("Dest: " + route.getKey() + " -> Gw: " + route.getValue()));
            }
            deviceNode.add(routeNode);
        }

        // VLANs Node
        if (!device.getVlans().isEmpty()) {
            DefaultMutableTreeNode vlanNode = new DefaultMutableTreeNode("VLANs (" + device.getVlans().size() + ")");
            for (String vlan : device.getVlans()) {
                vlanNode.add(new DefaultMutableTreeNode(vlan));
            }
            deviceNode.add(vlanNode);
        }

        if (isNew) {
            // Find insertion index
            int count = rootNode.getChildCount();
            int index = count;
            for (int i = 0; i < count; i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) rootNode.getChildAt(i);
                Object userObj = child.getUserObject();
                if (userObj instanceof NetworkDevice) {
                    NetworkDevice childDevice = (NetworkDevice) userObj;
                    if (com.netmapper.util.SubnetUtils.compareIps(device.getIpAddress(),
                            childDevice.getIpAddress()) < 0) {
                        index = i;
                        break;
                    }
                }
            }
            treeModel.insertNodeInto(deviceNode, rootNode, index);
            resultsTree.scrollPathToVisible(new javax.swing.tree.TreePath(deviceNode.getPath()));
        } else {
            treeModel.nodeStructureChanged(deviceNode);
        }
    }

    private void resetApp() {
        rootNode.removeAllChildren();
        treeModel.reload();
        deviceNodeMap.clear();
        ipField.setText("");
        logArea.setText("");
        logArea.append("Estado reseteado.\n");
    }

    private void showNetworkMap() {
        if (deviceNodeMap.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No hay dispositivos escaneados para mostrar en el mapa.",
                    "Mapa Vacío",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Extract NetworkDevice objects from tree nodes
        Map<String, NetworkDevice> devices = new java.util.HashMap<>();
        for (Map.Entry<String, DefaultMutableTreeNode> entry : deviceNodeMap.entrySet()) {
            Object userObj = entry.getValue().getUserObject();
            if (userObj instanceof NetworkDevice) {
                devices.put(entry.getKey(), (NetworkDevice) userObj);
            }
        }

        NetworkMapDialog dialog = new NetworkMapDialog(this, devices);
        dialog.setVisible(true);
    }
}
