package prsa.egosoft.netmapper.gui;

import prsa.egosoft.netmapper.i18n.Messages;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkInterface;
import prsa.egosoft.netmapper.model.DetectedEndpoint;
import prsa.egosoft.netmapper.service.NetworkScannerService;
import prsa.egosoft.netmapper.util.NetworkDiscoveryUtils;
import prsa.egosoft.netmapper.Main;
import java.util.List;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

public class MainWindow extends JFrame
{
    private NetworkScannerService scannerService;
    private JTextField ipField;
    private JTextField communityField;
    private JTextArea logArea;
    private JTree resultsTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JTabbedPane tabbedPane;
    private NetworkMapPanel mapPanel;
    
    private Map<String, DefaultMutableTreeNode> deviceNodeMap;
    
    // Componentes que necesitan actualización de idioma
    private JLabel ipLabel;
    private JLabel communityLabel;
    private JButton scanButton;
    private JButton autoButton;
    private JButton clearButton;
    private JComboBox<String> languageSelector;
    private String[] languages =
    {"Español", "English"};
    private java.util.Locale[] locales =
    {new java.util.Locale("es"), java.util.Locale.ENGLISH};
    
    public MainWindow()
    {
        super(Messages.getString("window.title",
                Messages.getString(Main.IS_ADMIN ? "window.adminmode" : "window.usermode")));
        this.scannerService = new NetworkScannerService();
        this.deviceNodeMap = new java.util.HashMap<>();
        
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        initComponents();
        Messages.addLocaleListener(this::updateUITexts);
        updateUITexts(); // Inicializar textos
    }
    
