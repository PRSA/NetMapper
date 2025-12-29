package prsa.egosoft.netmapper;

import prsa.egosoft.netmapper.gui.MainWindow;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkGraph;
import prsa.egosoft.netmapper.service.ExportService;
import prsa.egosoft.netmapper.service.NetworkController;
import prsa.egosoft.netmapper.gui.NetworkMapPanel;
import prsa.egosoft.netmapper.i18n.Messages;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;

import javax.swing.SwingUtilities;

public class Main
{
    public static final boolean IS_ADMIN;
    
    static
    {
        boolean isAdmin = false;
        String os = System.getProperty("os.name").toLowerCase();
        
        try
        {
            if(os.contains("win"))
            {
                // En Windows, 'net session' devuelve error (código 1) si no eres admin
                Process process = Runtime.getRuntime().exec("net session");
                isAdmin = process.waitFor() == 0;
            }
            else
            {
                // En Unix (Linux/macOS), el ID de usuario 0 siempre es root
                Process process = Runtime.getRuntime().exec("id -u");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String uid = reader.readLine();
                isAdmin = "0".equals(uid);
            }
        }
        catch(Exception e)
        {
            isAdmin = false;
        }
        
        IS_ADMIN = isAdmin;
    }
    
    public static void main(String[] args)
    {
        if(args.length > 0)
        {
            runHeadless(args);
        }
        else
        {
            SwingUtilities.invokeLater(() ->
            {
                new MainWindow().setVisible(true);
            });
        }
    }
    
    private static void runHeadless(String[] args)
    {
        String community = null;
        String targets = null;
        boolean autoDiscovery = false;
        String pngPath = null;
        String pdfPath = null;
        String jsonPath = null;
        boolean showHelp = false;
        
        for(int i = 0; i < args.length; i++)
        {
            switch(args[i])
            {
                case "-c":
                {
                    if(i + 1 < args.length)
                    {
                        community = args[++i];
                    }
                    break;
                }
                case "-t":
                {
                    if(i + 1 < args.length)
                    {
                        targets = args[++i];
                    }
                    break;
                }
                case "-a":
                {
                    autoDiscovery = true;
                    break;
                }
                case "-png":
                {
                    if(i + 1 < args.length)
                    {
                        pngPath = args[++i];
                    }
                    break;
                }
                case "-pdf":
                {
                    if(i + 1 < args.length)
                    {
                        pdfPath = args[++i];
                    }
                    break;
                }
                case "-json":
                {
                    if(i + 1 < args.length)
                    {
                        jsonPath = args[++i];
                    }
                    break;
                }
                case "-h":
                {
                    showHelp = true;
                    break;
                }
            }
        }
        
        // Validation
        if(showHelp)
        {
            System.out.println(Messages.getString("cli.usage"));
            System.out.println(Messages.getString("cli.options"));
            System.out.println("  -c <community>  " + Messages.getString("cli.opt.community"));
            System.out.println("  -t <targets>    " + Messages.getString("cli.opt.targets"));
            System.out.println("  -a              " + Messages.getString("cli.opt.autodiscover"));
            System.out.println("  -png <path>     " + Messages.getString("cli.opt.png"));
            System.out.println("  -pdf <path>     " + Messages.getString("cli.opt.pdf"));
            System.out.println("  -json <path>    " + Messages.getString("cli.opt.json"));
            System.out.println("  -h              " + Messages.getString("cli.opt.help"));
            return;
        }
        else
        {
            if(targets != null && autoDiscovery)
            {
                System.err.println(Messages.getString("cli.error.exclusive"));
                return;
            }
            if(targets == null && !autoDiscovery)
            {
                System.err.println(Messages.getString("cli.error.missing_target"));
                return;
            }
            if(community == null)
            {
                System.err.println(Messages.getString("cli.error.missing_community"));
                return;
            }
            
            NetworkController controller = new NetworkController();
            System.out.println(Messages.getString("cli.msg.scan_start"));
            
            if(autoDiscovery)
            {
                controller.autoDiscoverBlocking(community,
                        device -> System.out.println(Messages.getString("message.scan_complete", device.toString())));
            }
            else
            {
                controller.scanNetworkBlocking(targets, community,
                        device -> System.out.println(Messages.getString("message.scan_complete", device.toString())));
            }
            
            Map<String, NetworkDevice> devices = controller.getDiscoveredDevices();
            System.out.println(Messages.getString("cli.msg.scan_complete", devices.size()));
            
            if(devices.isEmpty())
            {
                System.out.println(Messages.getString("cli.msg.no_devices"));
                controller.shutdown();
                return;
            }
            
            ExportService exportService = new ExportService();
            NetworkGraph graph = NetworkGraph.buildFromDevices(devices);
            
            try
            {
                if(jsonPath != null)
                {
                    System.out.println(Messages.getString("cli.export.json", jsonPath));
                    exportService.exportToJSON(new File(jsonPath), devices);
                }
                if(pngPath != null)
                {
                    System.out.println(Messages.getString("cli.export.png", pngPath));
                    exportService.exportToPNG(new File(pngPath), graph, g2 -> NetworkMapPanel.paintGraph(g2, graph));
                }
                if(pdfPath != null)
                {
                    System.out.println(Messages.getString("cli.export.pdf", pdfPath));
                    exportService.exportToPDF(new File(pdfPath), graph, g2 -> NetworkMapPanel.paintGraph(g2, graph));
                }
            }
            catch(Exception e)
            {
                System.err.println(Messages.getString("cli.error.export", e.getMessage()));
            }
            
            controller.shutdown();
            System.out.println(Messages.getString("cli.msg.done"));
        }
    }
}
