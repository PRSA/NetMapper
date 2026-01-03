package prsa.egosoft.netmapper.gui;

import prsa.egosoft.netmapper.i18n.Messages;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkGraph;
import prsa.egosoft.netmapper.model.NetworkGraph.GraphNode;
import prsa.egosoft.netmapper.service.ExportService;
import prsa.egosoft.netmapper.service.GraphLayoutService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Panel displaying a network topology graph with export and print capabilities,
 * now with Zoom and Pan support.
 */
public class NetworkMapPanel extends JPanel {
    private NetworkGraph graph;
    private GraphPanel graphPanel;
    private Map<String, NetworkDevice> deviceMap;

    private final ExportService exportService;
    private final GraphLayoutService layoutService;

    // UI Components
    private JButton pngButton;
    private JButton pdfButton;
    private JButton jsonButton;
    private JButton printButton;

    // Zoom/Pan controls
    private JButton zoomInButton;
    private JButton zoomOutButton;
    private JButton fitButton;
    private JButton filterButton;

    private Map<String, Boolean> nodeTypeFilters = new HashMap<>();
    private java.util.EnumMap<NetworkGraph.EdgeType, Boolean> edgeTypeFilters = new java.util.EnumMap<>(
            NetworkGraph.EdgeType.class);
    private JPopupMenu filterMenu;

    public NetworkMapPanel() {
        this.exportService = new ExportService();
        this.layoutService = new GraphLayoutService();
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

        toolBar.addSeparator();

        // Zoom Controls
        this.zoomInButton = new JButton();
        zoomInButton.addActionListener(e -> graphPanel.zoomIn());
        toolBar.add(zoomInButton);

        this.zoomOutButton = new JButton();
        zoomOutButton.addActionListener(e -> graphPanel.zoomOut());
        toolBar.add(zoomOutButton);

        fitButton = new JButton();
        fitButton.addActionListener(e -> graphPanel.fitToScreen());
        toolBar.add(fitButton);

        filterButton = new JButton();
        filterButton.addActionListener(e -> {
            updateFilterMenu();
            filterMenu.show(filterButton, 0, filterButton.getHeight());
        });
        toolBar.add(filterButton);

        add(toolBar, BorderLayout.NORTH);
        add(new JScrollPane(graphPanel), BorderLayout.CENTER);
    }

    public void updateUITexts() {
        this.pngButton.setText(Messages.getString("button.png"));
        this.pngButton.setToolTipText(Messages.getString("tooltip.png"));
        this.pdfButton.setText(Messages.getString("button.pdf"));
        this.pdfButton.setToolTipText(Messages.getString("tooltip.pdf"));
        this.jsonButton.setText(Messages.getString("button.json"));
        this.jsonButton.setToolTipText(Messages.getString("tooltip.json"));
        this.printButton.setText(Messages.getString("button.print"));
        this.printButton.setToolTipText(Messages.getString("tooltip.print"));

        this.zoomInButton.setText(Messages.getString("button.zoom_in"));
        this.zoomInButton.setToolTipText(Messages.getString("tooltip.zoom_in"));
        this.zoomOutButton.setText(Messages.getString("button.zoom_out"));
        this.zoomOutButton.setToolTipText(Messages.getString("tooltip.zoom_out"));
        this.fitButton.setText(Messages.getString("button.fit"));
        this.fitButton.setToolTipText(Messages.getString("tooltip.fit"));
        this.filterButton.setText(Messages.getString("button.filters"));
        this.filterButton.setToolTipText(Messages.getString("tooltip.filters"));
    }

    public void initDevices() {
        if (deviceMap == null) {
            deviceMap = new HashMap<>();
        } else {
            deviceMap.clear();
        }
    }

    public void addOrUpdateDevice(NetworkDevice device) {
        if (deviceMap == null) {
            initDevices();
        }
        if (deviceMap.containsKey(device.getIpAddress())) {
            deviceMap.replace(device.getIpAddress(), device);
        } else {
            deviceMap.put(device.getIpAddress(), device);
        }
    }

    public void clear() {
        initDevices();
        graph = new NetworkGraph();
        graphPanel.setGraph(graph);
        graphPanel.repaint();
    }

    public void updateMap() {
        if (deviceMap == null || deviceMap.isEmpty()) {
            return;
        }
        // Build graph always identifying logical links (second param = true)
        this.graph = NetworkGraph.buildFromDevices(deviceMap, true);

        // Apply layout
        layoutService.calculateLayout(graph);

        graphPanel.setGraph(graph);
    }

