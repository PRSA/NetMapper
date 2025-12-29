package prsa.egosoft.netmapper.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkInterfaceInfo;
import prsa.egosoft.netmapper.util.NetworkDiscoveryUtils;
import prsa.egosoft.netmapper.util.SubnetUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Controller to orchestrate network scans and track results. Provides both
 * asynchronous and synchronous (blocking) scan methods.
 */
public class NetworkController
{
    private static final Logger logger = LoggerFactory.getLogger(NetworkController.class);
    private final NetworkScannerService scannerService;
    private final Map<String, NetworkDevice> discoveredDevices;
    
    public NetworkController()
    {
        this.scannerService = new NetworkScannerService();
        this.discoveredDevices = new ConcurrentHashMap<>();
    }
    
    public Map<String, NetworkDevice> getDiscoveredDevices()
    {
        return discoveredDevices;
    }
    
    public void clearResults()
    {
        discoveredDevices.clear();
    }
    
    /**
     * Performs an asynchronous scan using callbacks.
     */
    public void scanNetworkAsync(String target, String community, Consumer<NetworkDevice> onSuccess,
            Consumer<String> onError)
    {
        scannerService.scanNetwork(target, community, device ->
        {
            discoveredDevices.put(device.getIpAddress(), device);
            if(onSuccess != null)
            {
                onSuccess.accept(device);
            }
        }, onError);
    }
    
    /**
     * Performs a synchronous scan, blocking until all targeted IPs have been
     * scanned.
     */
    public void scanNetworkBlocking(String target, String community, Consumer<NetworkDevice> onDeviceFound)
    {
        scanNetworkBlocking(target, community, onDeviceFound, null);
    }
    
    /**
     * Performs a synchronous scan, blocking until all targeted IPs have been
     * scanned, optionally using a local address for SNMP binding.
     */
    public void scanNetworkBlocking(String target, String community, Consumer<NetworkDevice> onDeviceFound,
            String localAddress)
    {
        List<String> ips = SubnetUtils.getIpList(target);
        if(ips.isEmpty())
        {
            logger.error("Invalid target format: {}", target);
            return;
        }
        
        CountDownLatch latch = new CountDownLatch(ips.size());
        
        for(String ip : ips)
        {
            scannerService.scanDevice(ip, community, device ->
            {
                discoveredDevices.put(device.getIpAddress(), device);
                if(onDeviceFound != null)
                {
                    onDeviceFound.accept(device);
                }
                latch.countDown();
            }, error ->
            {
                // Ignore individual errors (timeouts) during bulk scan
                latch.countDown();
            }, null, localAddress);
        }
        
        try
        {
            // Wait with a generous timeout based on the number of IPs
            // 100 threads in scannerService, 5s timeout per device normally
            long timeout = Math.max(30, (ips.size() / 100) * 10 + 30);
            if(!latch.await(timeout, TimeUnit.SECONDS))
            {
                logger.warn("Scan timed out after {} seconds. Some devices might be missing.", timeout);
            }
        }
        catch(InterruptedException e)
        {
            Thread.currentThread().interrupt();
            logger.error("Scan interrupted", e);
        }
    }
    
    /**
     * Performs automatic discovery of local networks and scans them.
     */
    public void autoDiscoverBlocking(String community, Consumer<NetworkDevice> onDeviceFound)
    {
        List<NetworkInterfaceInfo> networks = NetworkDiscoveryUtils.discoverLocalNetworksWithInterfaces();
        if(networks.isEmpty())
        {
            logger.warn("No local networks found for auto-discovery.");
            return;
        }
        
        for(NetworkInterfaceInfo netInfo : networks)
        {
            logger.info("Auto-discovering network: {} on {} (Local IP: {})", netInfo.getCidr(),
                    netInfo.getInterfaceDisplayName(), netInfo.getLocalIp());
            scanNetworkBlocking(netInfo.getCidr(), community, onDeviceFound, netInfo.getLocalIp());
        }
    }
    
    public void shutdown()
    {
        scannerService.shutdown();
    }
}
