package prsa.egosoft.netmapper.scan;

import java.util.Map;
import java.util.List;

/**
 * Interface for active ARP scanning.
 */
public interface ArpScanner {

    /**
     * Scans a list of IPs by sending ARP requests.
     * 
     * @param ips List of IP addresses to scan.
     * @return Map of IP address to MAC address for detected devices.
     */
    Map<String, String> scan(List<String> ips);
}
