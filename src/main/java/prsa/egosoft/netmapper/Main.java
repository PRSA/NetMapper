package prsa.egosoft.netmapper;

import prsa.egosoft.netmapper.gui.MainWindow;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkGraph;
import prsa.egosoft.netmapper.service.ExportService;
import prsa.egosoft.netmapper.service.NetworkController;
import prsa.egosoft.netmapper.gui.NetworkMapPanel;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;

import javax.swing.SwingUtilities;

public class Main {
    public static final boolean IS_ADMIN;

    static {
        boolean isAdmin = false;
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("win")) {
                // En Windows, 'net session' devuelve error (código 1) si no eres admin
                Process process = Runtime.getRuntime().exec("net session");
                isAdmin = process.waitFor() == 0;
            } else {
                // En Unix (Linux/macOS), el ID de usuario 0 siempre es root
                Process process = Runtime.getRuntime().exec("id -u");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String uid = reader.readLine();
                isAdmin = "0".equals(uid);
            }
        } catch (Exception e) {
            isAdmin = false;
        }

        IS_ADMIN = isAdmin;
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            runHeadless(args);
        } else {
            SwingUtilities.invokeLater(() -> {
                new MainWindow().setVisible(true);
            });
        }
    }

    private static void runHeadless(String[] args) {
        String community = null;
        String targets = null;
        boolean autoDiscovery = false;
        String pngPath = null;
        String pdfPath = null;
        String jsonPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-c":
                    if (i + 1 < args.length) community = args[++i];
                    break;
                case "-t":
                    if (i + 1 < args.length) targets = args[++i];
                    break;
                case "-a":
                    autoDiscovery = true;
                    break;
                case "-png":
                    if (i + 1 < args.length) pngPath = args[++i];
                    break;
                case "-pdf":
                    if (i + 1 < args.length) pdfPath = args[++i];
                    break;
                case "-json":
                    if (i + 1 < args.length) jsonPath = args[++i];
                    break;
            }
        }

        // Validation
        if (targets != null && autoDiscovery) {
            System.err.println("Error: -t and -a are mutually exclusive.");
            return;
        }
        if (targets == null && !autoDiscovery) {
            System.err.println("Error: Either -t or -a must be specified.");
            return;
        }
        if (community == null) {
            System.err.println("Error: -c <community> is mandatory for scanning.");
            return;
        }

        NetworkController controller = new NetworkController();
        System.out.println("Starting network scan...");

        if (autoDiscovery) {
            controller.autoDiscoverBlocking(community, device -> System.out.println("Discovered: " + device));
        } else {
            controller.scanNetworkBlocking(targets, community, device -> System.out.println("Discovered: " + device));
        }

        Map<String, NetworkDevice> devices = controller.getDiscoveredDevices();
        System.out.println("Scan complete. Found " + devices.size() + " devices.");

        if (devices.isEmpty()) {
            System.out.println("No devices found. Skipping export.");
            controller.shutdown();
            return;
        }

        ExportService exportService = new ExportService();
        NetworkGraph graph = NetworkGraph.buildFromDevices(devices);

        try {
            if (jsonPath != null) {
                System.out.println("Exporting to JSON: " + jsonPath);
                exportService.exportToJSON(new File(jsonPath), devices);
            }
            if (pngPath != null) {
                System.out.println("Exporting to PNG: " + pngPath);
                exportService.exportToPNG(new File(pngPath), graph, g2 -> NetworkMapPanel.paintGraph(g2, graph));
            }
            if (pdfPath != null) {
                System.out.println("Exporting to PDF: " + pdfPath);
                exportService.exportToPDF(new File(pdfPath), graph, g2 -> NetworkMapPanel.paintGraph(g2, graph));
            }
        } catch (Exception e) {
            System.err.println("Error during export: " + e.getMessage());
        }

        controller.shutdown();
        System.out.println("Done.");
    }
}
