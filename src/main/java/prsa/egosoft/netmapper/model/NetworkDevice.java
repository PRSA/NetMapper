package prsa.egosoft.netmapper.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representa un dispositivo de red y su información recolectada.
 */
public class NetworkDevice {
    public enum DeviceType {
        SWITCH, ROUTER, FIREWALL, MANAGED_HOST, SHADOW_HOST, SHADOW_DEVICE, UNMANAGED_SWITCH, UNKNOWN
    }

    public enum DiscoveryMethod {
        VERIFIED_MANAGEMENT, ARP_INFERENCE, FDB_SNOOP, PASSIVE_TRAFFIC, MANUAL
    }

    public enum ManagementState {
        REACHABLE, UNREACHABLE, AUTH_FAILED, POLICIED_SILENCE, UNKNOWN
    }

    private String ipAddress;
    private String sysDescr;
    private String sysName;
    private String sysLocation;
    private String sysContact;
    private String sysUpTime;
    private String sysObjectId; // OID del sistema

    private String vendor;
    private String model;
    private String deviceType;
    private int sysServices;
    private String formattedServices;

    // Methodology Attributes
    private String layer; // core, distribution, access, edge, endpoint
    private java.util.List<String> discoverySources;
    private double confidence;
    private double stabilityScore;

    // New MUDFR Attributes
    private DeviceType typeEnum;
    private DiscoveryMethod discoveryMethod;
    private ManagementState mgmtState;
    private long lastSeenTimestamp;

    private List<NetworkInterface> interfaces;
    private Map<String, String> routingTable; // Destino -> NextHop
    private List<String> vlans;
    private Map<String, String> routeProtocols; // Destino -> Protocol Name (OSPF, RIP, etc.)

    // Mapa: Puerto (Index) -> Lista de Endpoints detectados
    private Map<Integer, List<DetectedEndpoint>> macAddressTable;

    // Mapa: Puerto (Index) -> Info Vecino LLDP/CDP (String descrtion/sysname)
    private Map<Integer, String> lldpNeighbors;

    public NetworkDevice() {
        this.interfaces = new ArrayList<>();
        this.routingTable = new HashMap<>();
        this.vlans = new ArrayList<>();
        this.macAddressTable = new HashMap<>();
        this.lldpNeighbors = new HashMap<>();
        this.routeProtocols = new HashMap<>();
        this.discoverySources = new ArrayList<>();
        this.confidence = 1.0;
        this.stabilityScore = 1.0;

        // Default values
        this.typeEnum = DeviceType.UNKNOWN;
        this.discoveryMethod = DiscoveryMethod.MANUAL;
        this.mgmtState = ManagementState.UNKNOWN;
        this.lastSeenTimestamp = System.currentTimeMillis();
    }

    public NetworkDevice(String ipAddress) {
        this();
        this.ipAddress = ipAddress;
        this.deviceType = prsa.egosoft.netmapper.i18n.Messages.getString("device.type.unknown");
    }

    // Getters y Setters
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getSysDescr() {
        return sysDescr;
    }

    public void setSysDescr(String sysDescr) {
        this.sysDescr = sysDescr;
    }

    public String getSysName() {
        return sysName;
    }

    public void setSysName(String sysName) {
        this.sysName = sysName;
    }

    public String getSysLocation() {
        return sysLocation;
    }

    public void setSysLocation(String sysLocation) {
        this.sysLocation = sysLocation;
    }

    public String getSysContact() {
        return sysContact;
    }

    public void setSysContact(String sysContact) {
        this.sysContact = sysContact;
    }

    public String getSysUpTime() {
        return sysUpTime;
    }

    public void setSysUpTime(String sysUpTime) {
        this.sysUpTime = sysUpTime;
    }

    public String getSysObjectId() {
        return sysObjectId;
    }

    public void setSysObjectId(String sysObjectId) {
        this.sysObjectId = sysObjectId;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<NetworkInterface> getInterfaces() {
        return interfaces;
    }

    public void addInterface(NetworkInterface networkInterface) {
        this.interfaces.add(networkInterface);
    }

    public Map<String, String> getRoutingTable() {
        return routingTable;
    }

    public void setRoutingTable(Map<String, String> routingTable) {
        this.routingTable = routingTable;
    }

    public List<String> getVlans() {
        return vlans;
    }

    public void setVlans(List<String> vlans) {
        this.vlans = vlans;
    }

    public Map<Integer, List<DetectedEndpoint>> getMacAddressTable() {
        return macAddressTable;
    }

    public void setMacAddressTable(Map<Integer, List<DetectedEndpoint>> macAddressTable) {
        this.macAddressTable = macAddressTable;
    }

    public Map<Integer, String> getLldpNeighbors() {
        return lldpNeighbors;
    }

    public void setLldpNeighbors(Map<Integer, String> lldpNeighbors) {
        this.lldpNeighbors = lldpNeighbors;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public int getSysServices() {
        return sysServices;
    }

    public void setSysServices(int sysServices) {
        this.sysServices = sysServices;
    }

    public String getFormattedServices() {
        return formattedServices;
    }

    public void setFormattedServices(String formattedServices) {
        this.formattedServices = formattedServices;
    }

    public String getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer = layer;
    }

    public java.util.List<String> getDiscoverySources() {
        return discoverySources;
    }

    public void addDiscoverySource(String source) {
        if (!this.discoverySources.contains(source)) {
            this.discoverySources.add(source);
        }
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public double getStabilityScore() {
        return stabilityScore;
    }

    public void setStabilityScore(double stabilityScore) {
        this.stabilityScore = stabilityScore;
    }

    public Map<String, String> getRouteProtocols() {
        return routeProtocols;
    }

    public void setRouteProtocols(Map<String, String> routeProtocols) {
        this.routeProtocols = routeProtocols;
    }

    public DeviceType getTypeEnum() {
        return typeEnum;
    }

    public void setTypeEnum(DeviceType typeEnum) {
        this.typeEnum = typeEnum;
    }

    public DiscoveryMethod getDiscoveryMethod() {
        return discoveryMethod;
    }

    public void setDiscoveryMethod(DiscoveryMethod discoveryMethod) {
        this.discoveryMethod = discoveryMethod;
    }

    public ManagementState getMgmtState() {
        return mgmtState;
    }

    public void setMgmtState(ManagementState mgmtState) {
        this.mgmtState = mgmtState;
    }

    public long getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }

    public void setLastSeenTimestamp(long lastSeenTimestamp) {
        this.lastSeenTimestamp = lastSeenTimestamp;
    }

    @Override
    public String toString() {
        return sysName != null && !sysName.isEmpty() ? sysName + " (" + ipAddress + ")" : ipAddress;
    }
}
