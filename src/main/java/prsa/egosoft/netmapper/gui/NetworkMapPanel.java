package prsa.egosoft.netmapper.gui;

import prsa.egosoft.netmapper.i18n.Messages;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkGraph;
import prsa.egosoft.netmapper.model.NetworkGraph.GraphNode;
import prsa.egosoft.netmapper.model.NetworkGraph.GraphEdge;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;

/**
 * Panel displaying a network topology graph with export and print capabilities.
 */
public class NetworkMapPanel extends JPanel {
    private NetworkGraph graph;
    private GraphPanel graphPanel;
    private Map<String, NetworkDevice> deviceMap;

    // Componentes que necesitan actualización de idioma
    private JButton pngButton;
    private JButton pdfButton;
    private JButton jsonButton;
    private JButton printButton;

    public NetworkMapPanel() {
        setLayout(new BorderLayout());
        initComponents();
        updateUITexts();
    }

    private void initComponents() {
        this.graph = new NetworkGraph(); // Empty initial graph
        this.graphPanel = new GraphPanel(graph);

        // Toolbar for Actions
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        this.pngButton = new JButton();
        pngButton.addActionListener(e -> exportToPNG());
        toolBar.add(pngButton);

        this.pdfButton = new JButton();
        pdfButton.addActionListener(e -> exportToPDF());
        toolBar.add(pdfButton);

        this.jsonButton = new JButton();
        jsonButton.addActionListener(e -> exportToJSON());
        toolBar.add(jsonButton);

        toolBar.addSeparator();

        this.printButton = new JButton();
        printButton.addActionListener(e -> printMap());
        toolBar.add(printButton);

        add(toolBar, BorderLayout.NORTH);
        add(new JScrollPane(graphPanel), BorderLayout.CENTER);
    }

    public void updateUITexts() {
        this.pngButton.setText(Messages.getString("button.png"));
        this.pdfButton.setText(Messages.getString("button.pdf"));
        this.jsonButton.setText(Messages.getString("button.json"));
        this.printButton.setText(Messages.getString("button.print"));
    }

    public void initDevices() {
        if (deviceMap == null)
            deviceMap = new HashMap<>();
        else
            deviceMap.clear();
    }

    public void addOrUpdateDevice(NetworkDevice device) {
        if (deviceMap == null)
            initDevices();
        if (deviceMap.containsKey(device.getIpAddress()))
            deviceMap.replace(device.getIpAddress(), device);
        else
            deviceMap.put(device.getIpAddress(), device);
    }

    public void clear() {
        initDevices();
        graph = new NetworkGraph();
        graphPanel.setGraph(graph);
        graphPanel.repaint();
    }

    public void updateMap() {
        if (deviceMap == null)
            initDevices();
        if (deviceMap.isEmpty()) {
            graph = new NetworkGraph();
        } else {
            graph = NetworkGraph.buildFromDevices(deviceMap);
        }
        graphPanel.setGraph(graph);
        graphPanel.repaint();
    }

