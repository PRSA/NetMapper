package prsa.egosoft.netmapper.gui;

import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkGraph;
import prsa.egosoft.netmapper.model.NetworkGraph.GraphNode;
import prsa.egosoft.netmapper.model.NetworkGraph.GraphEdge;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.Map;

/**
 * Dialog displaying a network topology graph.
 */
public class NetworkMapDialog extends JDialog {
    private NetworkGraph graph;
    private GraphPanel graphPanel;

    public NetworkMapDialog(JFrame parent, Map<String, NetworkDevice> deviceMap) {
        super(parent, "Mapa de Red", true);

        this.graph = NetworkGraph.buildFromDevices(deviceMap);
        this.graphPanel = new GraphPanel(graph);

        setSize(800, 600);
        setLocationRelativeTo(parent);
        add(new JScrollPane(graphPanel));
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
            calculateLayout();

            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    for (GraphNode node : graph.getNodes()) {
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
                    for (GraphNode node : graph.getNodes()) {
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

        private void calculateLayout() {
            // Simple circular layout
            int nodeCount = graph.getNodes().size();
            if (nodeCount == 0)
                return;

            double centerX = 400;
            double centerY = 300;
            double radius = 200;

            for (int i = 0; i < nodeCount; i++) {
                GraphNode node = graph.getNodes().get(i);
                double angle = 2 * Math.PI * i / nodeCount;
                node.setX(centerX + radius * Math.cos(angle));
                node.setY(centerY + radius * Math.sin(angle));
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw edges first
            g2.setColor(Color.GRAY);
            g2.setStroke(new BasicStroke(2));
            g2.setFont(new Font("Arial", Font.PLAIN, 9));
            for (GraphEdge edge : graph.getEdges()) {
                GraphNode source = findNode(edge.getSourceId());
                GraphNode target = findNode(edge.getTargetId());
                if (source != null && target != null) {
                    g2.drawLine((int) source.getX(), (int) source.getY(),
                            (int) target.getX(), (int) target.getY());

                    // Draw edge label if present
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

            // Draw nodes
            for (GraphNode node : graph.getNodes()) {
                // Choose color and icon based on type
                Color nodeColor = getNodeColor(node);
                String icon = getNodeIcon(node.getTypeLabel());

                g2.setColor(nodeColor);
                Ellipse2D circle = new Ellipse2D.Double(
                        node.getX() - NODE_RADIUS,
                        node.getY() - NODE_RADIUS,
                        NODE_RADIUS * 2,
                        NODE_RADIUS * 2);
                g2.fill(circle);

                g2.setColor(Color.BLACK);
                g2.draw(circle);

                // Draw icon
                if (!icon.isEmpty()) {
                    g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
                    FontMetrics ifm = g2.getFontMetrics();
                    int iconWidth = ifm.stringWidth(icon);
                    g2.drawString(icon, (int) (node.getX() - iconWidth / 2),
                            (int) (node.getY() + ifm.getAscent() / 2 - 2));
                }

                // Draw multi-line label
                g2.setFont(new Font("Arial", Font.PLAIN, 10));
                FontMetrics fm = g2.getFontMetrics();
                String[] lines = node.getLabel().split("\n");
                int yOffset = NODE_RADIUS + 15;
                for (String line : lines) {
                    int labelWidth = fm.stringWidth(line);
                    g2.drawString(line,
                            (int) (node.getX() - labelWidth / 2),
                            (int) (node.getY() + yOffset));
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
                return new Color(255, 165, 0); // Orange
            if (type.contains("switch"))
                return new Color(70, 130, 180); // Dark Blue (SteelBlue)
            if (type.contains("firewall"))
                return new Color(220, 20, 60); // Red (Crimson)
            if (type.contains("impresora"))
                return new Color(46, 139, 87); // Green (SeaGreen)
            if (type.contains("móvil") || type.contains("movil"))
                return new Color(255, 140, 0); // DarkOrange
            if (type.contains("servidor"))
                return new Color(138, 43, 226); // Purple (BlueViolet)
            if (type.contains("pc"))
                return new Color(0, 191, 255); // Sky Blue (DeepSkyBlue)

            // Default colors if type not explicitly recognized but known as device/endpoint
            if (node.getType() == NetworkGraph.NodeType.DEVICE) {
                return new Color(100, 150, 255); // Original Light Blue
            } else {
                return new Color(150, 255, 150); // Original Light Green
            }
        }

        private String getNodeIcon(String type) {
            if (type == null)
                return "";
            type = type.toLowerCase();
            if (type.contains("router"))
                return "\uD83C\uDF10"; // 🌐
            if (type.contains("switch"))
                return "\u21C4"; // ⇄
            if (type.contains("firewall"))
                return "\uD83D\uDD25"; // 🔥
            if (type.contains("impresora"))
                return "\uD83D\uDDA8"; // 🖨️
            if (type.contains("móvil") || type.contains("movil"))
                return "\uD83D\uDCF1"; // 📱
            if (type.contains("servidor"))
                return "\uD83D\uDDA5"; // 🖥️
            if (type.contains("pc"))
                return "\uD83D\uDCBB"; // 💻
            return "";
        }
    }
}
