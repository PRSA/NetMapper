package com.netmapper.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representa un dispositivo de red y su información recolectada.
 */
public class NetworkDevice {
    private String ipAddress;
    private String sysDescr;
    private String sysName;
    private String sysLocation;
    private String sysContact;
    private String sysUpTime;
    private String sysObjectId; // OID del sistema

    private String vendor;
    private String model;

    private List<NetworkInterface> interfaces;
    private Map<String, String> routingTable; // Destino -> NextHop
    private List<String> vlans;

    // Mapa: Puerto (Index) -> Lista de Endpoints detectados
    private Map<Integer, List<DetectedEndpoint>> macAddressTable;

    public NetworkDevice(String ipAddress) {
        this.ipAddress = ipAddress;
        this.interfaces = new ArrayList<>();
        this.routingTable = new HashMap<>();
        this.vlans = new ArrayList<>();
        this.macAddressTable = new HashMap<>();
    }

    // Getters y Setters
    public String getIpAddress() {
        return ipAddress;
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

    @Override
    public String toString() {
        return sysName != null && !sysName.isEmpty() ? sysName + " (" + ipAddress + ")" : ipAddress;
    }
}
