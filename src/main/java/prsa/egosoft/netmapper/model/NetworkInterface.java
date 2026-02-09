package prsa.egosoft.netmapper.model;

/**
 * Representa una interfaz de red de un dispositivo.
 */
public class NetworkInterface {
    public enum PortRole {
        UPLINK, ACCESS, INTERCONNECT, VIRTUAL, UNKNOWN
    }

    private int index;
    private String description; // ifDescr
    private String macAddress; // ifPhysAddress
    private String adminStatus;
    private String operStatus;
    private String ipAddress;
    private String subnetMask;
    private int untaggedVlanId; // VLAN nativa o no etiquetada
    private java.util.List<Integer> taggedVlans; // VLANs etiquetadas
    private int mtu;
    private String speed;
    private String type;
    private String duplexMode; // Full, Half, Unknown
    private String stpState; // Forwarding, Blocking, etc.
    private String stpRole; // Designated, Root, Alternate, etc.
    private String neighborInfo; // Descriptions of connected neighbors (LLDP/CDP)
    private boolean physicalValid; // Phase 6: physical_valid
    private String mismatchReason; // speed_mismatch, duplex_mismatch, mtu_mismatch, none
    private long inErrors; // ifInErrors
    private long outErrors; // ifOutErrors
    private long inDiscards; // ifInDiscards
    private long outDiscards; // ifOutDiscards

    // GTR Attributes
    private PortRole role;

    public NetworkInterface() {
        this.taggedVlans = new java.util.ArrayList<>();
        this.physicalValid = true;
        this.mismatchReason = "none";
        this.role = PortRole.UNKNOWN;
    }

    public NetworkInterface(int index, String description) {
        this();
        this.index = index;
        this.description = description;
    }

    public int getIndex() {
        return index;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getAdminStatus() {
        return adminStatus;
    }

    public void setAdminStatus(String adminStatus) {
        this.adminStatus = adminStatus;
    }

    public String getOperStatus() {
        return operStatus;
    }

    public void setOperStatus(String operStatus) {
        this.operStatus = operStatus;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getSubnetMask() {
        return subnetMask;
    }

    public void setSubnetMask(String subnetMask) {
        this.subnetMask = subnetMask;
    }

    public int getUntaggedVlanId() {
        return untaggedVlanId;
    }

    public void setUntaggedVlanId(int untaggedVlanId) {
        this.untaggedVlanId = untaggedVlanId;
    }

    public java.util.List<Integer> getTaggedVlans() {
        return taggedVlans;
    }

    public void setTaggedVlans(java.util.List<Integer> taggedVlans) {
        this.taggedVlans = taggedVlans;
    }

    public void addTaggedVlan(int vlanId) {
        if (!this.taggedVlans.contains(vlanId)) {
            this.taggedVlans.add(vlanId);
        }
    }

    public int getMtu() {
        return mtu;
    }

    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    public String getSpeed() {
        return speed;
    }

    public void setSpeed(String speed) {
        this.speed = speed;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDuplexMode() {
        return duplexMode;
    }

    public void setDuplexMode(String duplexMode) {
        this.duplexMode = duplexMode;
    }

    public String getStpState() {
        return stpState;
    }

    public void setStpState(String stpState) {
        this.stpState = stpState;
    }

    public String getNeighborInfo() {
        return neighborInfo;
    }

    public void setNeighborInfo(String neighborInfo) {
        this.neighborInfo = neighborInfo;
    }

    public String getStpRole() {
        return stpRole;
    }

    public void setStpRole(String stpRole) {
        this.stpRole = stpRole;
    }

    public boolean isPhysicalValid() {
        return physicalValid;
    }

    public void setPhysicalValid(boolean physicalValid) {
        this.physicalValid = physicalValid;
    }

    public String getMismatchReason() {
        return mismatchReason;
    }

    public void setMismatchReason(String mismatchReason) {
        this.mismatchReason = mismatchReason;
    }

    public long getInErrors() {
        return inErrors;
    }

    public void setInErrors(long inErrors) {
        this.inErrors = inErrors;
    }

    public long getOutErrors() {
        return outErrors;
    }

    public void setOutErrors(long outErrors) {
        this.outErrors = outErrors;
    }

    public long getInDiscards() {
        return inDiscards;
    }

    public void setInDiscards(long inDiscards) {
        this.inDiscards = inDiscards;
    }

    public long getOutDiscards() {
        return outDiscards;
    }

    public void setOutDiscards(long outDiscards) {
        this.outDiscards = outDiscards;
    }

    public PortRole getRole() {
        return role;
    }

    public void setRole(PortRole role) {
        this.role = role;
    }

    @Override
    public String toString() {
        return description + " ["
                + (ipAddress != null ? ipAddress : prsa.egosoft.netmapper.i18n.Messages.getString("interface.no_ip"))
                + "]";
    }
}
