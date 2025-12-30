package prsa.egosoft.netmapper.gui;

import prsa.egosoft.netmapper.i18n.Messages;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkGraph;
import prsa.egosoft.netmapper.model.NetworkGraph.GraphNode;
import prsa.egosoft.netmapper.model.NetworkGraph.GraphEdge;
import prsa.egosoft.netmapper.service.ExportService;
import prsa.egosoft.netmapper.service.GraphLayoutService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Panel displaying a network topology graph with export and print capabilities.
 */
public class NetworkMapPanel extends JPanel
{
    private NetworkGraph graph;
    private GraphPanel graphPanel;
    private Map<String, NetworkDevice> deviceMap;
    
    private final ExportService exportService;
    private final GraphLayoutService layoutService;
    
    // Componentes que necesitan actualización de idioma
    private JButton pngButton;
    private JButton pdfButton;
    private JButton jsonButton;
    private JButton printButton;
    private JCheckBox physicalViewCheckbox;
    private boolean simplifiedView = true;
    
    public NetworkMapPanel()
    {
        this.exportService = new ExportService();
        this.layoutService = new GraphLayoutService();
        setLayout(new BorderLayout());
        initComponents();
        updateUITexts();
    }
    
    private void initComponents()
    {
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
        
        this.physicalViewCheckbox = new JCheckBox();
        physicalViewCheckbox.setSelected(simplifiedView);
        physicalViewCheckbox.addActionListener(e ->
        {
            simplifiedView = physicalViewCheckbox.isSelected();
            updateMap();
        });
        toolBar.add(physicalViewCheckbox);
        
        add(toolBar, BorderLayout.NORTH);
        add(new JScrollPane(graphPanel), BorderLayout.CENTER);
    }
    
    public void updateUITexts()
    {
        this.pngButton.setText(Messages.getString("button.png"));
        this.pngButton.setToolTipText(Messages.getString("tooltip.png"));
        this.pdfButton.setText(Messages.getString("button.pdf"));
        this.pdfButton.setToolTipText(Messages.getString("tooltip.pdf"));
        this.jsonButton.setText(Messages.getString("button.json"));
        this.jsonButton.setToolTipText(Messages.getString("tooltip.json"));
        this.printButton.setText(Messages.getString("button.print"));
        this.printButton.setToolTipText(Messages.getString("tooltip.print"));
        this.physicalViewCheckbox.setText(Messages.getString("button.physical_view"));
        this.physicalViewCheckbox.setToolTipText(Messages.getString("tooltip.physical_view"));
    }
    
    public void initDevices()
    {
        if(deviceMap == null)
        {
            deviceMap = new HashMap<>();
        }
        else
        {
            deviceMap.clear();
        }
    }
    
    public void addOrUpdateDevice(NetworkDevice device)
    {
        if(deviceMap == null)
        {
            initDevices();
        }
        if(deviceMap.containsKey(device.getIpAddress()))
        {
            deviceMap.replace(device.getIpAddress(), device);
        }
        else
        {
            deviceMap.put(device.getIpAddress(), device);
        }
    }
    
    public void clear()
    {
        initDevices();
        graph = new NetworkGraph();
        graphPanel.setGraph(graph);
        graphPanel.repaint();
    }
    
    public void updateMap()
    {
        if(deviceMap == null)
        {
            initDevices();
        }
        if(deviceMap.isEmpty())
        {
            graph = new NetworkGraph();
        }
        else
        {
            graph = NetworkGraph.buildFromDevices(deviceMap, simplifiedView);
        }
        graphPanel.setGraph(graph);
        graphPanel.repaint();
    }
    
