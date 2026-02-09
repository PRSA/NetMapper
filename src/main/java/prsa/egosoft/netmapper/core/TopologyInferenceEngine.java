package prsa.egosoft.netmapper.core;

import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkDevice.DeviceType;
import prsa.egosoft.netmapper.model.NetworkDevice.DiscoveryMethod;
import prsa.egosoft.netmapper.model.NetworkDevice.ManagementState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import prsa.egosoft.netmapper.model.DetectedEndpoint;
import prsa.egosoft.netmapper.model.NetworkInterface;
import prsa.egosoft.netmapper.model.LinkConfidence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Motor de inferencia para construir la "Shadow Reality" y la topología física
 * basada en la Metodología MUDFR.
 */
public class TopologyInferenceEngine {
    private static final Logger logger = LoggerFactory.getLogger(TopologyInferenceEngine.class);
    private boolean verbose = false;
    private boolean forensics = false;

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setForensics(boolean forensics) {
        this.forensics = forensics;
    }

    // --- Phase 2: Shadow Node Generation ---

    /**
     * Realiza la Fusión ARP (L3 -> L2) y Fusión L2 Pura.
     */
    public Map<String, NetworkDevice> inferShadowNodes(Map<String, NetworkDevice> validDevices) {
        Map<String, NetworkDevice> allDevices = new ConcurrentHashMap<>(validDevices);

        // 1. ARP Fusion: IP -> MAC
        for (NetworkDevice device : validDevices.values()) {
            Map<Integer, List<DetectedEndpoint>> macTable = device.getMacAddressTable();
            if (macTable == null)
                continue;

            for (List<DetectedEndpoint> endpoints : macTable.values()) {
                for (DetectedEndpoint endpoint : endpoints) {
                    if (endpoint.getIpAddress() != null && !endpoint.getIpAddress().isEmpty()) {
                        String ip = endpoint.getIpAddress();
                        if (!allDevices.containsKey(ip)) {
                            if (verbose) {
                                logger.info("[MOTOR-0] Inferring SHADOW_HOST from ARP: {} ({})", ip,
                                        endpoint.getMacAddress());
                            }
                            NetworkDevice shadowHost = createShadowHost(ip, endpoint.getMacAddress(),
                                    endpoint.getVendor());
                            allDevices.put(ip, shadowHost);
                        }
                    }
                }
            }
        }

        // 2. L2 Fusion: MAC Only
        for (NetworkDevice switchDevice : validDevices.values()) {
            Map<Integer, List<DetectedEndpoint>> fdb = switchDevice.getMacAddressTable();
            if (fdb == null)
                continue;

            for (List<DetectedEndpoint> endpoints : fdb.values()) {
                for (DetectedEndpoint endpoint : endpoints) {
                    String mac = endpoint.getMacAddress();
                    boolean isKnown = allDevices.values().stream().anyMatch(
                            d -> d.getInterfaces().stream().anyMatch(i -> mac.equalsIgnoreCase(i.getMacAddress())));

                    if (!isKnown) {
                        NetworkDevice shadowDevice = new NetworkDevice();
                        shadowDevice.setSysName("L2-" + mac);
                        shadowDevice.setTypeEnum(DeviceType.SHADOW_DEVICE);
                        shadowDevice.setDiscoveryMethod(DiscoveryMethod.FDB_SNOOP);
                        shadowDevice.setMgmtState(ManagementState.UNREACHABLE);
                        shadowDevice.setConfidence(0.6);
                        shadowDevice.setVendor(endpoint.getVendor());

                        NetworkInterface iface = new NetworkInterface(0, "eth0 (Inferred)");
                        iface.setMacAddress(mac);
                        shadowDevice.addInterface(iface);
                        allDevices.put(mac, shadowDevice);
                    }
                }
            }
        }
        return allDevices;
    }

