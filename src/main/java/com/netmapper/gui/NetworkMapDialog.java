package com.netmapper.gui;

import com.netmapper.model.NetworkDevice;
import com.netmapper.model.NetworkGraph;
import com.netmapper.model.NetworkGraph.GraphNode;
import com.netmapper.model.NetworkGraph.GraphEdge;

import javax.swing.*;
import java.awt.*;
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

        public GraphPanel(NetworkGraph graph) {
            this.graph = graph;
            setPreferredSize(new Dimension(800, 600));
            setBackground(Color.WHITE);
            calculateLayout();
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
                // Choose color based on type
                Color nodeColor = node.getType() == NetworkGraph.NodeType.DEVICE
                        ? new Color(100, 150, 255)
                        : new Color(150, 255, 150);

                g2.setColor(nodeColor);
                Ellipse2D circle = new Ellipse2D.Double(
                        node.getX() - NODE_RADIUS,
                        node.getY() - NODE_RADIUS,
                        NODE_RADIUS * 2,
                        NODE_RADIUS * 2);
                g2.fill(circle);

                g2.setColor(Color.BLACK);
                g2.draw(circle);

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
    }
}
