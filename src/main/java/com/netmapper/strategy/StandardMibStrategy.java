package com.netmapper.strategy;

import com.netmapper.core.SnmpClient;
import com.netmapper.model.NetworkDevice;
import com.netmapper.model.NetworkInterface;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

/**
 * Estrategia estándar de descubrimiento usando MIB-II (RFC 1213).
 * Soporta la mayoría de routers y switches para información básica.
 */
public class StandardMibStrategy implements DiscoveryStrategy {

    // OIDs MIB-II System
    private static final String OID_SYS_DESCR = "1.3.6.1.2.1.1.1.0";
    private static final String OID_SYS_OBJECT_ID = "1.3.6.1.2.1.1.2.0";
    private static final String OID_SYS_UPTIME = "1.3.6.1.2.1.1.3.0";
    private static final String OID_SYS_CONTACT = "1.3.6.1.2.1.1.4.0";
    private static final String OID_SYS_NAME = "1.3.6.1.2.1.1.5.0";
    private static final String OID_SYS_LOCATION = "1.3.6.1.2.1.1.6.0";

    // OIDs Interfaces
    private static final String OID_IF_DESCR = "1.3.6.1.2.1.2.2.1.2";
    private static final String OID_IF_TYPE = "1.3.6.1.2.1.2.2.1.3";
    private static final String OID_IF_MTU = "1.3.6.1.2.1.2.2.1.4";
    private static final String OID_IF_SPEED = "1.3.6.1.2.1.2.2.1.5";
    private static final String OID_IF_PHYS_ADDRESS = "1.3.6.1.2.1.2.2.1.6";
    private static final String OID_IF_ADMIN_STATUS = "1.3.6.1.2.1.2.2.1.7";
    private static final String OID_IF_OPER_STATUS = "1.3.6.1.2.1.2.2.1.8";

    // OIDs IP Address Table
    private static final String OID_IP_AD_ENT_ADDR = "1.3.6.1.2.1.4.20.1.1";
    private static final String OID_IP_AD_ENT_IF_INDEX = "1.3.6.1.2.1.4.20.1.2";
    private static final String OID_IP_AD_ENT_NET_MASK = "1.3.6.1.2.1.4.20.1.3";

    // OID Routing Table (ipRouteDest, ipRouteNextHop, ipRouteMask)
    private static final String OID_IP_ROUTE_DEST = "1.3.6.1.2.1.4.21.1.1";
    private static final String OID_IP_ROUTE_NEXT_HOP = "1.3.6.1.2.1.4.21.1.7";

    @Override
    public boolean isApplicable(String sysDescr, String sysObjectId) {
        // Esta es la estrategia por defecto, siempre retorna true si no hay otra más
        // específica
        return true;
    }