    private void updateFilterMenu() {
        if (filterMenu == null) {
            filterMenu = new JPopupMenu();
        }
        filterMenu.removeAll();

        // Ensure default filters exist
        if (edgeTypeFilters.isEmpty()) {
            edgeTypeFilters.put(NetworkGraph.EdgeType.PHYSICAL, true);
            edgeTypeFilters.put(NetworkGraph.EdgeType.LOGICAL, true);
        }
        if (nodeTypeFilters.isEmpty()) {
            nodeTypeFilters.put(NetworkGraph.NodeType.DEVICE.name(), true);
            nodeTypeFilters.put(NetworkGraph.NodeType.ENDPOINT.name(), true);
        }

        // Link Types Section
        filterMenu.add(new JLabel(" " + Messages.getString("filter.link_types")));
        addFilterItem(filterMenu, Messages.getString("filter.links_physical"), NetworkGraph.EdgeType.PHYSICAL);
        addFilterItem(filterMenu, Messages.getString("filter.links_logical"), NetworkGraph.EdgeType.LOGICAL);

        filterMenu.addSeparator();

        // Node Types Section
        filterMenu.add(new JLabel(" " + Messages.getString("filter.node_types")));
        addFilterItem(filterMenu, Messages.getString("filter.nodes_devices"), NetworkGraph.NodeType.DEVICE.name());
        addFilterItem(filterMenu, Messages.getString("filter.nodes_endpoints"), NetworkGraph.NodeType.ENDPOINT.name());

        // Device Sub-types (Dynamic)
        if (graph != null) {
            java.util.Set<String> subTypes = new java.util.TreeSet<>();
            for (NetworkGraph.GraphNode node : graph.getNodes()) {
                if (node.getType() == NetworkGraph.NodeType.DEVICE && node.getTypeLabel() != null) {
                    subTypes.add(node.getTypeLabel());
                }
            }
            if (!subTypes.isEmpty()) {
                JMenu subMenu = new JMenu(Messages.getString("filter.node_types") + "...");
                for (String subType : subTypes) {
                    addFilterItem(subMenu, subType, "SUBTYPE:" + subType);
                }
                filterMenu.add(subMenu);
            }
        }
    }

    private void addFilterItem(Container container, String label, Object filterKey) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(label);
        String key = filterKey.toString();

