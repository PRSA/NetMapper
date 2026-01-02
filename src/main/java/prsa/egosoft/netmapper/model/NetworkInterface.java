package prsa.egosoft.netmapper.model;

/**
 * Representa una interfaz de red de un dispositivo.
 */
public class NetworkInterface {
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

    public NetworkInterface() {
        this.taggedVlans = new java.util.ArrayList<>();
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

    @Override
    public String toString() {
        return description + " ["
                + (ipAddress != null ? ipAddress : prsa.egosoft.netmapper.i18n.Messages.getString("interface.no_ip"))
                + "]";
    }
}