    private NetworkDevice createShadowHost(String ip, String mac, String vendor) {
        NetworkDevice shadow = new NetworkDevice(ip);
        shadow.setTypeEnum(DeviceType.SHADOW_HOST);
        shadow.setDiscoveryMethod(DiscoveryMethod.ARP_INFERENCE);
        shadow.setMgmtState(ManagementState.UNREACHABLE);
        shadow.setConfidence(0.8);
        shadow.setVendor(vendor);

        NetworkInterface iface = new NetworkInterface(0, "eth0 (Inferred)");
        iface.setMacAddress(mac);
        iface.setIpAddress(ip);
        shadow.addInterface(iface);
        return shadow;
    }

    // --- MOTOR 1: Backbone Engine ---

    public void processBackbone(Map<String, NetworkDevice> devices) {
        for (NetworkDevice device : devices.values()) {
            Map<Integer, String> neighbors = device.getLldpNeighbors();
            if (neighbors == null || neighbors.isEmpty())
                continue;

            for (Map.Entry<Integer, String> entry : neighbors.entrySet()) {
                int portIdx = entry.getKey();
                String neighborSysName = entry.getValue();
                NetworkDevice remote = findDeviceBySysName(neighborSysName, devices.values());

                if (remote != null) {
                    NetworkInterface localIf = findInterfaceByIndex(device, portIdx);
                    if (localIf != null) {
                        localIf.setRole(NetworkInterface.PortRole.UPLINK);
                    }
                }
            }
            detectLagInterfaces(device);
            detectStacking(device, devices);
        }
    }

    private NetworkDevice findDeviceBySysName(String sysName, Collection<NetworkDevice> allDevices) {
        if (sysName == null || sysName.isEmpty())
            return null;
        for (NetworkDevice d : allDevices) {
            if (sysName.equalsIgnoreCase(d.getSysName()))
                return d;
        }
        return null;
    }

    private void detectLagInterfaces(NetworkDevice device) {
        for (NetworkInterface ni : device.getInterfaces()) {
            if (ni.getDescription() != null && (ni.getDescription().toLowerCase().contains("lag")
                    || ni.getDescription().toLowerCase().contains("port-channel"))) {
                // LAG Detection Logic here
            }
        }
    }

    private void detectStacking(NetworkDevice device, Map<String, NetworkDevice> allDevices) {
        if (device.isStack()) {
            device.setTypeEnum(DeviceType.CLUSTER);
        }
    }

    private NetworkInterface findInterfaceByIndex(NetworkDevice dev, int idx) {
        for (NetworkInterface ni : dev.getInterfaces()) {
            if (ni.getIndex() == idx)
                return ni;
        }
        return null;
    }

    // --- MOTOR 2: Edge Engine ---

    public void processEdge(Map<String, NetworkDevice> allDevices) {
        for (NetworkDevice targetNode : allDevices.values()) {
            if (targetNode.getTypeEnum() == DeviceType.SHADOW_HOST ||
                    targetNode.getTypeEnum() == DeviceType.SHADOW_DEVICE ||
                    targetNode.getTypeEnum() == DeviceType.UNKNOWN) {

                String targetMac = null;
                for (NetworkInterface ni : targetNode.getInterfaces()) {
                    if (ni.getMacAddress() != null && !ni.getMacAddress().isEmpty()) {
                        targetMac = ni.getMacAddress();
                        break;
                    }
                }

                if (targetMac == null)
                    continue;

                LocationResult result = triangulatePhysicalLocation(targetMac, allDevices.values());

                if (result.type == LocationResult.Type.EDGE_PORT_FOUND) {
                    if (verbose) {
                        String targetId = targetNode.getIpAddress() != null ? targetNode.getIpAddress()
                                : targetNode.getSysName();
                        logger.info("[MOTOR-2] Physically located {} on {} port {}", targetId,
                                result.candidates.get(0).switchDevice.getIpAddress(),
                                result.candidates.get(0).portIndex);
                    }
                    targetNode.setConfidence(result.confidence);
                    targetNode.addDiscoverySource("FDB_TRIANGULATION");
                } else if (result.type == LocationResult.Type.SHARED_SEGMENT_FOUND) {
                    targetNode.setTypeEnum(DeviceType.SHADOW_HOST);
                    targetNode.setConfidence(0.70);
                }
            }
        }
    }