    private void initComponents()
    {
        // Panel Superior: Configuración usando GridBagLayout para mejor control
        JPanel configPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Fila 0
        gbc.gridx = 0;
        gbc.gridy = 0;
        ipLabel = new JLabel();
        configPanel.add(ipLabel, gbc);
        
        gbc.gridx = 1;
        ipField = new JTextField("192.168.1.0/24", 15);
        configPanel.add(ipField, gbc);
        
        gbc.gridx = 2;
        communityLabel = new JLabel();
        configPanel.add(communityLabel, gbc);
        
        gbc.gridx = 3;
        communityField = new JTextField("public", 10);
        configPanel.add(communityField, gbc);
        
        gbc.gridx = 4;
        scanButton = new JButton();
        scanButton.addActionListener(e -> startScan());
        configPanel.add(scanButton, gbc);
        
        gbc.gridx = 5;
        clearButton = new JButton();
        clearButton.addActionListener(e -> resetApp());
        configPanel.add(clearButton, gbc);
        
        // Selector de Idioma (fianl de la fila 0)
        gbc.gridx = 6;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        languageSelector = new JComboBox<>(languages);
        if(Messages.getLocale().getLanguage().equals("en"))
        {
            languageSelector.setSelectedIndex(1);
        }
        else
        {
            languageSelector.setSelectedIndex(0);
        }
        languageSelector.addActionListener(e ->
        {
            int index = languageSelector.getSelectedIndex();
            Messages.setLocale(locales[index]);
        });
        configPanel.add(languageSelector, gbc);
        
        // Fila 1: Botón Auto Descubrimiento debajo del objetivo
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        autoButton = new JButton();
        autoButton.addActionListener(e -> startAutoDiscovery());
        configPanel.add(autoButton, gbc);
        
        // Panel Central: Árbol de resultados
        rootNode = new DefaultMutableTreeNode(Messages.getString("tree.root"));
        treeModel = new DefaultTreeModel(rootNode);
        resultsTree = new JTree(treeModel);
        
        JScrollPane treeScroll = new JScrollPane(resultsTree);
        
        // Panel de Mapa
        mapPanel = new NetworkMapPanel();
        
        // JTabbedPane
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab(Messages.getString("tab.devices"), treeScroll);
        tabbedPane.addTab(Messages.getString("tab.map"), mapPanel);
        
        // Panel Inferior: Logs
        logArea = new JTextArea(5, 50);
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        
        // Layout Principal
        setLayout(new BorderLayout());
        add(configPanel, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
        add(logScroll, BorderLayout.SOUTH);
    }
    
    public void updateUITexts()
    {
        setTitle(Messages.getString("window.title",
                Messages.getString(Main.IS_ADMIN ? "window.adminmode" : "window.usermode")));
        ipLabel.setText(Messages.getString("label.target") + ":");
        communityLabel.setText(Messages.getString("label.community") + ":");
        scanButton.setText(Messages.getString("button.scan"));
        autoButton.setText(Messages.getString("button.autodiscover"));
        clearButton.setText(Messages.getString("button.clear"));
        
        if(tabbedPane != null)
        {
            tabbedPane.setTitleAt(0, Messages.getString("tab.devices"));
            tabbedPane.setTitleAt(1, Messages.getString("tab.map"));
        }
        
        String tooltipText = Messages.getString("tooltip.target.formats");
        ipLabel.setToolTipText(tooltipText);
        ipField.setToolTipText(tooltipText);
        
        tooltipText = Messages.getString("tooltip.community");
        communityLabel.setToolTipText(tooltipText);
        communityField.setToolTipText(tooltipText);
        
        tooltipText = Messages.getString("tooltip.scan");
        scanButton.setToolTipText(tooltipText);
        
        tooltipText = Messages.getString("tooltip.autodiscover");
        autoButton.setToolTipText(tooltipText);
        
        tooltipText = Messages.getString("tooltip.clear");
        clearButton.setToolTipText(tooltipText);
        
        tooltipText = Messages.getString("tooltip.language");
        languageSelector.setToolTipText(tooltipText);
        
        mapPanel.updateUITexts();
        
        if(rootNode != null)
        {
            rootNode.setUserObject(Messages.getString("tree.root"));
            treeModel.nodeChanged(rootNode);
        }
        
        // Re-display existing devices to update their group labels if necessary
        // (though model objects won't change strings magically,
        // static parts of the tree like "Interfaces (3)" are generated in
        // displayDevice)
        // For simplicity, we only update the root here.
    }
    
    private void startAutoDiscovery()
    {
        logArea.append(Messages.getString("message.autodiscover_start") + "\n");
        List<String> networks = NetworkDiscoveryUtils.discoverLocalNetworks();
        
        if(networks.isEmpty())
        {
            logArea.append(Messages.getString("message.error_no_networks") + "\n");
            return;
        }
        
        logArea.append(Messages.getString("message.networks_found", networks.toString()) + "\n");
        
        String community = communityField.getText().trim();
        for(String network : networks)
        {
            logArea.append(Messages.getString("message.scan_start", network) + "\n");
            scannerService.scanNetwork(network, community,
                    device -> SwingUtilities.invokeLater(() -> displayDevice(device)),
                    error -> SwingUtilities.invokeLater(() -> logArea.append(error + "\n")));
        }
    }
    
    private void startScan()
    {
        String ip = ipField.getText().trim();
        String community = communityField.getText().trim();
        
        // Validate input format
        if(!isValidTargetInput(ip))
        {
            JOptionPane.showMessageDialog(this,
                    Messages.getString("validation.invalid_format") + "\n\n"
                            + Messages.getString("validation.formats_title") + "\n"
                            + Messages.getString("validation.format.single_ip") + "\n"
                            + Messages.getString("validation.format.cidr") + "\n"
                            + Messages.getString("validation.format.netmask") + "\n"
                            + Messages.getString("validation.format.range") + "\n"
                            + Messages.getString("validation.format.list"),
                    Messages.getString("validation.error_title"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        logArea.append(Messages.getString("message.scan_start", ip) + "\n");
        
        scannerService.scanNetwork(ip, community, device -> SwingUtilities.invokeLater(() -> displayDevice(device)),
                error -> SwingUtilities.invokeLater(() -> logArea.append(error + "\n"))); // Simplified error log
    }
    
    private boolean isValidTargetInput(String input)
    {
        if(input == null || input.isEmpty())
        {
            return false;
        }
        
        // Check for comma-separated list
        if(input.contains(","))
        {
            String[] parts = input.split(",");
            for(String part : parts)
            {
                if(!isValidSingleTarget(part.trim()))
                {
                    return false;
                }
            }
            return true;
        }
        
        return isValidSingleTarget(input);
    }
    
    private boolean isValidSingleTarget(String target)
    {
        // Check for CIDR notation or IP/Netmask
        if(target.contains("/"))
        {
            String[] parts = target.split("/");
            if(parts.length != 2)
                return false;
            if(!isValidIpAddress(parts[0]))
                return false;
            
            // Check if it's CIDR (numeric prefix) or netmask (IP address)
            if(parts[1].contains("."))
            {
                // IP/Netmask format (e.g., 192.168.1.0/255.255.255.0)
                return isValidIpAddress(parts[1]);
            }
            else
            {
                // CIDR format (e.g., 192.168.1.0/24)
                try
                {
                    int prefix = Integer.parseInt(parts[1]);
                    return prefix >= 0 && prefix <= 32;
                }
                catch(NumberFormatException e)
                {
                    return false;
                }
            }
        }
        
        // Check for range notation (e.g., 192.168.1.1-192.168.1.50)
        if(target.contains("-"))
        {
            String[] parts = target.split("-");
            if(parts.length != 2)
                return false;
            return isValidIpAddress(parts[0].trim()) && isValidIpAddress(parts[1].trim());
        }
        
        // Single IP address
        return isValidIpAddress(target);
    }
    
    private boolean isValidIpAddress(String ip)
    {
        if(ip == null || ip.isEmpty())
            return false;
        String[] parts = ip.split("\\.");
        if(parts.length != 4)
            return false;
        
        for(String part : parts)
        {
            try
            {
                int num = Integer.parseInt(part);
                if(num < 0 || num > 255)
                    return false;
            }
            catch(NumberFormatException e)
            {
                return false;
            }
        }
        return true;
    }
    
    private void displayDevice(NetworkDevice device)
    {
        logArea.append(Messages.getString("message.scan_complete", device.toString()) + "\n");
        
        DefaultMutableTreeNode deviceNode;
        boolean isNew = false;
        
        if(deviceNodeMap.containsKey(device.getIpAddress()))
        {
            deviceNode = deviceNodeMap.get(device.getIpAddress());
            deviceNode.setUserObject(device); // Store object, not string
            deviceNode.removeAllChildren();
        }
        else
        {
            deviceNode = new DefaultMutableTreeNode(device); // Store object
            deviceNodeMap.put(device.getIpAddress(), deviceNode);
            isNew = true;
        }
        
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
                niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.type") + ": " + ni.getType()));
            if(ni.getMtu() > 0)
                niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.mtu") + ": " + ni.getMtu()));
            if(ni.getSpeed() != null)
                niNode.add(new DefaultMutableTreeNode(Messages.getString("interface.speed") + ": " + ni.getSpeed() + " "
                        + Messages.getString("interface.speed_unit")));
            
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
        
        if(isNew)
        {
            // Find insertion index
            int count = rootNode.getChildCount();
            int index = count;
            for(int i = 0; i < count; i++)
            {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) rootNode.getChildAt(i);
                Object userObj = child.getUserObject();
                if(userObj instanceof NetworkDevice)
                {
                    NetworkDevice childDevice = (NetworkDevice) userObj;
                    if(prsa.egosoft.netmapper.util.SubnetUtils.compareIps(device.getIpAddress(),
                            childDevice.getIpAddress()) < 0)
                    {
                        index = i;
                        break;
                    }
                }
            }
            treeModel.insertNodeInto(deviceNode, rootNode, index);
            resultsTree.scrollPathToVisible(new javax.swing.tree.TreePath(deviceNode.getPath()));
        }
        else
        {
            treeModel.nodeStructureChanged(deviceNode);
        }
        
        // Update map panel
        Map<String, NetworkDevice> devices = new java.util.HashMap<>();
        for(Map.Entry<String, DefaultMutableTreeNode> entry : deviceNodeMap.entrySet())
        {
            Object userObj = entry.getValue().getUserObject();
            if(userObj instanceof NetworkDevice)
            {
                devices.put(entry.getKey(), (NetworkDevice) userObj);
            }
        }
        mapPanel.updateMap(devices);
    }
    
    private void resetApp()
    {
        rootNode.removeAllChildren();
        treeModel.reload();
        deviceNodeMap.clear();
        ipField.setText("");
        logArea.setText("");
        mapPanel.updateMap(new java.util.HashMap<>());
        logArea.append(Messages.getString("message.reset_complete") + "\n");
    }
}
