package prsa.egosoft.netmapper.model;

import java.util.Map;

/**
 * Data Transfer Object for the network map JSON structure.
 */
public class NetworkMapDTO {
    private Map<String, Object> summary;
    private Map<String, NetworkDevice> devices;

    public NetworkMapDTO() {
    }

    public Map<String, Object> getSummary() {
        return summary;
    }

    public void setSummary(Map<String, Object> summary) {
        this.summary = summary;
    }

    public Map<String, NetworkDevice> getDevices() {
        return devices;
    }

    public void setDevices(Map<String, NetworkDevice> devices) {
        this.devices = devices;
    }
}
