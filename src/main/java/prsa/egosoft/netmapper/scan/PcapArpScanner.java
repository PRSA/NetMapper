package prsa.egosoft.netmapper.scan;

import org.pcap4j.core.BpfProgram;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.ArpPacket;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.namednumber.ArpHardwareType;
import org.pcap4j.packet.namednumber.ArpOperation;
import org.pcap4j.packet.namednumber.EtherType;
import org.pcap4j.util.ByteArrays;
import org.pcap4j.util.MacAddress;
import org.pcap4j.util.NifSelector;
import prsa.egosoft.netmapper.util.NetworkDiscoveryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class PcapArpScanner implements ArpScanner {
    private static final Logger logger = LoggerFactory.getLogger(PcapArpScanner.class);
    private static final int READ_TIMEOUT = 10; // ms
    private static final int SNAPLEN = 65536;
    private static final int COUNT = 1; // Packets to send per IP (could be more)
    private static final int SCAN_TIMEOUT_MS = 2000; // Time to wait for replies

    @Override
    public Map<String, String> scan(List<String> ips) {
        Map<String, String> detectedDevices = new ConcurrentHashMap<>();

        if (ips == null || ips.isEmpty())
            return detectedDevices;

        // Try to identify the correct network interface.
        // For simplicity, we might pick the one that matches the first IP's subnet,
        // or just try to find a suitable one.
        // In a real generic tool, we might iterate all interfaces.
        PcapNetworkInterface nif = null;
        try {
            // Heuristic: Find interface that has an address in the same subnet as the first
            // target IP.
            // Simplified: Just pick the first non-loopback with an IP.
            // Better: NetworkDiscoveryUtils logic usage.
            nif = findBestInterface(ips.get(0));
        } catch (Exception e) {
            logger.error("Error finding network interface", e);
            return detectedDevices;
        }

        if (nif == null) {
            logger.error("No suitable network interface found for Pcap4J scanning.");
            return detectedDevices;
        }

        logger.info("Starting active ARP scan on interface: {}", nif.getName());

        ExecutorService pool = Executors.newFixedThreadPool(1);

        try (PcapHandle handle = nif.openLive(SNAPLEN, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS,
                READ_TIMEOUT)) {
            PcapHandle sendHandle = nif.openLive(SNAPLEN, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS,
                    READ_TIMEOUT);

            // Set filter to capture only ARP replies
            handle.setFilter("arp", BpfProgram.BpfCompileMode.OPTIMIZE);

            // Listener task
            Future<?> listenerTask = pool.submit(() -> {
                long endTime = System.currentTimeMillis() + SCAN_TIMEOUT_MS + (ips.size() * 5L); // Add buffer for
                                                                                                 // sending time
                while (System.currentTimeMillis() < endTime) {
                    try {
                        Packet packet = handle.getNextPacket();
                        if (packet != null && packet.contains(ArpPacket.class)) {
                            ArpPacket arp = packet.get(ArpPacket.class);
                            if (arp.getHeader().getOperation().equals(ArpOperation.REPLY)) {
                                String ip = arp.getHeader().getSrcProtocolAddr().getHostAddress();
                                String mac = arp.getHeader().getSrcHardwareAddr().toString();
                                detectedDevices.put(ip, mac.toUpperCase());
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            });

            // Sender loop
            MacAddress srcMac = nif.getLinkLayerAddresses().stream()
                    .filter(a -> a instanceof MacAddress)
                    .map(a -> (MacAddress) a).findFirst().orElse(null);

            InetAddress srcIp = nif.getAddresses().stream()
                    .map(a -> a.getAddress())
                    .filter(a -> a instanceof java.net.Inet4Address)
                    .findFirst().orElse(null);

            if (srcMac != null && srcIp != null) {
                for (String ip : ips) {
                    try {
                        sendArpRequest(sendHandle, srcMac, srcIp, InetAddress.getByName(ip));
                        // Slight delay to avoid flooding too fast?
                        // Thread.sleep(1);
                    } catch (Exception e) {
                        logger.error("Error sending ARP to {}", ip, e);
                    }
                }
            } else {
                logger.error("Could not determine source MAC or IP for interface {}", nif.getName());
            }

            // Wait until listener finishes
            listenerTask.get();
            sendHandle.close();

        } catch (Exception e) {
            logger.error("Error during Pcap ARP scan", e);
        } finally {
            pool.shutdown();
        }

        return detectedDevices;
    }

    private void sendArpRequest(PcapHandle handle, MacAddress srcMac, InetAddress srcIp, InetAddress dstIp)
            throws Exception {
        ArpPacket.Builder arpBuilder = new ArpPacket.Builder()
                .hardwareType(ArpHardwareType.ETHERNET)
                .protocolType(EtherType.IPV4)
                .hardwareAddrLength((byte) MacAddress.SIZE_IN_BYTES)
                .protocolAddrLength((byte) ByteArrays.INET4_ADDRESS_SIZE_IN_BYTES)
                .operation(ArpOperation.REQUEST)
                .srcHardwareAddr(srcMac)
                .srcProtocolAddr(srcIp)
                .dstHardwareAddr(MacAddress.ETHER_BROADCAST_ADDRESS)
                .dstProtocolAddr(dstIp);

        EthernetPacket.Builder etherBuilder = new EthernetPacket.Builder()
                .dstAddr(MacAddress.ETHER_BROADCAST_ADDRESS)
                .srcAddr(srcMac)
                .type(EtherType.ARP)
                .payloadBuilder(arpBuilder)
                .paddingAtBuild(true);

        Packet p = etherBuilder.build();
        handle.sendPacket(p);
    }

    private PcapNetworkInterface findBestInterface(String targetIp) {
        try {
            // Simplified logic: get first UP, non-loopback.
            // Pcaps.getDevByAddress(InetAddress.getByName(targetIp)) won't work if target
            // is remote subnet.
            // We want LOCAL interface that routes to target.
            // For now, let's just pick the first likely physical interface.
            List<PcapNetworkInterface> allDevs = Pcaps.findAllDevs();
            for (PcapNetworkInterface dev : allDevs) {
                if (!dev.isLoopBack() && dev.isUp()) {
                    // Check if it has IPv4
                    boolean hasIpv4 = dev.getAddresses().stream()
                            .anyMatch(a -> a.getAddress() instanceof java.net.Inet4Address);
                    if (hasIpv4)
                        return dev;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to list pcap devices", e);
        }
        return null;
    }
}
