package prsa.egosoft.netmapper.core;

import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkDevice.DeviceType;
import prsa.egosoft.netmapper.model.NetworkDevice.DiscoveryMethod;
import prsa.egosoft.netmapper.model.NetworkDevice.ManagementState;
import prsa.egosoft.netmapper.model.DetectedEndpoint;
import prsa.egosoft.netmapper.model.NetworkInterface;
import prsa.egosoft.netmapper.model.LinkConfidence;
import prsa.egosoft.netmapper.model.LinkConfidence.DecayProfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Motor de inferencia para construir la "Shadow Reality" y la topología física
 * basada en la Metodología MUDFR.
 */
public class TopologyInferenceEngine {

    // --- Phase 2: Shadow Node Generation ---

    /**
     * Realiza la Fusión ARP (L3 -> L2) y Fusión L2 Pura.
     * Analiza las tablas ARP y FDB de los dispositivos gestionados para inferir
     * la existencia de nodos "Sombra" (Shadow Nodes).
     * 
     * @param validDevices Mapa de dispositivos ya descubiertos (Managed).
     * @return Nuevo mapa que incluye los dispositivos originales + los inferidos.
     */
    public Map<String, NetworkDevice> inferShadowNodes(Map<String, NetworkDevice> validDevices) {
        Map<String, NetworkDevice> allDevices = new ConcurrentHashMap<>(validDevices);

        // 1. ARP Fusion: IP -> MAC (seen in Router Core)
        for (NetworkDevice device : validDevices.values()) {
            // Check MacAddressTable which typically holds ARP entries (IP+MAC) from
            // standard MIBs
            Map<Integer, List<DetectedEndpoint>> macTable = device.getMacAddressTable();
            if (macTable == null)
                continue;

            for (List<DetectedEndpoint> endpoints : macTable.values()) {
                for (DetectedEndpoint endpoint : endpoints) {
                    if (endpoint.getIpAddress() != null && !endpoint.getIpAddress().isEmpty()) {
                        String ip = endpoint.getIpAddress();

                        // If IP is not already a managed device
                        if (!allDevices.containsKey(ip)) {
                            NetworkDevice shadowHost = createShadowHost(ip, endpoint.getMacAddress(),
                                    endpoint.getVendor());
                            allDevices.put(ip, shadowHost);
                        } else {
                            // Update existing device with MAC if missing?
                            // Typically managed device discovery should have found it, but we can enrich.
                        }
                    }
                }
            }
        }

        // 2. L2 Fusion: MAC Only (seen in FDB) -> Shadow Device
        for (NetworkDevice switchDevice : validDevices.values()) {
            Map<Integer, List<DetectedEndpoint>> fdb = switchDevice.getMacAddressTable();
            if (fdb == null)
                continue;

            for (List<DetectedEndpoint> endpoints : fdb.values()) {
                for (DetectedEndpoint endpoint : endpoints) {
                    String mac = endpoint.getMacAddress();

                    // Check if this MAC is already associated with ANY known device (Managed or
                    // Shadow Host)
                    boolean isKnown = allDevices.values().stream().anyMatch(
                            d -> d.getInterfaces().stream().anyMatch(i -> mac.equalsIgnoreCase(i.getMacAddress())));

                    if (!isKnown) {
                        // Nobody claims this MAC -> It's a Shadow Device (L2 only)
                        NetworkDevice shadowDevice = new NetworkDevice(); // No IP
                        shadowDevice.setSysName("L2-" + mac);
                        shadowDevice.setTypeEnum(DeviceType.SHADOW_DEVICE);
                        shadowDevice.setDiscoveryMethod(DiscoveryMethod.FDB_SNOOP);
                        shadowDevice.setMgmtState(ManagementState.UNREACHABLE);
                        shadowDevice.setConfidence(0.6); // Lower confidence than ARP-correlated host
                        shadowDevice.setVendor(endpoint.getVendor());

                        NetworkInterface iface = new NetworkInterface(0, "eth0 (Inferred)");
                        iface.setMacAddress(mac);
                        shadowDevice.addInterface(iface);

                        // Use MAC as key since no IP
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
        shadow.setMgmtState(ManagementState.UNREACHABLE); // By definition, didn't respond to SNMP
        shadow.setConfidence(0.8); // High confidence of existence, lower than verified
        shadow.setVendor(vendor);

        // Create an interface to hold the MAC
        NetworkInterface iface = new NetworkInterface(0, "eth0 (Inferred)");
        iface.setMacAddress(mac);
        iface.setIpAddress(ip);
        shadow.addInterface(iface);

        return shadow;
    }

    // --- Phase 3: Triangulation FDB ---

    public static class LocationResult {
        public enum Type {
            EDGE_PORT_FOUND,
            AMBIGUOUS_LOCATION,
            SHARED_SEGMENT_FOUND,
            UNKNOWN
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

    /**
     * Triangula la ubicación física de un nodo basado en las tablas FDB.
     * 
     * @param targetMac   MAC del nodo a ubicar.
     * @param allDevices  Todos los dispositivos (para buscar en sus FDBs).
     * @param uplinkPorts Set de puertos 'uplink' que deben ser ignorados.
     *                    Formato sugerido: "SwitchIP:PortIndex"
     */
    public LocationResult triangulatePhysicalLocation(String targetMac, Collection<NetworkDevice> allDevices,
            Set<String> uplinkPorts) {
        List<Location> candidateLocations = new ArrayList<>();

        for (NetworkDevice switchDevice : allDevices) {
            Map<Integer, List<DetectedEndpoint>> fdb = switchDevice.getMacAddressTable();
            if (fdb == null)
                continue;

            for (Map.Entry<Integer, List<DetectedEndpoint>> entry : fdb.entrySet()) {
                int portIndex = entry.getKey();
                String uplinkKey = switchDevice.getIpAddress() + ":" + portIndex;

                // CRITICAL: Ignore if MAC is seen on an Uplink port
                if (uplinkPorts.contains(uplinkKey)) {
                    continue;
                }

                // Heuristic: Check if this port has multiple MACs (Unmanaged Switch Candidate)
                int macCountOnPort = entry.getValue().size();
                if (macCountOnPort > 1) {
                    // This port likely connects to an unmanaged switch
                    // We return a specific result so the caller knows to create an intermediate
                    // node
                    candidateLocations.add(new Location(switchDevice, portIndex, "1", true)); // true = isSharedSegment
                } else {
                    for (DetectedEndpoint endpoint : entry.getValue()) {
                        if (targetMac.equalsIgnoreCase(endpoint.getMacAddress())) {
                            // Found MAC on this port
                            String vlan = "1"; // Default or extract if available
                            candidateLocations.add(new Location(switchDevice, portIndex, vlan, false));
                        }
                    }
                }
            }
        }

        if (candidateLocations.stream().anyMatch(l -> l.isSharedSegment)) {
            // If any candidate location is a shared segment, we prioritize reporting that
            // The caller should link shadow node -> unmanaged switch -> this port
            return LocationResult
                    .sharedSegment(candidateLocations.stream().filter(l -> l.isSharedSegment).findFirst().get());
        }

        if (candidateLocations.isEmpty()) {
            return LocationResult.unknown(); // Likely wireless or behind NAT
        } else if (candidateLocations.size() == 1) {
            return LocationResult.edgeFound(candidateLocations.get(0), 0.95f); // High confidence
        } else {
            // MAC seen on multiple access ports -> Ambiguous (Loop, VM migration, Spoofing)
            // Or properly handled by detailed analysis (e.g. unmanaged switch detection
            // logic)
            return LocationResult.ambiguous(candidateLocations);
        }
    }

    // --- Phase 11: Dynamic Confidence Model ---

    /**
     * Calcula la confianza dinámica basada en la fuente y el tiempo.
     */
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
            case PASSIVE_TRAFFIC:
                return 0.6f;
            default:
                return 0.5f;
        }
    }

    private float calculateDecayedScore(float initialScore, long timeDiffMillis, LinkConfidence.DecayProfile profile) {
        long halfLifeMillis;
        switch (profile) {
            case HALF_LIFE_1H:
                halfLifeMillis = 3600 * 1000L;
                break;
            case HALF_LIFE_24H:
                halfLifeMillis = 24 * 3600 * 1000L;
                break;
            case NO_DECAY:
                return initialScore;
            default:
                halfLifeMillis = 24 * 3600 * 1000L;
        }

        double decayFactor = Math.pow(0.5, (double) timeDiffMillis / halfLifeMillis);
        return (float) (initialScore * decayFactor);
    }
}