    @Override
    public void discover(SnmpClient snmp, NetworkDevice device) {
        String ip = device.getIpAddress();

        // 1. Información del Sistema
        device.setSysDescr(snmp.get(ip, OID_SYS_DESCR));
        device.setSysName(snmp.get(ip, OID_SYS_NAME));
        device.setSysLocation(snmp.get(ip, OID_SYS_LOCATION));
        device.setSysContact(snmp.get(ip, OID_SYS_CONTACT));
        device.setSysContact(snmp.get(ip, OID_SYS_CONTACT));
        device.setSysUpTime(snmp.get(ip, OID_SYS_UPTIME));

        // 1b. Identificación de Marca/Modelo
        String sysObjectIdVal = snmp.get(ip, OID_SYS_OBJECT_ID);
        device.setSysObjectId(sysObjectIdVal);
        detectVendorAndModel(device);

        // 2. Interfaces
        Map<String, String> ifDescrs = snmp.walk(ip, OID_IF_DESCR);
        Map<String, String> ifTypes = snmp.walk(ip, OID_IF_TYPE);
        Map<String, String> ifMtus = snmp.walk(ip, OID_IF_MTU);
        Map<String, String> ifSpeeds = snmp.walk(ip, OID_IF_SPEED);
        Map<String, String> ifPhysAddr = snmp.walk(ip, OID_IF_PHYS_ADDRESS);
        Map<String, String> ifAdminStatus = snmp.walk(ip, OID_IF_ADMIN_STATUS);
        Map<String, String> ifOperStatus = snmp.walk(ip, OID_IF_OPER_STATUS);

        // 3. IP Address Mapping to Interface Index
        Map<String, String> ipIfIndex = snmp.walk(ip, OID_IP_AD_ENT_IF_INDEX);
        Map<String, String> ipNetMask = snmp.walk(ip, OID_IP_AD_ENT_NET_MASK);

        // Procesar interfaces
        for (Map.Entry<String, String> entry : ifDescrs.entrySet()) {
            String oid = entry.getKey();
            try {
                // Extraer índice (último componente del OID)
                int index = Integer.parseInt(oid.substring(oid.lastIndexOf('.') + 1));
                String descr = entry.getValue();

                NetworkInterface netIf = new NetworkInterface(index, descr);

                // Configuración extendida
                netIf.setType(ifTypes.get(OID_IF_TYPE + "." + index));
                String mtuStr = ifMtus.get(OID_IF_MTU + "." + index);
                if (mtuStr != null)
                    netIf.setMtu(Integer.parseInt(mtuStr));
                netIf.setSpeed(ifSpeeds.get(OID_IF_SPEED + "." + index));

                // Asignar MAC Address
                String rawMac = ifPhysAddr.get(OID_IF_PHYS_ADDRESS + "." + index);
                netIf.setMacAddress(formatMacAddress(rawMac));

                // Estados
                netIf.setAdminStatus(mapStatus(ifAdminStatus.get(OID_IF_ADMIN_STATUS + "." + index)));
                netIf.setOperStatus(mapStatus(ifOperStatus.get(OID_IF_OPER_STATUS + "." + index)));

                // Buscar IP asociada a esta interfaz
                for (Map.Entry<String, String> ipEntry : ipIfIndex.entrySet()) {
                    if (Integer.parseInt(ipEntry.getValue()) == index) {
                        // El OID key es OID_IP_AD_ENT_IF_INDEX + . + IP
                        String ipOidSuffix = ipEntry.getKey().substring(OID_IP_AD_ENT_IF_INDEX.length() + 1);
                        netIf.setIpAddress(ipOidSuffix);
                        netIf.setSubnetMask(ipNetMask.get(OID_IP_AD_ENT_NET_MASK + "." + ipOidSuffix));
                        break;
                    }
                }

                device.addInterface(netIf);
            } catch (Exception e) {
                // Ignorar error de parsing para una interfaz y continuar
            }
        }

        // 4. Tabla de Rutas (Básico)
        Map<String, String> routes = snmp.walk(ip, OID_IP_ROUTE_NEXT_HOP);
        for (Map.Entry<String, String> route : routes.entrySet()) {
            try {
                String destIp = route.getKey().substring(OID_IP_ROUTE_NEXT_HOP.length() + 1);
                String nextHop = route.getValue();
                device.getRoutingTable().put(destIp, nextHop);
            } catch (Exception e) {
                continue;
            }
        }

        // 5. Tabla de Direcciones MAC (BRIDGE-MIB)
        try {
            fetchMacAddressTable(snmp, ip, device);
        } catch (Exception e) {
            System.err.println("Error obteniendo tabla MAC: " + e.getMessage());
        }

        // 6. VLANs (Q-BRIDGE-MIB simple)
        try {
            fetchVlans(snmp, ip, device);
        } catch (Exception e) {
            System.err.println("Error obteniendo VLANs: " + e.getMessage());
        }
    }

    private void fetchMacAddressTable(SnmpClient snmp, String ip, NetworkDevice device) {
        // dot1dTpFdbAddress
        String oidMacAddress = "1.3.6.1.2.1.17.4.3.1.1";
        // dot1dTpFdbPort
        String oidMacPort = "1.3.6.1.2.1.17.4.3.1.2";
        // dot1dBasePortIfIndex (Mapeo de Puerto Bridge -> ifIndex)
        String oidBasePortIfIndex = "1.3.6.1.2.1.17.1.4.1.2";

        Map<String, String> macs = snmp.walk(ip, oidMacAddress);
        Map<String, String> ports = snmp.walk(ip, oidMacPort);
        Map<String, String> bridgeToIfIndex = snmp.walk(ip, oidBasePortIfIndex);

        // Mapa inverso: BridgePort -> ifIndex
        Map<Integer, Integer> bridgePortMap = new HashMap<>();
        for (Map.Entry<String, String> entry : bridgeToIfIndex.entrySet()) {
            try {
                int bridgePort = Integer.parseInt(entry.getKey().substring(oidBasePortIfIndex.length() + 1));
                int ifIdx = Integer.parseInt(entry.getValue());
                bridgePortMap.put(bridgePort, ifIdx);
            } catch (Exception e) {
                continue;
            }
        }

        for (Map.Entry<String, String> entry : macs.entrySet()) {
            try {
                String suffix = entry.getKey().substring(oidMacAddress.length() + 1);
                String mac = formatMacAddress(entry.getValue());

                String portVal = ports.get(oidMacPort + "." + suffix);
                if (portVal != null) {
                    int bridgePort = Integer.parseInt(portVal);
                    Integer ifIndex = bridgePortMap.get(bridgePort);

                    if (ifIndex != null) {
                        device.getMacAddressTable().computeIfAbsent(ifIndex, k -> new ArrayList<>()).add(mac);
                    }
                }
            } catch (Exception e) {
                continue;
            }
        }
    }