    private void exportToPNG()
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("network_map.png"));
        if(chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            File file = chooser.getSelectedFile();
            try
            {
                exportService.exportToPNG(file, graph, g2 -> graphPanel.paint(g2));
                JOptionPane.showMessageDialog(this, Messages.getString("message.export_success", file.getName()));
            }
            catch(IOException ex)
            {
                JOptionPane.showMessageDialog(this, Messages.getString("message.export_error", ex.getMessage()),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void exportToPDF()
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("network_map.pdf"));
        if(chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            File file = chooser.getSelectedFile();
            try
            {
                exportService.exportToPDF(file, graph, g2 -> graphPanel.paint(g2));
                JOptionPane.showMessageDialog(this, Messages.getString("message.export_success", file.getName()));
            }
            catch(Exception ex)
            {
                JOptionPane.showMessageDialog(this, Messages.getString("message.export_error", ex.getMessage()),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void exportToJSON()
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("network_map.json"));
        if(chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            File file = chooser.getSelectedFile();
            try
            {
                exportService.exportToJSON(file, deviceMap);
                JOptionPane.showMessageDialog(this, Messages.getString("message.export_success", file.getName()));
            }
            catch(Exception ex)
            {
                JOptionPane.showMessageDialog(this, Messages.getString("message.export_error", ex.getMessage()),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void printMap()
    {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("NetMapper - " + Messages.getString("button.map"));
        
        job.setPrintable(new Printable()
        {
            @Override
            public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException
            {
                if(pageIndex > 0)
                {
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
        
        if(job.printDialog())
        {
            try
            {
                job.print();
            }
            catch(PrinterException ex)
            {
                JOptionPane.showMessageDialog(this, Messages.getString("message.export_error", ex.getMessage()),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private class GraphPanel extends JPanel
    {
        private NetworkGraph graph;
        private static final int NODE_RADIUS = 15;
        
        private GraphNode selectedNode = null;
        private Point dragOffset = new Point();
        
        // Multiplicador de escala actual (para transformar coordenadas de ratón)
        private double currentScale = 1.0;
        private double offsetX = 0;
        private double offsetY = 0;
        
        public GraphPanel(NetworkGraph graph)
        {
            this.graph = graph;
            setPreferredSize(new Dimension(800, 600));
            setBackground(Color.WHITE);
            
            MouseAdapter mouseAdapter = new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent e)
                {
                    // Transform mouse coordinates to graph coordinates
                    double gx = (e.getX() - offsetX) / currentScale;
                    double gy = (e.getY() - offsetY) / currentScale;
                    
                    for(GraphNode node : GraphPanel.this.graph.getNodes())
                    {
                        double dist = Math.sqrt(Math.pow(gx - node.getX(), 2) + Math.pow(gy - node.getY(), 2));
                        if(dist <= NODE_RADIUS)
                        {
                            selectedNode = node;
                            dragOffset.x = (int) (gx - node.getX());
                            dragOffset.y = (int) (gy - node.getY());
                            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                            break;
                        }
                    }
                }
                
                @Override
                public void mouseReleased(MouseEvent e)
                {
                    selectedNode = null;
                    updateCursor(e.getPoint());
                }
                
                @Override
                public void mouseDragged(MouseEvent e)
                {
                    if(selectedNode != null)
                    {
                        double gx = (e.getX() - offsetX) / currentScale;
                        double gy = (e.getY() - offsetY) / currentScale;
                        selectedNode.setX(gx - dragOffset.x);
                        selectedNode.setY(gy - dragOffset.y);
                        repaint();
                    }
                }
                
                @Override
                public void mouseMoved(MouseEvent e)
                {
                    updateCursor(e.getPoint());
                }
                
                private void updateCursor(Point p)
                {
                    if(selectedNode != null)
                    {
                        return;
                    }
                    
                    double gx = (p.getX() - offsetX) / currentScale;
                    double gy = (p.getY() - offsetY) / currentScale;
                    
                    boolean overNode = false;
                    for(GraphNode node : GraphPanel.this.graph.getNodes())
                    {
                        double dist = Math.sqrt(Math.pow(gx - node.getX(), 2) + Math.pow(gy - node.getY(), 2));
                        if(dist <= NODE_RADIUS)
                        {
                            overNode = true;
                            break;
                        }
                    }
                    if(overNode)
                    {
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    }
                    else
                    {
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
            };
            
            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
        }
        
        public void setGraph(NetworkGraph newGraph)
        {
            Map<String, Point.Double> oldPositions = new HashMap<>();
            if(this.graph != null)
            {
                for(GraphNode node : this.graph.getNodes())
                {
                    oldPositions.put(node.getId(), new Point.Double(node.getX(), node.getY()));
                }
            }
            
            this.graph = newGraph;
            // Layout inicial razonable
            layoutService.calculateLayout(this.graph, getWidth() > 0 ? getWidth() : 800,
                    getHeight() > 0 ? getHeight() : 600);
            
            for(GraphNode node : this.graph.getNodes())
            {
                if(oldPositions.containsKey(node.getId()))
                {
                    Point.Double pos = oldPositions.get(node.getId());
                    if(pos.x != 0 || pos.y != 0)
                    {
                        node.setX(pos.x);
                        node.setY(pos.y);
                    }
                }
            }
        }
        
        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            
            // Calculate scale to fit all
            java.awt.geom.Rectangle2D bounds = graph.calculateBounds(g2);
            double padding = 40;
            double availableWidth = getWidth() - padding * 2;
            double availableHeight = getHeight() - padding * 2;
            
            currentScale = Math.min(availableWidth / bounds.getWidth(), availableHeight / bounds.getHeight());
            if(currentScale > 1.5)
            {
                currentScale = 1.5; // Avoid over-scaling small graphs
            }
            
            offsetX = padding - bounds.getMinX() * currentScale
                    + (availableWidth - bounds.getWidth() * currentScale) / 2;
            offsetY = padding - bounds.getMinY() * currentScale
                    + (availableHeight - bounds.getHeight() * currentScale) / 2;
            
            g2.translate(offsetX, offsetY);
            g2.scale(currentScale, currentScale);
            
            drawGraph(g2, graph);
        }
    }
    
    /**
     * Common drawing logic for all formats.
     */
    private static void drawGraph(Graphics2D g2, NetworkGraph graph)
    {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw edges
        g2.setColor(Color.GRAY);
        g2.setStroke(new BasicStroke(2));
        g2.setFont(new Font("Arial", Font.PLAIN, 9));
        for(GraphEdge edge : graph.getEdges())
        {
            GraphNode source = findNodeInList(graph.getNodes(), edge.getSourceId());
            GraphNode target = findNodeInList(graph.getNodes(), edge.getTargetId());
            if(source != null && target != null)
            {
                g2.drawLine((int) source.getX(), (int) source.getY(), (int) target.getX(), (int) target.getY());
                
                if(edge.getLabel() != null && !edge.getLabel().isEmpty())
                {
                    int midX = (int) ((source.getX() + target.getX()) / 2);
                    int midY = (int) ((source.getY() + target.getY()) / 2);
                    
                    g2.setColor(new Color(100, 100, 100, 180));
                    String[] lines = edge.getLabel().split("\n");
                    FontMetrics fm = g2.getFontMetrics();
                    int yOffset = -(lines.length * fm.getHeight()) / 2;
                    for(String line : lines)
                    {
                        int labelWidth = fm.stringWidth(line);
                        g2.drawString(line, midX - labelWidth / 2, midY + yOffset);
                        yOffset += fm.getHeight();
                    }
                    g2.setColor(Color.GRAY);
                }
            }
        }
        
        // Draw nodes
        int radius = 15;
        for(GraphNode node : graph.getNodes())
        {
            Color nodeColor = getNodeColorForType(node);
            String icon = getNodeIconForType(node.getTypeLabel());
            
            g2.setColor(nodeColor);
            Ellipse2D circle = new Ellipse2D.Double(node.getX() - radius, node.getY() - radius, radius * 2, radius * 2);
            g2.fill(circle);
            
            g2.setColor(Color.BLACK);
            g2.draw(circle);
            
            if(!icon.isEmpty())
            {
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
                FontMetrics ifm = g2.getFontMetrics();
                int iconWidth = ifm.stringWidth(icon);
                g2.drawString(icon, (int) (node.getX() - iconWidth / 2), (int) (node.getY() + ifm.getAscent() / 2 - 2));
            }
            
            g2.setFont(new Font("Arial", Font.PLAIN, 10));
            FontMetrics fm = g2.getFontMetrics();
            String[] lines = node.getLabel().split("\n");
            int yOffset = radius + 12;
            for(String line : lines)
            {
                int labelWidth = fm.stringWidth(line);
                g2.drawString(line, (int) (node.getX() - labelWidth / 2), (int) (node.getY() + yOffset));
                yOffset += fm.getHeight();
            }
        }
    }
    
    /**
     * Static helper for painting a graph. HEADLESS.
     */
    public static void paintGraph(Graphics2D g2, NetworkGraph graph, int width, int height)
    {
        // Auto-scale in headless mode too
        java.awt.geom.Rectangle2D bounds = graph.calculateBounds(g2);
        double padding = 50;
        double availableWidth = width - padding * 2;
        double availableHeight = height - padding * 2;
        
        double scale = Math.min(availableWidth / bounds.getWidth(), availableHeight / bounds.getHeight());
        if(scale > 1.5)
        {
            scale = 1.5;
        }
        
        double tx = padding - bounds.getMinX() * scale + (availableWidth - bounds.getWidth() * scale) / 2;
        double ty = padding - bounds.getMinY() * scale + (availableHeight - bounds.getHeight() * scale) / 2;
        
        g2.translate(tx, ty);
        g2.scale(scale, scale);
        
        drawGraph(g2, graph);
    }
    
    // Obsolete helper but kept for signature compatibility if needed
    public static void paintGraph(Graphics2D g2, NetworkGraph graph)
    {
        paintGraph(g2, graph, 1200, 800);
    }
    
    private static NetworkGraph.GraphNode findNodeInList(java.util.List<NetworkGraph.GraphNode> nodes, String id)
    {
        for(NetworkGraph.GraphNode node : nodes)
        {
            if(node.getId().equals(id))
            {
                return node;
            }
        }
        return null;
    }
    
    private static Color getNodeColorForType(NetworkGraph.GraphNode node)
    {
        String type = node.getTypeLabel();
        if(type == null)
        {
            return node.getType() == NetworkGraph.NodeType.DEVICE ? new Color(100, 150, 255) : new Color(150, 255, 150);
        }
        
        type = type.toLowerCase();
        if(type.contains("router"))
        {
            return new Color(255, 165, 0);
        }
        if(type.contains("switch"))
        {
            return new Color(70, 130, 180);
        }
        if(type.contains("firewall"))
        {
            return new Color(220, 20, 60);
        }
        if(type.contains("impresora"))
        {
            return new Color(46, 139, 87);
        }
        if(type.contains("móvil") || type.contains("movil"))
        {
            return new Color(255, 140, 0);
        }
        if(type.contains("servidor"))
        {
            return new Color(138, 43, 226);
        }
        if(type.contains("pc"))
        {
            return new Color(0, 191, 255);
        }
        
        if(node.getType() == NetworkGraph.NodeType.DEVICE)
        {
            return new Color(100, 150, 255);
        }
        else
        {
            return new Color(150, 255, 150);
        }
    }
    
    private static String getNodeIconForType(String type)
    {
        if(type == null)
        {
            return "";
        }
        type = type.toLowerCase();
        if(type.contains("router"))
        {
            return "\uD83C\uDF10";
        }
        if(type.contains("switch"))
        {
            return "\u21C4";
        }
        if(type.contains("firewall"))
        {
            return "\uD83D\uDD25";
        }
        if(type.contains("impresora"))
        {
            return "\uD83D\uDDA8";
        }
        if(type.contains("móvil") || type.contains("movil"))
        {
            return "\uD83D\uDCF1";
        }
        if(type.contains("servidor"))
        {
            return "\uD83D\uDDA5";
        }
        if(type.contains("pc"))
        {
            return "\uD83D\uDCBB";
        }
        return "";
    }
}