    public LocationResult triangulatePhysicalLocation(String targetMac, Collection<NetworkDevice> allDevices) {
        List<Location> candidateLocations = new ArrayList<>();
        for (NetworkDevice switchDevice : allDevices) {
            Map<Integer, List<DetectedEndpoint>> fdb = switchDevice.getMacAddressTable();
            if (fdb == null)
                continue;

            for (Map.Entry<Integer, List<DetectedEndpoint>> entry : fdb.entrySet()) {
                int portIndex = entry.getKey();
                NetworkInterface ni = findInterfaceByIndex(switchDevice, portIndex);

                if (ni != null && ni.getRole() == NetworkInterface.PortRole.UPLINK)
                    continue;

                for (DetectedEndpoint endpoint : entry.getValue()) {
                    if (targetMac.equalsIgnoreCase(endpoint.getMacAddress())) {
                        if (forensics) {
                            logger.info("[FORENSICS] MAC {} found in FDB of {} (Port {})", targetMac,
                                    switchDevice.getIpAddress(), portIndex);
                        }
                        boolean isShared = entry.getValue().size() > 3;
                        candidateLocations.add(new Location(switchDevice, portIndex, "1", isShared));
                    }
                }
            }
        }

        if (candidateLocations.isEmpty())
            return LocationResult.unknown();

        List<Location> nonShared = candidateLocations.stream().filter(l -> !l.isSharedSegment)
                .collect(Collectors.toList());

        if (nonShared.size() == 1) {
            return LocationResult.edgeFound(nonShared.get(0), 0.95f);
        } else if (!nonShared.isEmpty()) {
            return LocationResult.ambiguous(nonShared);
        } else {
            return LocationResult.sharedSegment(candidateLocations.get(0));
        }
    }

    public void validatePhysicalCharacteristics(NetworkInterface a, NetworkInterface b) {
        if (a == null || b == null)
            return;
        boolean valid = true;
        if (a.getSpeed() != null && b.getSpeed() != null && !a.getSpeed().equals(b.getSpeed())) {
            a.setMismatchReason("speed_mismatch");
            b.setMismatchReason("speed_mismatch");
            valid = false;
        }
        if (a.getDuplexMode() != null && b.getDuplexMode() != null
                && !a.getDuplexMode().equalsIgnoreCase(b.getDuplexMode())) {
            a.setMismatchReason("duplex_mismatch");
            b.setMismatchReason("duplex_mismatch");
            valid = false;
        }
        if (a.getMtu() > 0 && b.getMtu() > 0 && a.getMtu() != b.getMtu()) {
            a.setMismatchReason("mtu_mismatch");
            b.setMismatchReason("mtu_mismatch");
            valid = false;
        }
        a.setPhysicalValid(valid);
        b.setPhysicalValid(valid);
    }

    // --- MOTOR 3: Capa Lógica ---

    public void processLogicalLayer(Map<String, NetworkDevice> allDevices) {
        List<NetworkDevice> devices = new ArrayList<>(allDevices.values());
        for (int i = 0; i < devices.size(); i++) {
            NetworkDevice a = devices.get(i);
            for (int j = i + 1; j < devices.size(); j++) {
                NetworkDevice b = devices.get(j);
                if (areOnSameSubnet(a, b)) {
                    a.addDiscoverySource("L3_ADJACENCY");
                    b.addDiscoverySource("L3_ADJACENCY");
                }
            }
            if (a.getRoutingTable() != null && a.getRoutingTable().containsKey("0.0.0.0/0")) {
                // Simple gateway detection
            }
        }
    }

