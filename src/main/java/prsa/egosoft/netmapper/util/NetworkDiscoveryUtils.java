package prsa.egosoft.netmapper.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

import prsa.egosoft.netmapper.model.NetworkInterfaceInfo;

public class NetworkDiscoveryUtils
{
    /**
     * Discovers all local network interfaces and returns a list of unique,
     * non-overlapping CIDR networks with their associated interface information.
     * Prioritizes larger ranges (smaller prefix lengths) when networks overlap or
     * encompass others. If a network is configured on multiple interfaces, only the
     * first one found is returned.
     */
    public static List<NetworkInterfaceInfo> discoverLocalNetworksWithInterfaces()
    {
        List<NetworkInfo> networks = new ArrayList<>();
        
        try
        {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while(interfaces.hasMoreElements())
            {
                NetworkInterface ni = interfaces.nextElement();
                
                // Skip loopback, down, or virtual interfaces that might not be relevant for
                // physical discovery
                if(ni.isLoopback() || !ni.isUp() || ni.isPointToPoint())
                {
                    continue;
                }
                
                String interfaceName = ni.getName();
                String interfaceDisplayName = ni.getDisplayName();
                
                for(InterfaceAddress ia : ni.getInterfaceAddresses())
                {
                    InetAddress address = ia.getAddress();
                    // We only support IPv4 for now as per current project focus
                    if(address instanceof Inet4Address)
                    {
                        short prefixLength = ia.getNetworkPrefixLength();
                        if(prefixLength > 0 && prefixLength < 32)
                        {
                            networks.add(new NetworkInfo(address, prefixLength, interfaceName, interfaceDisplayName));
                        }
                    }
                }
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        
        return filterAndSortNetworksWithInterfaces(networks);
    }
    
    /**
     * Discovers all local network interfaces and returns a list of unique,
     * non-overlapping CIDR networks. Prioritizes larger ranges (smaller prefix
     * lengths) when networks overlap or encompass others.
     *
     * @deprecated Use {@link #discoverLocalNetworksWithInterfaces()} for
     *             interface-aware scanning
     */
    public static List<String> discoverLocalNetworks()
    {
        List<NetworkInfo> networks = new ArrayList<>();
        
        try
        {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while(interfaces.hasMoreElements())
            {
                NetworkInterface ni = interfaces.nextElement();
                
                // Skip loopback, down, or virtual interfaces that might not be relevant for
                // physical discovery
                if(ni.isLoopback() || !ni.isUp() || ni.isPointToPoint())
                {
                    continue;
                }
                
                for(InterfaceAddress ia : ni.getInterfaceAddresses())
                {
                    InetAddress address = ia.getAddress();
                    // We only support IPv4 for now as per current project focus
                    if(address instanceof Inet4Address)
                    {
                        short prefixLength = ia.getNetworkPrefixLength();
                        if(prefixLength > 0 && prefixLength < 32)
                        {
                            networks.add(new NetworkInfo(address, prefixLength, null, null));
                        }
                    }
                }
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        
        return filterAndSortNetworks(networks);
    }
    
    private static List<NetworkInterfaceInfo> filterAndSortNetworksWithInterfaces(List<NetworkInfo> networks)
    {
        if(networks.isEmpty())
        {
            return Collections.emptyList();
        }
        
        // Sort by prefix length (ascending) so larger networks come first
        // If prefix lengths are equal, sort by IP address
        networks.sort(
                Comparator.comparingInt((NetworkInfo n) -> n.prefixLength).thenComparing(n -> n.networkAddressLong));
        
        List<NetworkInfo> filtered = new ArrayList<>();
        
        for(NetworkInfo current : networks)
        {
            boolean isContained = false;
            for(NetworkInfo existing : filtered)
            {
                if(existing.contains(current))
                {
                    isContained = true;
                    break;
                }
            }
            if(!isContained)
            {
                filtered.add(current);
            }
        }
        
        return filtered.stream().map(NetworkInfo::toNetworkInterfaceInfo).distinct().collect(Collectors.toList());
    }
    
    private static List<String> filterAndSortNetworks(List<NetworkInfo> networks)
    {
        if(networks.isEmpty())
        {
            return Collections.emptyList();
        }
        
        // Sort by prefix length (ascending) so larger networks come first
        // If prefix lengths are equal, sort by IP address
        networks.sort(
                Comparator.comparingInt((NetworkInfo n) -> n.prefixLength).thenComparing(n -> n.networkAddressLong));
        
        List<NetworkInfo> filtered = new ArrayList<>();
        
        for(NetworkInfo current : networks)
        {
            boolean isContained = false;
            for(NetworkInfo existing : filtered)
            {
                if(existing.contains(current))
                {
                    isContained = true;
                    break;
                }
            }
            if(!isContained)
            {
                filtered.add(current);
            }
        }
        
        return filtered.stream().map(NetworkInfo::toCidr).distinct().collect(Collectors.toList());
    }
    
    private static class NetworkInfo
    {
        final long networkAddressLong;
        final short prefixLength;
        final long mask;
        final String originalIp;
        final String interfaceName;
        final String interfaceDisplayName;
        
        NetworkInfo(InetAddress address, short prefixLength, String interfaceName, String interfaceDisplayName)
        {
            this.originalIp = address.getHostAddress();
            this.prefixLength = prefixLength;
            this.mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
            long ipLong = ipToLong(address);
            this.networkAddressLong = ipLong & mask;
            this.interfaceName = interfaceName;
            this.interfaceDisplayName = interfaceDisplayName;
        }
        
        boolean contains(NetworkInfo other)
        {
            // "this" contains "other" if "this" mask covers "other" AND the network
            // addresses match under "this" mask
            if(this.prefixLength > other.prefixLength)
            {
                return false;
            }
            return (other.networkAddressLong & this.mask) == this.networkAddressLong;
        }
        
        String toCidr()
        {
            return longToIp(networkAddressLong) + "/" + prefixLength;
        }
        
        NetworkInterfaceInfo toNetworkInterfaceInfo()
        {
            return new NetworkInterfaceInfo(toCidr(), interfaceName, interfaceDisplayName, originalIp);
        }
        
        private static long ipToLong(InetAddress ip)
        {
            byte[] octets = ip.getAddress();
            long result = 0;
            for(byte octet : octets)
            {
                result <<= 8;
                result |= octet & 0xFF;
            }
            return result & 0xFFFFFFFFL;
        }
        
        private static String longToIp(long ip)
        {
            return ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF);
        }
    }
}