        if (filterKey instanceof NetworkGraph.EdgeType) {
            item.setSelected(edgeTypeFilters.getOrDefault(filterKey, true));
            item.addActionListener(e -> {
                edgeTypeFilters.put((NetworkGraph.EdgeType) filterKey, item.isSelected());
                graphPanel.repaint();
            });
        } else {
            item.setSelected(nodeTypeFilters.getOrDefault(key, true));
            item.addActionListener(e -> {
                nodeTypeFilters.put(key, item.isSelected());
                graphPanel.repaint();
            });
        }
        container.add(item);
    }

    private java.util.function.Predicate<NetworkGraph.GraphNode> getNodeFilter() {
        return node -> {
            // Check main category
            if (!nodeTypeFilters.getOrDefault(node.getType().name(), true)) {
                return false;
            }
            // Check specific device type if it's a device
            if (node.getType() == NetworkGraph.NodeType.DEVICE) {
                return nodeTypeFilters.getOrDefault("SUBTYPE:" + node.getTypeLabel(), true);
            }
            return true;
        };
    }

    private java.util.function.Predicate<NetworkGraph.GraphEdge> getEdgeFilter() {
        return edge -> edgeTypeFilters.getOrDefault(edge.getType(), true);
    }

    private void exportToPNG() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("network_map.png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                // Export uses the panel's drawing logic but typically wants to capture the
                // whole graph,
                // perhaps strictly fitting it to the image size.
                exportService.exportToPNG(file, graph, g2 -> NetworkMapPanel.paintGraph(g2, graph,
                        graph.calculateBounds(g2).getBounds().width + 100,
                        graph.calculateBounds(g2).getBounds().height + 100));

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
            try {
                exportService.exportToPDF(file, graph, g2 -> NetworkMapPanel.paintGraph(g2, graph, 800, 600));
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
            try {
                exportService.exportToJSON(file, deviceMap);
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

                double imgWidth = pageFormat.getImageableWidth();
                double imgHeight = pageFormat.getImageableHeight();

                NetworkMapPanel.paintGraph(g2d, graph, (int) imgWidth, (int) imgHeight);

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

    /**
     * Inner class that handles the actual graph rendering and interaction.
     */
    private class GraphPanel extends JPanel {
        private NetworkGraph graph;
        private static final int NODE_RADIUS = 15;

        private GraphNode selectedNode = null;
        private Point dragStartPoint = null; // For panning

        // Offset relative to the selected node when dragging
        private Point nodeDragOffset = new Point();

        // Transform state
        private double currentScale = 1.0;
        private double offsetX = 0;
        private double offsetY = 0;

        // Flag to force auto-fit on next paint (e.g., when a new graph is loaded)
        private boolean forceFit = true;

        public GraphPanel(NetworkGraph graph) {
            this.graph = graph;
            setPreferredSize(new Dimension(800, 600));
            setBackground(Color.WHITE);

            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    // Check if we clicked on a node
                    double gx = (e.getX() - offsetX) / currentScale;
                    double gy = (e.getY() - offsetY) / currentScale;

                    for (GraphNode node : GraphPanel.this.graph.getNodes()) {
                        double dist = Math.sqrt(Math.pow(gx - node.getX(), 2) + Math.pow(gy - node.getY(), 2));
                        if (dist <= NODE_RADIUS) {
                            selectedNode = node;
                            nodeDragOffset.x = (int) (gx - node.getX());
                            nodeDragOffset.y = (int) (gy - node.getY());
                            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                            return; // Found a node, stop checking
                        }
                    }

                    // If no node clicked, start panning
                    dragStartPoint = e.getPoint();
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    selectedNode = null;
                    dragStartPoint = null;
                    updateCursor(e.getPoint());
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (selectedNode != null) {
                        // Dragging a node
                        double gx = (e.getX() - offsetX) / currentScale;
                        double gy = (e.getY() - offsetY) / currentScale;
                        selectedNode.setX(gx - nodeDragOffset.x);
                        selectedNode.setY(gy - nodeDragOffset.y);
                        repaint();
                    } else if (dragStartPoint != null) {
                        // Panning the canvas
                        int dx = e.getX() - dragStartPoint.x;
                        int dy = e.getY() - dragStartPoint.y;

                        offsetX += dx;
                        offsetY += dy;

                        dragStartPoint = e.getPoint(); // Reset start point to current for continuous drag
                        repaint();
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    updateCursor(e.getPoint());
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    // Zoom logic
                    if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
                        double zoomFactor = 0.1;
                        double scaleChange = (e.getWheelRotation() < 0) ? (1 + zoomFactor) : (1 - zoomFactor);

                        applyZoom(scaleChange, e.getPoint());
                    }
                }

                private void updateCursor(Point p) {
                    if (selectedNode != null || dragStartPoint != null) {
                        return;
                    }

                    double gx = (p.getX() - offsetX) / currentScale;
                    double gy = (p.getY() - offsetY) / currentScale;

                    boolean overNode = false;
                    for (GraphNode node : GraphPanel.this.graph.getNodes()) {
                        double dist = Math.sqrt(Math.pow(gx - node.getX(), 2) + Math.pow(gy - node.getY(), 2));
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
            addMouseWheelListener(mouseAdapter);
        }

        private void applyZoom(double factor, Point center) {
            double oldScale = currentScale;
            currentScale *= factor;

            // Cap the zoom levels
            if (currentScale < 0.1)
                currentScale = 0.1;
            if (currentScale > 5.0)
                currentScale = 5.0;

            // Adjust offset to keep mouse pointer fixed relative to graph
            if (center != null) {
                // (mouseX - offsetX) / oldScale = graphX
                // (mouseX - newOffsetX) / newScale = graphX
                // => mouseX - newOffsetX = graphX * newScale
                // => newOffsetX = mouseX - graphX * newScale

                double graphX = (center.x - offsetX) / oldScale;
                double graphY = (center.y - offsetY) / oldScale;

                offsetX = center.x - graphX * currentScale;
                offsetY = center.y - graphY * currentScale;
            } else {
                // If zoom center is not provided (e.g. button click), zoom to center of panel
                double centerX = getWidth() / 2.0;
                double centerY = getHeight() / 2.0;

                double graphX = (centerX - offsetX) / oldScale;
                double graphY = (centerY - offsetY) / oldScale;

                offsetX = centerX - graphX * currentScale;
                offsetY = centerY - graphY * currentScale;
            }

            repaint();
        }

        public void zoomIn() {
            applyZoom(1.2, null);
        }

        public void zoomOut() {
            applyZoom(0.8, null);
        }

        public void fitToScreen() {
            forceFit = true;
            repaint();
        }

        public void setGraph(NetworkGraph newGraph) {
            Map<String, java.awt.geom.Point2D.Double> oldPositions = new HashMap<>();
            if (this.graph != null) {
                for (GraphNode node : this.graph.getNodes()) {
                    oldPositions.put(node.getId(), new java.awt.geom.Point2D.Double(node.getX(), node.getY()));
                }
            }

            this.graph = newGraph;
            // Layout
            layoutService.calculateLayout(this.graph, getWidth() > 0 ? getWidth() : 800,
                    getHeight() > 0 ? getHeight() : 600);

            for (GraphNode node : this.graph.getNodes()) {
                if (oldPositions.containsKey(node.getId())) {
                    java.awt.geom.Point2D.Double pos = oldPositions.get(node.getId());
                    if (pos.x != 0 || pos.y != 0) {
                        node.setX(pos.x);
                        node.setY(pos.y);
                    }
                }
            }

            // Trigger auto-fit when setting a new graph
            forceFit = true;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            if (forceFit) {
                // Calculate scale to fit all
                java.awt.geom.Rectangle2D bounds = graph.calculateBounds(g2, getNodeFilter(), getEdgeFilter());
                if (bounds.getWidth() > 0 && bounds.getHeight() > 0) {
                    double padding = 40;
                    double availableWidth = getWidth() - padding * 2;
                    double availableHeight = getHeight() - padding * 2;

                    currentScale = Math.min(availableWidth / bounds.getWidth(), availableHeight / bounds.getHeight());
                    if (currentScale > 1.5) {
                        currentScale = 1.5; // Avoid over-scaling small graphs
                    }

                    offsetX = padding - bounds.getMinX() * currentScale
                            + (availableWidth - bounds.getWidth() * currentScale) / 2;
                    offsetY = padding - bounds.getMinY() * currentScale
                            + (availableHeight - bounds.getHeight() * currentScale) / 2;
                } else {
                    // Default values if graph is empty
                    currentScale = 1.0;
                    offsetX = 0;
                    offsetY = 0;
                }
                forceFit = false;
            }

            g2.translate(offsetX, offsetY);
            g2.scale(currentScale, currentScale);

            drawGraph(g2, graph, getNodeFilter(), getEdgeFilter());
        }
    }

    /**
     * Common drawing logic for all formats.
     */
    private static void drawGraph(Graphics2D g2, NetworkGraph graph,
            java.util.function.Predicate<NetworkGraph.GraphNode> nodeFilter,
            java.util.function.Predicate<NetworkGraph.GraphEdge> edgeFilter) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Filter nodes first for optimized edge lookup
        java.util.Set<String> visibleNodeIds = new java.util.HashSet<>();
        java.util.List<NetworkGraph.GraphNode> nodesToDraw = new java.util.ArrayList<>();
        for (NetworkGraph.GraphNode node : graph.getNodes()) {
            if (nodeFilter.test(node)) {
                nodesToDraw.add(node);
                visibleNodeIds.add(node.getId());
            }
        }

        // Draw edges
        g2.setColor(Color.GRAY);
        g2.setStroke(new BasicStroke(2));
        g2.setFont(new Font("Arial", Font.PLAIN, 9));
        for (NetworkGraph.GraphEdge edge : graph.getEdges()) {
            // Apply edge type filter
            if (!edgeFilter.test(edge))
                continue;

            // Edge is only drawn if both ends are visible
            if (visibleNodeIds.contains(edge.getSourceId()) && visibleNodeIds.contains(edge.getTargetId())) {
                NetworkGraph.GraphNode source = findNodeInList(graph.getNodes(), edge.getSourceId());
                NetworkGraph.GraphNode target = findNodeInList(graph.getNodes(), edge.getTargetId());
                if (source != null && target != null) {
                    if (edge.getType() == NetworkGraph.EdgeType.LOGICAL) {
                        // Draw logical links as dashed lines or different color?
                        float[] dash = { 5, 5 };
                        g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dash, 0));
                    } else {
                        g2.setStroke(new BasicStroke(2));
                    }

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
                        g2.setColor(Color.GRAY);
                    }
                }
            }
        }

        // Draw nodes
        int radius = 15;
        for (NetworkGraph.GraphNode node : nodesToDraw) {
            Color nodeColor = getNodeColorForType(node);
            String icon = getNodeIconForType(node.getTypeLabel());

            g2.setColor(nodeColor);
            Ellipse2D circle = new Ellipse2D.Double(node.getX() - radius, node.getY() - radius, radius * 2, radius * 2);
            g2.fill(circle);

            g2.setColor(Color.BLACK);
            g2.draw(circle);

            if (!icon.isEmpty()) {
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
                FontMetrics ifm = g2.getFontMetrics();
                int iconWidth = ifm.stringWidth(icon);
                g2.drawString(icon, (int) (node.getX() - iconWidth / 2), (int) (node.getY() + ifm.getAscent() / 2 - 2));
            }

            g2.setFont(new Font("Arial", Font.PLAIN, 10));
            FontMetrics fm = g2.getFontMetrics();
            String[] lines = node.getLabel().split("\n");
            int yOffset = radius + 12;
            for (String line : lines) {
                int labelWidth = fm.stringWidth(line);
                g2.drawString(line, (int) (node.getX() - labelWidth / 2), (int) (node.getY() + yOffset));
                yOffset += fm.getHeight();
            }
        }
    }

    /**
     * Static helper for painting a graph. HEADLESS.
     */
    public static void paintGraph(Graphics2D g2, NetworkGraph graph, int width, int height) {
        paintGraph(g2, graph, width, height, n -> true, e -> true);
    }

    public static void paintGraph(Graphics2D g2, NetworkGraph graph, int width, int height,
            java.util.function.Predicate<NetworkGraph.GraphNode> nodeFilter,
            java.util.function.Predicate<NetworkGraph.GraphEdge> edgeFilter) {

        java.awt.geom.Rectangle2D bounds = graph.calculateBounds(g2, nodeFilter, edgeFilter);
        double padding = 50;
        double availableWidth = width - padding * 2;
        double availableHeight = height - padding * 2;

        double scale = Math.min(availableWidth / bounds.getWidth(), availableHeight / bounds.getHeight());
        if (scale > 1.5) {
            scale = 1.5;
        }

        double tx = padding - bounds.getMinX() * scale + (availableWidth - bounds.getWidth() * scale) / 2;
        double ty = padding - bounds.getMinY() * scale + (availableHeight - bounds.getHeight() * scale) / 2;

        g2.translate(tx, ty);
        g2.scale(scale, scale);

        drawGraph(g2, graph, nodeFilter, edgeFilter);
    }

    // Obsolete helper but kept for signature compatibility if needed
    public static void paintGraph(Graphics2D g2, NetworkGraph graph) {
        paintGraph(g2, graph, 1200, 800);
    }

    private static NetworkGraph.GraphNode findNodeInList(java.util.List<NetworkGraph.GraphNode> nodes, String id) {
        for (NetworkGraph.GraphNode node : nodes) {
            if (node.getId().equals(id)) {
                return node;
            }
        }
        return null;
    }

    private static Color getNodeColorForType(NetworkGraph.GraphNode node) {
        String type = node.getTypeLabel();
        if (type == null) {
            return node.getType() == NetworkGraph.NodeType.DEVICE ? new Color(100, 150, 255) : new Color(150, 255, 150);
        }

        type = type.toLowerCase();
        if (type.contains("router")) {
            return new Color(255, 165, 0);
        }
        if (type.contains("switch")) {
            return new Color(70, 130, 180);
        }
        if (type.contains("firewall")) {
            return new Color(220, 20, 60);
        }
        if (type.contains("impresora")) {
            return new Color(46, 139, 87);
        }
        if (type.contains("móvil") || type.contains("movil")) {
            return new Color(255, 140, 0);
        }
        if (type.contains("servidor")) {
            return new Color(138, 43, 226);
        }
        if (type.contains("pc")) {
            return new Color(0, 191, 255);
        }

        if (node.getType() == NetworkGraph.NodeType.DEVICE) {
            return new Color(100, 150, 255);
        } else {
            return new Color(150, 255, 150);
        }
    }

    private static String getNodeIconForType(String type) {
        if (type == null) {
            return "";
        }
        type = type.toLowerCase();
        if (type.contains("router")) {
            return "\uD83C\uDF10";
        }
        if (type.contains("switch")) {
            return "\u21C4";
        }
        if (type.contains("firewall")) {
            return "\uD83D\uDD25";
        }
        if (type.contains("impresora")) {
            return "\uD83D\uDDA8";
        }
        if (type.contains("móvil") || type.contains("movil")) {
            return "\uD83D\uDCF1";
        }
        if (type.contains("servidor")) {
            return "\uD83D\uDDA5";
        }
        if (type.contains("pc")) {
            return "\uD83D\uDCBB";
        }
        return "";
    }
}