    private boolean areOnSameSubnet(NetworkDevice a, NetworkDevice b) {
        for (NetworkInterface ifA : a.getInterfaces()) {
            if (ifA.getIpAddress() == null || ifA.getSubnetMask() == null)
                continue;
            for (NetworkInterface ifB : b.getInterfaces()) {
                if (ifB.getIpAddress() == null)
                    continue;
                if (isInSubnet(ifB.getIpAddress(), ifA.getIpAddress(), ifA.getSubnetMask()))
                    return true;
            }
        }
        return false;
    }

    private boolean isInSubnet(String ip, String subnetIp, String mask) {
        try {
            String[] ipParts = ip.split("\\.");
            String[] subnetParts = subnetIp.split("\\.");
            String[] maskParts = mask.split("\\.");
            if (ipParts.length != 4 || subnetParts.length != 4 || maskParts.length != 4)
                return false;
            for (int i = 0; i < 3; i++) {
                if (Integer.parseInt(maskParts[i]) == 255) {
                    if (!ipParts[i].equals(subnetParts[i]))
                        return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // --- Confidence Model ---

    public float calculateConfidence(DiscoveryMethod source, long lastSeenTime) {
        float baseConfidence = getBaseConfidence(source);
        long timeDiff = System.currentTimeMillis() - lastSeenTime;
        LinkConfidence.DecayProfile profile = LinkConfidence.DecayProfile.HALF_LIFE_24H;
        return calculateDecayedScore(baseConfidence, timeDiff, profile);
    }

    private float getBaseConfidence(DiscoveryMethod source) {
        switch (source) {
            case VERIFIED_MANAGEMENT:
                return 1.0f;
            case FDB_SNOOP:
                return 0.95f;
            case ARP_INFERENCE:
                return 0.8f;
            default:
                return 0.5f;
        }
    }

    private float calculateDecayedScore(float initialScore, long timeDiffMillis, LinkConfidence.DecayProfile profile) {
        long halfLifeMillis = (profile == LinkConfidence.DecayProfile.HALF_LIFE_1H) ? 3600 * 1000L : 24 * 3600 * 1000L;
        if (profile == LinkConfidence.DecayProfile.NO_DECAY)
            return initialScore;
        double decayFactor = Math.pow(0.5, (double) timeDiffMillis / halfLifeMillis);
        return (float) (initialScore * decayFactor);
    }

    // --- Inner Classes ---

    public static class LocationResult {
        public enum Type {
            EDGE_PORT_FOUND, AMBIGUOUS_LOCATION, SHARED_SEGMENT_FOUND, UNKNOWN
        }

        public Type type;
        public List<Location> candidates;
        public float confidence;

        public LocationResult(Type type, List<Location> candidates, float confidence) {
            this.type = type;
            this.candidates = candidates;
            this.confidence = confidence;
        }

        public static LocationResult edgeFound(Location loc, float confidence) {
            return new LocationResult(Type.EDGE_PORT_FOUND, Collections.singletonList(loc), confidence);
        }

        public static LocationResult sharedSegment(Location loc) {
            return new LocationResult(Type.SHARED_SEGMENT_FOUND, Collections.singletonList(loc), 0.8f);
        }

        public static LocationResult ambiguous(List<Location> locs) {
            return new LocationResult(Type.AMBIGUOUS_LOCATION, locs, 0.0f);
        }

        public static LocationResult unknown() {
            return new LocationResult(Type.UNKNOWN, Collections.emptyList(), 0.0f);
        }
    }

    public static class Location {
        public NetworkDevice switchDevice;
        public int portIndex;
        public String vlan;
        public boolean isSharedSegment;

        public Location(NetworkDevice switchDevice, int portIndex, String vlan, boolean isSharedSegment) {
            this.switchDevice = switchDevice;
            this.portIndex = portIndex;
            this.vlan = vlan;
            this.isSharedSegment = isSharedSegment;
        }
    }
}
