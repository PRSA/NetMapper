package prsa.egosoft.netmapper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkGraph;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Service to handle exporting network graphs to various formats without
 * requiring a visible GUI.
 */
public class ExportService
{
    
    private final GraphLayoutService layoutService;
    private final ObjectMapper objectMapper;
    
    public ExportService()
    {
        this.layoutService = new GraphLayoutService();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    public void exportToJSON(File file, Map<String, NetworkDevice> deviceMap) throws IOException
    {
        objectMapper.writeValue(file, deviceMap);
    }
    
    public void exportToPNG(File file, NetworkGraph graph, Consumer<Graphics2D> paintCallback) throws IOException
    {
        int width = 1200; // Default width for headless export
        int height = 800; // Default height for headless export
        
        layoutService.calculateLayout(graph, width, height);
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        
        // Background
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        
        paintCallback.accept(g2);
        g2.dispose();
        
        ImageIO.write(image, "png", file);
    }
    
    public void exportToPDF(File file, NetworkGraph graph, Consumer<Graphics2D> paintCallback) throws IOException
    {
        int width = 1200;
        int height = 800;
        
        layoutService.calculateLayout(graph, width, height);
        
        try(PDDocument document = new PDDocument())
        {
            PDRectangle pageSize = new PDRectangle(width, height);
            PDPage page = new PDPage(pageSize);
            document.addPage(page);
            
            PdfBoxGraphics2D pdfBoxGraphics2D = new PdfBoxGraphics2D(document, width, height);
            try
            {
                paintCallback.accept(pdfBoxGraphics2D);
            }
            finally
            {
                pdfBoxGraphics2D.dispose();
            }
            
            try(PDPageContentStream contentStream = new PDPageContentStream(document, page))
            {
                contentStream.drawForm(pdfBoxGraphics2D.getXFormObject());
            }
            
            document.save(file);
        }
    }
}