    private void exportToPNG() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("network_map.png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            BufferedImage image = new BufferedImage(graphPanel.getWidth(), graphPanel.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            graphPanel.paint(g2);
            g2.dispose();
            try {
                ImageIO.write(image, "png", file);
                JOptionPane.showMessageDialog(this, Messages.getString("message.export_success", file.getName()));
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, Messages.getString("message.export_error", ex.getMessage()),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportToPDF() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("network_map.pdf"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (PDDocument document = new PDDocument()) {
                float width = graphPanel.getWidth();
                float height = graphPanel.getHeight();
                PDRectangle pageSize = new PDRectangle(width, height);
                PDPage page = new PDPage(pageSize);
                document.addPage(page);

                PdfBoxGraphics2D pdfBoxGraphics2D = new PdfBoxGraphics2D(document, width, height);
                try {
                    graphPanel.paint(pdfBoxGraphics2D);
                } finally {
                    pdfBoxGraphics2D.dispose();
                }

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.drawForm(pdfBoxGraphics2D.getXFormObject());
                }

                document.save(file);
                JOptionPane.showMessageDialog(this, Messages.getString("message.export_success", file.getName()));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, Messages.getString("message.export_error", ex.getMessage()),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportToJSON() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("network_map.json"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            try {
                objectMapper.writeValue(file, deviceMap);
                JOptionPane.showMessageDialog(this, Messages.getString("message.export_success", file.getName()));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, Messages.getString("message.export_error", ex.getMessage()),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void printMap() {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("NetMapper - " + Messages.getString("button.map"));

        job.setPrintable(new Printable() {
            @Override
            public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
                if (pageIndex > 0) {
                    return NO_SUCH_PAGE;
                }

                Graphics2D g2d = (Graphics2D) graphics;
                g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

                double scaleX = pageFormat.getImageableWidth() / graphPanel.getWidth();
                double scaleY = pageFormat.getImageableHeight() / graphPanel.getHeight();
                double scale = Math.min(scaleX, scaleY);
                g2d.scale(scale, scale);

                graphPanel.paint(g2d);

                return PAGE_EXISTS;
            }
        });

        if (job.printDialog()) {
            try {
                job.print();
            } catch (PrinterException ex) {
                JOptionPane.showMessageDialog(this, Messages.getString("message.export_error", ex.getMessage()),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private class GraphPanel extends JPanel {
        private NetworkGraph graph;
        private static final int NODE_RADIUS = 30;

        private GraphNode selectedNode = null;
        private Point dragOffset = new Point();

        public GraphPanel(NetworkGraph graph) {
            this.graph = graph;
            setPreferredSize(new Dimension(800, 600));
            setBackground(Color.WHITE);
            // No initial calculateLayout here, will be called by first updateMap

            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    for (GraphNode node : GraphPanel.this.graph.getNodes()) {
                        double dist = Math
                                .sqrt(Math.pow(e.getX() - node.getX(), 2) + Math.pow(e.getY() - node.getY(), 2));
                        if (dist <= NODE_RADIUS) {
                            selectedNode = node;
                            dragOffset.x = (int) (e.getX() - node.getX());
                            dragOffset.y = (int) (e.getY() - node.getY());
                            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                            break;
                        }
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    selectedNode = null;
                    updateCursor(e.getPoint());
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (selectedNode != null) {
                        selectedNode.setX(e.getX() - dragOffset.x);
                        selectedNode.setY(e.getY() - dragOffset.y);
                        repaint();
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    updateCursor(e.getPoint());
                }

                private void updateCursor(Point p) {
                    if (selectedNode != null)
                        return;

                    boolean overNode = false;
                    for (GraphNode node : GraphPanel.this.graph.getNodes()) {
                        double dist = Math
                                .sqrt(Math.pow(p.getX() - node.getX(), 2) + Math.pow(p.getY() - node.getY(), 2));
                        if (dist <= NODE_RADIUS) {
                            overNode = true;
                            break;
                        }
                    }
                    if (overNode) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    } else {
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
            };

            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
        }

        public void setGraph(NetworkGraph newGraph) {
            // Save current positions to preserve them if nodes still exist
            Map<String, Point.Double> oldPositions = new HashMap<>();
            if (this.graph != null) {
                for (GraphNode node : this.graph.getNodes()) {
                    oldPositions.put(node.getId(), new Point.Double(node.getX(), node.getY()));
                }
            }

            this.graph = newGraph;
            calculateLayout();

            // Restore positions for existing nodes
            for (GraphNode node : this.graph.getNodes()) {
                if (oldPositions.containsKey(node.getId())) {
                    Point.Double pos = oldPositions.get(node.getId());
                    if (pos.x != 0 || pos.y != 0) {
                        node.setX(pos.x);
                        node.setY(pos.y);
                    }
                }
            }
        }

        private void calculateLayout() {
            List<GraphNode> devices = new ArrayList<>();
            List<GraphNode> endpoints = new ArrayList<>();

            for (GraphNode node : graph.getNodes()) {
                if (node.getType() == NetworkGraph.NodeType.DEVICE) {
                    devices.add(node);
                } else {
                    endpoints.add(node);
                }
            }

            if (devices.isEmpty() && endpoints.isEmpty())
                return;

            double centerX = getWidth() / 2.0;
            double centerY = getHeight() / 2.0;
            if (centerX <= 0)
                centerX = 400;
            if (centerY <= 0)
                centerY = 300;

            double deviceRadius = Math.min(centerX, centerY) * 0.7;
            if (devices.size() == 1) {
                devices.get(0).setX(centerX);
                devices.get(0).setY(centerY);
            } else {
                for (int i = 0; i < devices.size(); i++) {
                    GraphNode device = devices.get(i);
                    double angle = 2 * Math.PI * i / devices.size();
                    device.setX(centerX + deviceRadius * Math.cos(angle));
                    device.setY(centerY + deviceRadius * Math.sin(angle));
                }
            }

            Map<String, List<GraphNode>> deviceToEndpoints = new HashMap<>();
            for (GraphNode device : devices) {
                deviceToEndpoints.put(device.getId(), new ArrayList<>());
            }

            List<GraphNode> orphanEndpoints = new ArrayList<>();
            for (GraphNode endpoint : endpoints) {
                boolean connected = false;
                for (NetworkGraph.GraphEdge edge : graph.getEdges()) {
                    if (edge.getTargetId().equals(endpoint.getId())) {
                        if (deviceToEndpoints.containsKey(edge.getSourceId())) {
                            deviceToEndpoints.get(edge.getSourceId()).add(endpoint);
                            connected = true;
                            break;
                        }
                    } else if (edge.getSourceId().equals(endpoint.getId())) {
                        if (deviceToEndpoints.containsKey(edge.getTargetId())) {
                            deviceToEndpoints.get(edge.getTargetId()).add(endpoint);
                            connected = true;
                            break;
                        }
                    }
                }
                if (!connected) {
                    orphanEndpoints.add(endpoint);
                }
            }

            double endpointOrbitRadius = 100;
            for (GraphNode device : devices) {
                List<GraphNode> children = deviceToEndpoints.get(device.getId());
                int count = children.size();
                if (count == 0)
                    continue;

                for (int i = 0; i < count; i++) {
                    GraphNode child = children.get(i);
                    double angle = 2 * Math.PI * i / count;
                    child.setX(device.getX() + endpointOrbitRadius * Math.cos(angle));
                    child.setY(device.getY() + endpointOrbitRadius * Math.sin(angle));
                }
            }

            if (!orphanEndpoints.isEmpty()) {
                double orphanRadius = 50;
                for (int i = 0; i < orphanEndpoints.size(); i++) {
                    GraphNode orphan = orphanEndpoints.get(i);
                    double angle = 2 * Math.PI * i / orphanEndpoints.size();
                    orphan.setX(centerX + orphanRadius * Math.cos(angle));
                    orphan.setY(centerY + orphanRadius * Math.sin(angle));
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(Color.GRAY);
            g2.setStroke(new BasicStroke(2));
            g2.setFont(new Font("Arial", Font.PLAIN, 9));
            for (GraphEdge edge : graph.getEdges()) {
                GraphNode source = findNode(edge.getSourceId());
                GraphNode target = findNode(edge.getTargetId());
                if (source != null && target != null) {
                    g2.drawLine((int) source.getX(), (int) source.getY(), (int) target.getX(), (int) target.getY());

                    if (edge.getLabel() != null && !edge.getLabel().isEmpty()) {
                        int midX = (int) ((source.getX() + target.getX()) / 2);
                        int midY = (int) ((source.getY() + target.getY()) / 2);

                        g2.setColor(new Color(100, 100, 100, 180));
                        String[] lines = edge.getLabel().split("\n");
                        FontMetrics fm = g2.getFontMetrics();
                        int yOffset = -(lines.length * fm.getHeight()) / 2;
                        for (String line : lines) {
                            int labelWidth = fm.stringWidth(line);
                            g2.drawString(line, midX - labelWidth / 2, midY + yOffset);
                            yOffset += fm.getHeight();
                        }
                    }
                }
            }

            for (GraphNode node : graph.getNodes()) {
                Color nodeColor = getNodeColor(node);
                String icon = getNodeIcon(node.getTypeLabel());

                g2.setColor(nodeColor);
                Ellipse2D circle = new Ellipse2D.Double(node.getX() - NODE_RADIUS, node.getY() - NODE_RADIUS,
                        NODE_RADIUS * 2, NODE_RADIUS * 2);
                g2.fill(circle);

                g2.setColor(Color.BLACK);
                g2.draw(circle);

                if (!icon.isEmpty()) {
                    g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
                    FontMetrics ifm = g2.getFontMetrics();
                    int iconWidth = ifm.stringWidth(icon);
                    g2.drawString(icon, (int) (node.getX() - iconWidth / 2),
                            (int) (node.getY() + ifm.getAscent() / 2 - 2));
                }

                g2.setFont(new Font("Arial", Font.PLAIN, 10));
                FontMetrics fm = g2.getFontMetrics();
                String[] lines = node.getLabel().split("\n");
                int yOffset = NODE_RADIUS + 15;
                for (String line : lines) {
                    int labelWidth = fm.stringWidth(line);
                    g2.drawString(line, (int) (node.getX() - labelWidth / 2), (int) (node.getY() + yOffset));
                    yOffset += fm.getHeight();
                }
            }
        }

        private GraphNode findNode(String id) {
            for (GraphNode node : graph.getNodes()) {
                if (node.getId().equals(id)) {
                    return node;
                }
            }
            return null;
        }

        private Color getNodeColor(GraphNode node) {
            String type = node.getTypeLabel();
            if (type == null)
                return node.getType() == NetworkGraph.NodeType.DEVICE ? new Color(100, 150, 255)
                        : new Color(150, 255, 150);

            type = type.toLowerCase();
            if (type.contains("router"))
                return new Color(255, 165, 0);
            if (type.contains("switch"))
                return new Color(70, 130, 180);
            if (type.contains("firewall"))
                return new Color(220, 20, 60);
            if (type.contains("impresora"))
                return new Color(46, 139, 87);
            if (type.contains("móvil") || type.contains("movil"))
                return new Color(255, 140, 0);
            if (type.contains("servidor"))
                return new Color(138, 43, 226);
            if (type.contains("pc"))
                return new Color(0, 191, 255);

            if (node.getType() == NetworkGraph.NodeType.DEVICE) {
                return new Color(100, 150, 255);
            } else {
                return new Color(150, 255, 150);
            }
        }

        private String getNodeIcon(String type) {
            if (type == null)
                return "";
            type = type.toLowerCase();
            if (type.contains("router"))
                return "\uD83C\uDF10";
            if (type.contains("switch"))
                return "\u21C4";
            if (type.contains("firewall"))
                return "\uD83D\uDD25";
            if (type.contains("impresora"))
                return "\uD83D\uDDA8";
            if (type.contains("móvil") || type.contains("movil"))
                return "\uD83D\uDCF1";
            if (type.contains("servidor"))
                return "\uD83D\uDDA5";
            if (type.contains("pc"))
                return "\uD83D\uDCBB";
            return "";
        }
    }
}
