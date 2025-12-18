package prsa.egosoft.netmapper.model;

/**
 * Represents an endpoint detected on a network interface.
 * Contains MAC address, optional IP address, and Vendor information.
 */
public class DetectedEndpoint {
    private String macAddress;
    private String ipAddress;
    private String vendor;

    public DetectedEndpoint(String macAddress, String ipAddress, String vendor) {
        this.macAddress = macAddress;
        this.ipAddress = ipAddress;
        this.vendor = vendor;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    @Override
    public String toString() {
        // Formato para UI: MAC (Vendor) - IP: x.x.x.x
        StringBuilder sb = new StringBuilder();
        sb.append(macAddress);
        if (vendor != null && !vendor.isEmpty() && !"Unknown".equals(vendor)) {
            sb.append(" (").append(vendor).append(")");
        }
        if (ipAddress != null && !ipAddress.isEmpty()) {
            sb.append(" - IP: ").append(ipAddress);
        }
        return sb.toString();
    }
}
