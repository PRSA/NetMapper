package com.netmapper.gui;

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

    public MainWindow() {
        super("NetMapper - Java SNMP Tool");
        this.scannerService = new NetworkScannerService();

        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
    }

    private void initComponents() {
        // Panel Superior: Configuración
        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        configPanel.add(new JLabel("IP Objetivo / CIDR:"));
        ipField = new JTextField("192.168.1.0/24", 15);
        configPanel.add(ipField);

        configPanel.add(new JLabel("Comunidad:"));
        communityField = new JTextField("public", 10);
        configPanel.add(communityField);

        JButton scanButton = new JButton("Escanear");
        scanButton.addActionListener(e -> startScan());
        configPanel.add(scanButton);

        // Panel Central: Árbol de resultados y Detalles
        rootNode = new DefaultMutableTreeNode("Dispositivos");
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

        logArea.append("Iniciando escaneo de rango/IP: " + ip + "...\n");

        scannerService.scanNetwork(ip, community,
                device -> SwingUtilities.invokeLater(() -> displayDevice(device)),
                error -> SwingUtilities.invokeLater(() -> logArea.append(error + "\n"))); // Simplified error log
    }

    private void displayDevice(NetworkDevice device) {
        logArea.append("Escaneo completado para " + device.toString() + "\n");

        DefaultMutableTreeNode deviceNode = new DefaultMutableTreeNode(device.toString());

        // System Info Node
        DefaultMutableTreeNode sysInfoNode = new DefaultMutableTreeNode("Información del Sistema");
        sysInfoNode.add(new DefaultMutableTreeNode("Marca: " + device.getVendor()));
        sysInfoNode.add(new DefaultMutableTreeNode("Modelo: " + device.getModel()));
        sysInfoNode.add(new DefaultMutableTreeNode("Descripción: " + device.getSysDescr()));
        sysInfoNode.add(new DefaultMutableTreeNode("Ubicación: " + device.getSysLocation()));
        sysInfoNode.add(new DefaultMutableTreeNode("Contacto: " + device.getSysContact()));
        sysInfoNode.add(new DefaultMutableTreeNode("Tiempo de actividad: " + device.getSysUpTime()));
        deviceNode.add(sysInfoNode);

        // Interfaces Node
        DefaultMutableTreeNode ifNode = new DefaultMutableTreeNode(
                "Interfaces (" + device.getInterfaces().size() + ")");
        for (NetworkInterface ni : device.getInterfaces()) {
            DefaultMutableTreeNode niNode = new DefaultMutableTreeNode(ni.toString());

            // Detalles de configuración
            niNode.add(new DefaultMutableTreeNode("Estado Admin: " + ni.getAdminStatus()));
            niNode.add(new DefaultMutableTreeNode("Estado Oper: " + ni.getOperStatus()));
            niNode.add(new DefaultMutableTreeNode("MAC: " + (ni.getMacAddress() != null ? ni.getMacAddress() : "N/A")));

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

        rootNode.add(deviceNode);
        treeModel.reload();
    }
}
