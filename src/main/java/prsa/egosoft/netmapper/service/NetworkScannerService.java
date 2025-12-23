package prsa.egosoft.netmapper.service;

import prsa.egosoft.netmapper.Main;
import prsa.egosoft.netmapper.core.SnmpClient;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.strategy.DiscoveryStrategy;
import prsa.egosoft.netmapper.strategy.StandardMibStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import prsa.egosoft.netmapper.util.SubnetUtils; // Import SubnetUtils
import java.util.List;

import prsa.egosoft.netmapper.scan.ArpScanner;
import prsa.egosoft.netmapper.scan.PcapArpScanner;
import java.util.Map;
import java.util.Collections;

/**
 * Servicio principal para coordinar el escaneo de dispositivos.
 */
public class NetworkScannerService
{
    private static final Logger logger = LoggerFactory.getLogger(NetworkScannerService.class);
    private ExecutorService executorService;
    
    public NetworkScannerService()
    {
        this.executorService = Executors.newFixedThreadPool(100); // Aumentado a 100 hilos para escaneos de red rápidos
    }
    
    /**
     * Escanea un rango de red (CIDR o IP única). Usa la interfaz de red por
     * defecto.
     */
    public void scanNetwork(String cidrInput, String community, Consumer<NetworkDevice> onSuccess,
            Consumer<String> onError)
    {
        scanNetwork(cidrInput, community, onSuccess, onError, null);
    }
    
    /**
     * Escanea un rango de red (CIDR o IP única) usando una interfaz de red
     * específica.
     * 
     * @param cidrInput     Red en formato CIDR o IP única
     * @param community     Comunidad SNMP
     * @param onSuccess     Callback para dispositivos descubiertos
     * @param onError       Callback para errores
     * @param interfaceName Nombre de la interfaz de red a usar (null para usar la
     *                      por defecto)
     */
    public void scanNetwork(String cidrInput, String community, Consumer<NetworkDevice> onSuccess,
            Consumer<String> onError, String interfaceName)
    {
        List<String> ips = SubnetUtils.getIpList(cidrInput);
        if(ips.isEmpty())
        {
            onError.accept(prsa.egosoft.netmapper.i18n.Messages.getString("message.error_invalid_format", cidrInput));
            return;
        }
        
        logger.info("Starting network scan: {} IPs detected in range {}", ips.size(), cidrInput);
        
        // 0. Active ARP Scan (Pcap4J)
        Map<String, String> activeArpMap = Collections.emptyMap();
        if(Main.IS_ADMIN)
        {
            try
            {
                ArpScanner scanner = new PcapArpScanner();
                activeArpMap = scanner.scan(ips, interfaceName);
                logger.info("Active ARP Scan detected {} devices.", activeArpMap.size());
            }
            catch(Throwable t)
            {
                logger.warn("Could not perform active ARP scan (Pcap4J): {}", t.getMessage());
            }
        }
        else
        {
            logger.info("Running without privileges. Active ARP scan will be skipped.");
        }
        
        for(String ip : ips)
        {
            String knownMac = activeArpMap.get(ip);
            scanDevice(ip, community, onSuccess, onError, knownMac);
        }
    }
    
    public void scanDevice(String ip, String community, Consumer<NetworkDevice> onSuccess, Consumer<String> onError)
    {
        scanDevice(ip, community, onSuccess, onError, null);
    }
    
    /**
     * Escanea un único dispositivo IP de forma asíncrona.
     */
    public void scanDevice(String ip, String community, Consumer<NetworkDevice> onSuccess, Consumer<String> onError,
            String knownMac)
    {
        executorService.submit(() ->
        {
            SnmpClient client = null;
            try
            {
                // logger.info("Scanning {}", ip); // Reduce log noise for bulk scans
                client = new SnmpClient(community);
                
                // Crear dispositivo
                NetworkDevice device = new NetworkDevice(ip);
                
                // Si tenemos MAC del escaneo activo, la asignamos directamente
                if(knownMac != null)
                {
                    device.setVendor(prsa.egosoft.netmapper.util.MacVendorUtils.getVendor(knownMac));
                    prsa.egosoft.netmapper.model.NetworkInterface ni = new prsa.egosoft.netmapper.model.NetworkInterface(
                            0, "Active ARP");
                    ni.setMacAddress(knownMac);
                    ni.setIpAddress(ip);
                    device.addInterface(ni);
                }
                
                // Cadena de estrategias: ARP primero (rápido), luego SNMP (detallado)
                DiscoveryStrategy arpStrategy = new prsa.egosoft.netmapper.strategy.ArpDiscoveryStrategy();
                DiscoveryStrategy snmpStrategy = new StandardMibStrategy();
                
                // 1. ARP Discovery (rápido)
                if(Main.IS_ADMIN)
                {
                    if(arpStrategy.isApplicable(null, null))
                    {
                        arpStrategy.discover(client, device);
                    }
                }
                
                // 2. SNMP Discovery (detallado)
                // Usamos client para SNMP
                if(snmpStrategy.isApplicable(device.getSysDescr(), device.getSysObjectId()))
                {
                    snmpStrategy.discover(client, device);
                }
                
                // Solo reportar éxito si el dispositivo respondió a algo (ARP o SNMP)
                if(device.getSysDescr() != null || !device.getInterfaces().isEmpty() || device.getVendor() != null)
                {
                    onSuccess.accept(device);
                }
                else
                {
                    logger.debug("Dispositivo {} no respondió ni a ARP ni a SNMP.", ip);
                }
                
            }
            catch(Exception e)
            {
                // En escaneo masivo, es normal que muchas IPs no respondan (timeout).
                // Podríamos filtrar errores de timeout para no saturar la UI, o dejar que la UI
                // decida.
                // Para single scan (scanDevice invocado directamente) queremos ver el error.
                // Para bulk, tal vez solo loguear.
                // Decisión: propagar error. La UI puede ignorar timeouts si quiere.
                logger.error("Error scanning device {}", ip, e);
                onError.accept(
                        prsa.egosoft.netmapper.i18n.Messages.getString("message.error_accessing", ip, e.getMessage()));
            }
            finally
            {
                if(client != null)
                {
                    try
                    {
                        client.stop();
                    }
                    catch(IOException e)
                    {
                        logger.error("Error closing SNMP client", e);
                    }
                }
            }
        });
    }
    
    public void shutdown()
    {
        executorService.shutdown();
    }
}
