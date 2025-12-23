package prsa.egosoft.netmapper.scan;

import java.util.Map;
import java.util.List;

/**
 * Interface for active ARP scanning.
 */
public interface ArpScanner
{
    
    /**
     * Scans the given list of IPs using ARP and returns a map of IP to MAC address.
     * Uses the default network interface.
     */
    Map<String, String> scan(List<String> ips);
    
    /**
     * Scans the given list of IPs using ARP and returns a map of IP to MAC address.
     * Uses the specified network interface.
     * 
     * @param ips           List of IP addresses to scan
     * @param interfaceName Name of the network interface to use (e.g., "eth0",
     *                      "wlan0")
     * @return Map of IP addresses to MAC addresses
     */
    Map<String, String> scan(List<String> ips, String interfaceName);
}