    private void fetchVlans(SnmpClient snmp, String ip, NetworkDevice device) {
        // dot1qVlanStaticName
        String oidVlanName = "1.3.6.1.2.1.17.7.1.4.3.1.1";

        Map<String, String> vlanNames = snmp.walk(ip, oidVlanName);
        for (Map.Entry<String, String> entry : vlanNames.entrySet()) {
            try {
                // El último número del OID es el VLAN ID
                String vlanIdStr = entry.getKey().substring(oidVlanName.length() + 1);
                String name = entry.getValue();
                device.getVlans().add("VLAN " + vlanIdStr + ": " + name);
            } catch (Exception e) {
                continue;
            }
        }
    }

    private String formatMacAddress(String raw) {
        if (raw == null || raw.isEmpty())
            return "";
        if (raw.contains(":"))
            return raw;
        // Hex string cleaning implies more logic, but this is a stub for raw data
        return raw;
    }

    private String mapStatus(String status) {
        if ("1".equals(status))
            return "UP";
        if ("2".equals(status))
            return "DOWN";
        if ("3".equals(status))
            return "TESTING";
        return "UNKNOWN (" + status + ")";
    }

    private void detectVendorAndModel(NetworkDevice device) {
        String oid = device.getSysObjectId();
        String descr = device.getSysDescr();

        String vendor = "Desconocido";
        String model = "Desconocido";

        if (oid != null) {
            if (oid.contains(".1.3.6.1.4.1.9.")) {
                vendor = "Cisco";
            } else if (oid.contains(".1.3.6.1.4.1.2011.")) {
                vendor = "Huawei";
            } else if (oid.contains(".1.3.6.1.4.1.2636.")) {
                vendor = "Juniper";
            } else if (oid.contains(".1.3.6.1.4.1.8072.")) {
                vendor = "Linux (Net-SNMP)";
            } else if (oid.contains(".1.3.6.1.4.1.11.")) {
                vendor = "HP / HPE";
            } else if (oid.contains(".1.3.6.1.4.1.43.")) {
                vendor = "3Com";
            } else if (oid.contains(".1.3.6.1.4.1.14823.")) {
                vendor = "Aruba";
            } else if (oid.contains(".1.3.6.1.4.1.311.")) {
                vendor = "Microsoft";
            } else if (oid.contains(".1.3.6.1.4.1.171.")) {
                vendor = "D-Link";
            } else if (oid.contains(".1.3.6.1.4.1.1916.")) {
                vendor = "Extreme Networks";
            } else if (oid.contains(".1.3.6.1.4.1.2007.")) {
                vendor = "Teldat";
            } else if (oid.contains(".1.3.6.1.4.1.2496.")) {
                vendor = "Asus";
            } else if (oid.contains(".1.3.6.1.4.1.14125.")) {
                vendor = "EnGenius";
            } else if (oid.contains(".1.3.6.1.4.1.63.")) {
                vendor = "Apple";
            }
        }

        // Si no detectamos por OID, intentamos buscar en la descripción
        if ("Desconocido".equals(vendor) && descr != null) {
            String lowerDescr = descr.toLowerCase();
            if (lowerDescr.contains("cisco"))
                vendor = "Cisco";
            else if (lowerDescr.contains("huawei"))
                vendor = "Huawei";
            else if (lowerDescr.contains("juniper"))
                vendor = "Juniper";
            else if (lowerDescr.contains("aruba"))
                vendor = "Aruba";
            else if (lowerDescr.contains("3com"))
                vendor = "3Com";
            else if (lowerDescr.contains("hewlett") || lowerDescr.contains("hpe"))
                vendor = "HP / HPE";
            else if (lowerDescr.contains("linux"))
                vendor = "Linux";
            else if (lowerDescr.contains("windows"))
                vendor = "Windows";
            else if (lowerDescr.contains("d-link") || lowerDescr.contains("dlink"))
                vendor = "D-Link";
            else if (lowerDescr.contains("extreme"))
                vendor = "Extreme Networks";
            else if (lowerDescr.contains("teldat"))
                vendor = "Teldat";
            else if (lowerDescr.contains("tenda"))
                vendor = "Tenda";
            else if (lowerDescr.contains("asus"))
                vendor = "Asus";
            else if (lowerDescr.contains("engenius"))
                vendor = "EnGenius";
            else if (lowerDescr.contains("apple") || lowerDescr.contains("macos") || lowerDescr.contains("mac os"))
                vendor = "Apple";
        }

        // Intento básico de extraer modelo del sysDescr si es Cisco
        // Ejemplo Cisco: "Cisco IOS Software, C2960 Software..."
        if ("Cisco".equals(vendor) && descr != null) {
            if (descr.contains(",")) {
                String[] parts = descr.split(",");
                if (parts.length > 1) {
                    model = parts[1].trim();
                    // Si queremos ser menos agresivos o la prueba espera el string completo,
                    // dejamos de reemplazar
                }
            }
        } else if (descr != null && descr.length() < 100) {
            // Si la descripción es corta, úsala como modelo
            model = descr;
        }

        device.setVendor(vendor);
        device.setModel(model);
    }
}
