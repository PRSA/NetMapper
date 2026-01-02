package prsa.egosoft.netmapper.gui;

import prsa.egosoft.netmapper.i18n.Messages;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.service.NetworkController;
import prsa.egosoft.netmapper.Main;

import javax.swing.*;
import java.awt.*;

public class MainWindow extends JFrame {
    private NetworkController networkController;
    private JTextField ipField;
    private JTextField communityField;
    private JTextArea logArea;
    private JTabbedPane tabbedPane;
    private DeviceTreePanel treePanel;
    private NetworkMapPanel mapPanel;

    // private Map<String, DefaultMutableTreeNode> deviceNodeMap;

    // Componentes que necesitan actualización de idioma
    private JLabel ipLabel;
    private JLabel communityLabel;
    private JButton scanButton;
    private JButton autoButton;
    private JButton clearButton;
    private JButton loadMapButton;
    private JComboBox<String> languageSelector;

    public MainWindow() {
        super(Messages.getString("window.title",
                Messages.getString(Main.IS_ADMIN ? "window.adminmode" : "window.usermode")));
        this.networkController = new NetworkController();
        // this.deviceNodeMap = new java.util.HashMap<>();

        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        Messages.addLocaleListener(this::updateUITexts);
        updateUITexts(); // Inicializar textos
    }

    private void initComponents() {
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

        // Selector de Idioma (final de la fila 0)
        gbc.gridx = 6;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        languageSelector = new JComboBox<>(Messages.getAvailableLanguages());

        int languageIndex = Messages.defaultLanguageIndex;
        for (int i = 0; i < Messages.getAvailableLocales().length; i++) {
            if (Messages.getAvailableLocales()[i].equals(Messages.getLocale())) {
                languageIndex = i;
                break;
            }
        }
        languageSelector.setSelectedIndex(languageIndex);
        languageSelector.addActionListener(e -> {
            int index = languageSelector.getSelectedIndex();
            Messages.setLocale(Messages.getAvailableLocales()[index]);
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

        // Botón Cargar Mapa
        gbc.gridx = 2;
        gbc.gridwidth = 1;
        loadMapButton = new JButton();
        loadMapButton.addActionListener(e -> loadMap());
        configPanel.add(loadMapButton, gbc);

        treePanel = new DeviceTreePanel();

        // Panel de Mapa
        mapPanel = new NetworkMapPanel();

        // JTabbedPane
        tabbedPane = new JTabbedPane();
        // tabbedPane.addTab(Messages.getString("tab.devices"), treeScroll);
        tabbedPane.addTab(Messages.getString("tab.devices"), treePanel);
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

    public void updateUITexts() {
        setTitle(Messages.getString("window.title",
                Messages.getString(Main.IS_ADMIN ? "window.adminmode" : "window.usermode")));
        ipLabel.setText(Messages.getString("label.target") + ":");
        communityLabel.setText(Messages.getString("label.community") + ":");
        scanButton.setText(Messages.getString("button.scan"));
        autoButton.setText(Messages.getString("button.autodiscover"));
        clearButton.setText(Messages.getString("button.clear"));
        loadMapButton.setText(Messages.getString("button.load_map"));

        if (tabbedPane != null) {
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

        tooltipText = Messages.getString("tooltip.load_map");
        loadMapButton.setToolTipText(tooltipText);

        tooltipText = Messages.getString("tooltip.language");
        languageSelector.setToolTipText(tooltipText);

        treePanel.updateUITexts();
        mapPanel.updateUITexts();
    }

    private void startAutoDiscovery() {
        logArea.append(Messages.getString("message.autodiscover_start") + "\n");
        String community = communityField.getText().trim();

        // Use the controller for async auto-discovery
        new Thread(() -> {
            networkController.autoDiscoverBlocking(community,
                    device -> SwingUtilities.invokeLater(() -> displayDevice(device)));
            SwingUtilities.invokeLater(() -> logArea.append(Messages.getString("message.scan_complete_all") + "\n"));
        }).start();
    }

    private void startScan() {
        String ip = ipField.getText().trim();
        String community = communityField.getText().trim();

        // Validate input format
        if (!isValidTargetInput(ip)) {
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

        networkController.scanNetworkAsync(ip, community,
                device -> SwingUtilities.invokeLater(() -> displayDevice(device)),
                error -> SwingUtilities.invokeLater(() -> logArea.append(error + "\n")));
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
            if (parts.length != 2) {
                return false;
            }
            if (!isValidIpAddress(parts[0])) {
                return false;
            }

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
            if (parts.length != 2) {
                return false;
            }
            return isValidIpAddress(parts[0].trim()) && isValidIpAddress(parts[1].trim());
        }

        // Single IP address
        return isValidIpAddress(target);
    }

    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private void loadMap() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(Messages.getString("dialog.load_map.title"));
        int userSelection = fileChooser.showOpenDialog(this);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            java.io.File fileToLoad = fileChooser.getSelectedFile();
            logArea.append(Messages.getString("message.loading_map", fileToLoad.getName()) + "\n");

            new Thread(() -> {
                try {
                    networkController.loadDevicesFromJson(fileToLoad);
                    java.util.Map<String, NetworkDevice> devices = networkController.getDiscoveredDevices();

                    SwingUtilities.invokeLater(() -> {
                        treePanel.clear();
                        mapPanel.clear(); // Ensure clean slate
                        for (NetworkDevice device : devices.values()) {
                            treePanel.addOrUpdateDevice(device);
                            mapPanel.addOrUpdateDevice(device);
                        }
                        mapPanel.updateMap();
                        logArea.append(Messages.getString("message.map_loaded", devices.size()) + "\n");
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                            Messages.getString("error.load_map", e.getMessage()),
                            Messages.getString("dialog.error.title"), JOptionPane.ERROR_MESSAGE));
                }
            }).start();
        }
    }

    private void displayDevice(NetworkDevice device) {
        logArea.append(Messages.getString("message.scan_complete", device.toString()) + "\n");

        treePanel.addOrUpdateDevice(device);
        mapPanel.addOrUpdateDevice(device);
        mapPanel.updateMap();
    }

    private void resetApp() {
        ipField.setText("");
        logArea.setText("");
        treePanel.clear();
        mapPanel.clear();
        logArea.append(Messages.getString("message.reset_complete") + "\n");
    }
}
