package prsa.egosoft.netmapper.strategy;

import prsa.egosoft.netmapper.core.SnmpClient;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkInterface;
import prsa.egosoft.netmapper.util.MacVendorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estrategia de descubrimiento mediante tabla ARP (Fast). Útil para identificar
 * dispositivos que no responden a SNMP. Compatible con Linux (/proc/net/arp) y
 * Windows (arp -a).
 */
public class ArpDiscoveryStrategy implements DiscoveryStrategy
{
    private static final Logger logger = LoggerFactory.getLogger(ArpDiscoveryStrategy.class);
    private static final String LINUX_ARP_FILE = "/proc/net/arp";
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    
    @Override
    public boolean isApplicable(String sysDescr, String sysObjectId)
    {
        // Se aplica si no hay información previa o si queremos enriquecer
        return sysDescr == null;
    }
    
    @Override
    public void discover(SnmpClient snmpClient, NetworkDevice device)
    {
        String ip = device.getIpAddress();
        String mac = IS_WINDOWS ? findMacInArpCommand(ip) : findMacInArpLinuxFile(ip);
        
        // Fallback: Si en Linux falla el fichero, podríamos intentar 'arp -n'
        // pero /proc/net/arp es lo estándar.
        
        if(mac != null)
        {
            String vendor = MacVendorUtils.getVendor(mac);
            device.setVendor(vendor);
            
            // Si no tiene interfaces (porque SNMP no ha corrido), creamos una mínima con la
            // MAC
            if(device.getInterfaces().isEmpty())
            {
                NetworkInterface ni = new NetworkInterface(0, "ARP Discovery");
                ni.setMacAddress(mac);
                ni.setIpAddress(ip);
                device.addInterface(ni);
            }
            else
            {
                // Si ya tiene, intentamos asociar la MAC a la interfaz que tiene la IP
                for(NetworkInterface ni : device.getInterfaces())
                {
                    if(ip.equals(ni.getIpAddress()))
                    {
                        ni.setMacAddress(mac);
                        break;
                    }
                }
            }
            logger.debug("ARP discovery exitoso para {}: MAC={}, Vendor={}", ip, mac, vendor);
        }
    }
    
    private String findMacInArpLinuxFile(String ip)
    {
        try(BufferedReader br = new BufferedReader(new FileReader(LINUX_ARP_FILE)))
        {
            String line;
            // Saltar cabecera
            br.readLine();
            while((line = br.readLine()) != null)
            {
                String[] parts = line.split("\\s+");
                if(parts.length >= 4 && parts[0].equals(ip))
                {
                    String mac = parts[3];
                    if(isValidMac(mac))
                    {
                        return normalizeMac(mac);
                    }
                }
            }
        }
        catch(IOException e)
        {
            logger.error("Error reading system ARP table ({})", LINUX_ARP_FILE, e);
        }
        return null;
    }
    
    private String findMacInArpCommand(String ip)
    {
        try
        {
            ProcessBuilder pb = new ProcessBuilder("arp", "-a");
            Process process = pb.start();
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
            {
                String line;
                while((line = reader.readLine()) != null)
                {
                    if(line.contains(ip))
                    {
                        String mac = extractMacFromLine(line);
                        if(mac != null && isValidMac(mac))
                        {
                            return normalizeMac(mac);
                        }
                    }
                }
            }
        }
        catch(IOException e)
        {
            logger.error("Error executing arp -a command", e);
        }
        return null;
    }
    
    private String extractMacFromLine(String line)
    {
        // Regex para dirección MAC (XX:XX:XX:XX:XX:XX o XX-XX-XX-XX-XX-XX)
        Pattern pattern = Pattern.compile("([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}");
        Matcher matcher = pattern.matcher(line);
        if(matcher.find())
        {
            return matcher.group();
        }
        return null;
    }
    
    private boolean isValidMac(String mac)
    {
        return mac != null && !mac.equals("00:00:00:00:00:00") && !mac.equals("00-00-00-00-00-00");
    }
    
    private String normalizeMac(String mac)
    {
        return mac.replace("-", ":").toUpperCase();
    }
}
