package prsa.egosoft.netmapper.strategy;

import prsa.egosoft.netmapper.core.SnmpClient;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkInterface;
import prsa.egosoft.netmapper.model.DetectedEndpoint;
import prsa.egosoft.netmapper.util.MacVendorUtils;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

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
    private static final String OID_SYS_SERVICES = "1.3.6.1.2.1.1.7.0";

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
        String sysDescr = snmp.get(ip, OID_SYS_DESCR);
        if (sysDescr == null) {
            return; // Early departure: Device is not responding to SNMP
        }
        device.setSysDescr(sysDescr);
        device.setSysName(snmp.get(ip, OID_SYS_NAME));
        device.setSysLocation(snmp.get(ip, OID_SYS_LOCATION));
        device.setSysContact(snmp.get(ip, OID_SYS_CONTACT));
        device.setSysUpTime(snmp.get(ip, OID_SYS_UPTIME));

        // 1b. Identificación de Marca/Modelo y Tipo
        String sysObjectIdVal = snmp.get(ip, OID_SYS_OBJECT_ID);
        device.setSysObjectId(sysObjectIdVal);

        String sysServicesStr = snmp.get(ip, OID_SYS_SERVICES);
        int sysServices = 0;
        try {
            if (sysServicesStr != null)
                sysServices = Integer.parseInt(sysServicesStr);
        } catch (NumberFormatException ignored) {
        }

        detectVendorAndModel(device);
        device.setSysServices(sysServices);
        device.setFormattedServices(formatSysServices(sysServices));
        detectDeviceType(device, sysServices);

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

        // 2b. High Speed Interfaces (ifXTable)
        // ifHighSpeed: 1.3.6.1.2.1.31.1.1.1.15 (Units: Mbps)
        String oidIfHighSpeed = "1.3.6.1.2.1.31.1.1.1.15";
        Map<String, String> ifHighSpeeds = snmp.walk(ip, oidIfHighSpeed);

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

                // Speed Logic: Prefer High Speed if available and > 0, otherwise normal speed
                String highSpeedStr = ifHighSpeeds.get(oidIfHighSpeed + "." + index);
                String speedStr = ifSpeeds.get(OID_IF_SPEED + "." + index);

                long finalSpeed = 0;
                if (highSpeedStr != null) {
                    try {
                        long highSpeedVal = Long.parseLong(highSpeedStr);
                        // ifHighSpeed is in Mbps, convert to bps if you want uniform storage,
                        // but model expects String. Let's format it nicely or store as bps string.
                        // 0 usually means unknown or too small? RFC says 1 Mbps steps.
                        if (highSpeedVal > 0) {
                            finalSpeed = highSpeedVal * 1_000_000L;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }

                if (finalSpeed == 0 && speedStr != null) {
                    try {
                        finalSpeed = Long.parseLong(speedStr);
                        // Case: ifSpeed reports max value (4.29Gbps) for faster links
                        if (finalSpeed == 4294967295L) {
                            // Indicate it's faster, but we don't know without HighSpeed
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }

                if (finalSpeed > 0) {
                    netIf.setSpeed(String.valueOf(finalSpeed));
                } else {
                    netIf.setSpeed(speedStr != null ? speedStr : "0");
                }

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

        // 6b. Fallback: Detect VLANs from interface naming (Linux, etc.)
        try {
            detectVlansFromInterfaceNames(device);
        } catch (Exception e) {
            System.err.println("Error detecting VLANs from interface names: " + e.getMessage());
        }

        // 7. ARP Table (ipNetToMediaTable) - Fallback/Supplement for L3 devices
        try {
            fetchIpNetToMediaTable(snmp, ip, device);
        } catch (Exception e) {
            System.err.println("Error obteniendo ARP Table: " + e.getMessage());
        }
    }

    /**
     * Detects VLANs from Linux-style interface naming (e.g., eth0.100,
     * wlp0s20f3.35)
     * This is a fallback when Q-BRIDGE-MIB is not available.
     */
    private void detectVlansFromInterfaceNames(NetworkDevice device) {
        // Define multiple patterns for different OS conventions
        Pattern[] vlanPatterns = {
                Pattern.compile("^(.+)\\.(\\d{1,4})$"), // Linux/macOS: eth0.100
                Pattern.compile("^vlan(\\d{1,4})$"), // macOS: vlan100
                Pattern.compile("VLAN[\\s:]*?(\\d{1,4})"), // Windows: VLAN 100, VLAN:100
                Pattern.compile("\\(VLAN[\\s:]*?(\\d{1,4})\\)") // Windows Hyper-V: (VLAN 100)
        };

        for (NetworkInterface ni : device.getInterfaces()) {
            String descr = ni.getDescription();
            if (descr == null || descr.isEmpty())
                continue;

            // Skip if already has VLAN assigned (from Q-BRIDGE-MIB)
            if (ni.getUntaggedVlanId() > 0)
                continue;

            // Try each pattern
            for (int i = 0; i < vlanPatterns.length; i++) {
                Matcher matcher = vlanPatterns[i].matcher(descr);
                if (matcher.find()) {
                    try {
                        // Extract VLAN ID (group 1 for standalone, group 2 for dot notation)
                        String vlanStr = matcher.group(matcher.groupCount());
                        int vlanId = Integer.parseInt(vlanStr);

                        // Validate VLAN ID range
                        if (vlanId >= 1 && vlanId <= 4094) {
                            ni.setUntaggedVlanId(vlanId);

                            // Add to device VLAN list
                            String detectionMethod = getDetectionMethod(i);
                            String vlanEntry = "VLAN " + vlanId + " ("
                                    + prsa.egosoft.netmapper.i18n.Messages.getString("vlan.detected_from",
                                            detectionMethod, descr)
                                    + ")";
                            addVlanIfNotExists(device, vlanId, vlanEntry);

                            System.out.println(
                                    "DEBUG: Detected VLAN " + vlanId + " from " + detectionMethod + ": " + descr);
                            break; // Stop after first match
                        }
                    } catch (NumberFormatException e) {
                        // Invalid VLAN ID, try next pattern
                    }
                }
            }
        }
    }

    private String getDetectionMethod(int patternIndex) {
        switch (patternIndex) {
            case 0:
                return prsa.egosoft.netmapper.i18n.Messages.getString("vlan.dot_notation");
            case 1:
                return prsa.egosoft.netmapper.i18n.Messages.getString("vlan.prefix");
            case 2:
                return prsa.egosoft.netmapper.i18n.Messages.getString("vlan.keyword");
            case 3:
                return prsa.egosoft.netmapper.i18n.Messages.getString("vlan.hyperv");
            default:
                return prsa.egosoft.netmapper.i18n.Messages.getString("vlan.interface_name");
        }
    }

    private void addVlanIfNotExists(NetworkDevice device, int vlanId, String vlanEntry) {
        for (String existingVlan : device.getVlans()) {
            if (existingVlan.startsWith("VLAN " + vlanId)) {
                return; // Already exists
            }
        }
        device.getVlans().add(vlanEntry);
    }

    private void fetchIpNetToMediaTable(SnmpClient snmp, String ip, NetworkDevice device) {
        // ipNetToMediaPhysAddress: 1.3.6.1.2.1.4.22.1.2
        // Key format: .ifIndex.ip1.ip2.ip3.ip4
        String oidArpPhysAddress = "1.3.6.1.2.1.4.22.1.2";

        System.out.println("DEBUG: Fetching ARP Table (ipNetToMediaTable)...");

        Map<String, String> arpEntries = snmp.walk(ip, oidArpPhysAddress);
        System.out.println("DEBUG: ARP Table Size: " + arpEntries.size());

        for (Map.Entry<String, String> entry : arpEntries.entrySet()) {
            try {
                String oidSuffix = entry.getKey().substring(oidArpPhysAddress.length() + 1);
                // Suffix is ifIndex.ip.ip.ip.ip
                String[] parts = oidSuffix.split("\\.");
                if (parts.length < 5)
                    continue;

                int ifIndex = Integer.parseInt(parts[0]);
                String mac = formatMacAddress(entry.getValue());
                String vendor = MacVendorUtils.getVendor(mac);

                String entryIp = parts[1] + "." + parts[2] + "." + parts[3] + "." + parts[4];

                DetectedEndpoint endpoint = new DetectedEndpoint(mac, entryIp, vendor);
                device.getMacAddressTable().computeIfAbsent(ifIndex, k -> new ArrayList<>()).add(endpoint);

            } catch (Exception e) {
                // Ignore parse errors for single entry
            }
        }
    }

    private void fetchMacAddressTable(SnmpClient snmp, String ip, NetworkDevice device) {
        // dot1dTpFdbAddress
        String oidMacAddress = "1.3.6.1.2.1.17.4.3.1.1";
        // dot1dTpFdbPort
        String oidMacPort = "1.3.6.1.2.1.17.4.3.1.2";

        Map<Integer, Integer> bridgePortMap = getBridgePortToIfIndexMap(snmp, ip);
        boolean hasBridgeMap = !bridgePortMap.isEmpty();

        System.out.println("DEBUG: Bridge Port Map size: " + bridgePortMap.size());
        if (!bridgePortMap.isEmpty()) {
            System.out.println("DEBUG: Sample Mapping: " + bridgePortMap.entrySet().iterator().next());
        }

        Map<String, String> macs = snmp.walk(ip, oidMacAddress);
        Map<String, String> ports = snmp.walk(ip, oidMacPort);

        System.out.println("DEBUG: MAC Table Size: " + macs.size());
        System.out.println("DEBUG: Port Table Size: " + ports.size());

        int mappedCount = 0;

        for (Map.Entry<String, String> entry : macs.entrySet()) {
            try {
                String suffix = entry.getKey().substring(oidMacAddress.length() + 1);
                String mac = formatMacAddress(entry.getValue());
                String vendor = MacVendorUtils.getVendor(mac);

                String portVal = ports.get(oidMacPort + "." + suffix);
                if (portVal != null) {
                    int bridgePort = Integer.parseInt(portVal);
                    Integer ifIndex = bridgePortMap.get(bridgePort);

                    if (ifIndex == null && !hasBridgeMap) {
                        // Fallback: Si no hay mapa, asumimos que port bridge == ifIndex
                        ifIndex = bridgePort;
                    }

                    if (ifIndex != null) {
                        DetectedEndpoint endpoint = new DetectedEndpoint(mac, null, vendor);
                        device.getMacAddressTable().computeIfAbsent(ifIndex, k -> new ArrayList<>()).add(endpoint);
                        mappedCount++;
                    } else {
                        // System.out.println("DEBUG: Unmapped Bridge Port: " + bridgePort); // Verbose
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing MAC entry: " + e.getMessage());
                continue;
            }
        }
        System.out.println("DEBUG: Total Mapped MACs: " + mappedCount);
    }

    private void fetchVlans(SnmpClient snmp, String ip, NetworkDevice device) {
        // dot1qVlanStaticName: 1.3.6.1.2.1.17.7.1.4.3.1.1
        String oidVlanName = "1.3.6.1.2.1.17.7.1.4.3.1.1";
        // dot1qVlanCurrentEgressPorts: 1.3.6.1.2.1.17.7.1.4.2.1.4
        // Indexado por TimeFilter (0) y VlanIndex
        String oidEgressPorts = "1.3.6.1.2.1.17.7.1.4.2.1.4";

        // Primero obtenemos nombres para listar en el dispositivo (requisito original)
        Map<String, String> vlanNames = snmp.walk(ip, oidVlanName);
        for (Map.Entry<String, String> entry : vlanNames.entrySet()) {
            try {
                String vlanIdStr = entry.getKey().substring(oidVlanName.length() + 1);
                String name = entry.getValue();
                device.getVlans().add("VLAN " + vlanIdStr + ": " + name);
            } catch (Exception e) {
                continue;
            }
        }

        // Obtenemos el mapa de Puerto Bridge -> ifIndex
        // Si falla (vacío), no podemos mapear puertos a interfaces, por lo que salimos
        Map<Integer, Integer> bridgePortMap = getBridgePortToIfIndexMap(snmp, ip);
        boolean hasBridgeMap = !bridgePortMap.isEmpty();

        // 1. VLANs Nativas (PVID) - Untagged
        // dot1qPvid: 1.3.6.1.2.1.17.7.1.4.5.1.1
        // OID Key: .1.3.6.1.2.1.17.7.1.4.5.1.1.{dot1dBasePort}
        String oidPvid = "1.3.6.1.2.1.17.7.1.4.5.1.1";
        Map<String, String> pvids = snmp.walk(ip, oidPvid);

        for (Map.Entry<String, String> entry : pvids.entrySet()) {
            try {
                String oid = entry.getKey();
                int bridgePort = Integer.parseInt(oid.substring(oidPvid.length() + 1));
                int vlanId = Integer.parseInt(entry.getValue());

                Integer ifIndex = bridgePortMap.get(bridgePort);
                if (ifIndex == null && !hasBridgeMap) {
                    ifIndex = bridgePort;
                }

                if (ifIndex != null) {
                    for (NetworkInterface ni : device.getInterfaces()) {
                        if (ni.getIndex() == ifIndex) {
                            ni.setUntaggedVlanId(vlanId);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // Ignorar error de parsing
            }
        }

        // 2. VLANs Etiquetadas (Egress Ports)
        // Ahora procesamos puertos miembros (tagged + untagged teóricamente, pero
        // filtramos)
        Map<String, String> egressPorts = snmp.walk(ip, oidEgressPorts);
        for (Map.Entry<String, String> entry : egressPorts.entrySet()) {
            try {
                String oid = entry.getKey();
                // OID format: ...4.2.1.4.{timeMark}.{vlanId}
                // Asumimos que los dos últimos componentes son TimeMark y VlanId
                String[] parts = oid.split("\\.");
                if (parts.length < 2)
                    continue;

                int vlanId = Integer.parseInt(parts[parts.length - 1]);
                String portListHex = entry.getValue(); // String Hex (ej: "FF 00 ...")

                List<Integer> memberBridgePorts = parsePortList(portListHex);

                for (Integer bridgePort : memberBridgePorts) {
                    Integer ifIndex = bridgePortMap.get(bridgePort);
                    if (ifIndex == null && !hasBridgeMap) {
                        ifIndex = bridgePort;
                    }

                    if (ifIndex != null) {
                        for (NetworkInterface ni : device.getInterfaces()) {
                            if (ni.getIndex() == ifIndex) {
                                // Agregamos a taggedVlans solo si no es la nativa
                                // (Opcional: La lógica de si Egress incluye untagged depende del switch,
                                // pero generalmente sí. Lo agregamos y el consumidor decide,
                                // O lo filtramos si coincide con untaggedVlanId)
                                if (ni.getUntaggedVlanId() != vlanId) {
                                    ni.addTaggedVlan(vlanId);
                                }
                                break;
                            }
                        }
                    }
                }

            } catch (Exception e) {
                continue;
            }
        }
    }

    private Map<Integer, Integer> getBridgePortToIfIndexMap(SnmpClient snmp, String ip) {
        String oidBasePortIfIndex = "1.3.6.1.2.1.17.1.4.1.2";
        Map<String, String> bridgeToIfIndex = snmp.walk(ip, oidBasePortIfIndex);
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
        return bridgePortMap;
    }

    /**
     * Parsea un PortList (OCTET STRING) que representa un mapa de bits de puertos.
     * formato snmp4j toString() usualmente "xx:xx:xx..." o "xx xx xx..."
     */
    private List<Integer> parsePortList(String hexString) {
        List<Integer> ports = new ArrayList<>();
        if (hexString == null || hexString.isEmpty())
            return ports;

        // Limpieza básica: quitar separadores comunes (: o espacio)
        String cleanHex = hexString.replaceAll("[:\\s]", "");

        // Si snmp4j devolvió caracteres ASCII imprimibles en vez de hex, esto fallará.
        // Asumimos comportamiento estándar para OCTET STRING que contiene bytes no
        // imprimibles.
        // Si resultan ser imprimibles (poco probable para bitmap), habría que convertir
        // chars a hex.

        try {
            byte[] bytes = new byte[cleanHex.length() / 2];
            for (int i = 0; i < cleanHex.length(); i += 2) {
                bytes[i / 2] = (byte) ((Character.digit(cleanHex.charAt(i), 16) << 4)
                        + Character.digit(cleanHex.charAt(i + 1), 16));
            }

            for (int i = 0; i < bytes.length; i++) {
                for (int bit = 0; bit < 8; bit++) {
                    if ((bytes[i] & (0x80 >> bit)) != 0) {
                        // Port numbers are 1-based
                        ports.add(i * 8 + bit + 1);
                    }
                }
            }
        } catch (Exception e) {
            // Fallback o log si el formato no era hex
        }
        return ports;
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
            return prsa.egosoft.netmapper.i18n.Messages.getString("interface.status.up");
        if ("2".equals(status))
            return prsa.egosoft.netmapper.i18n.Messages.getString("interface.status.down");
        if ("3".equals(status))
            return prsa.egosoft.netmapper.i18n.Messages.getString("interface.status.testing");
        return prsa.egosoft.netmapper.i18n.Messages.getString("interface.status.unknown", status);
    }

    private void detectVendorAndModel(NetworkDevice device) {
        String oid = device.getSysObjectId();
        String descr = device.getSysDescr();

        String vendor = prsa.egosoft.netmapper.i18n.Messages.getString("device.type.unknown");
        String model = prsa.egosoft.netmapper.i18n.Messages.getString("device.type.unknown");

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
        if (prsa.egosoft.netmapper.i18n.Messages.getString("device.type.unknown").equals(vendor) && descr != null) {
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

    private void detectDeviceType(NetworkDevice device, int sysServices) {
        String descr = device.getSysDescr() != null ? device.getSysDescr().toLowerCase() : "";
        String vendor = device.getVendor() != null ? device.getVendor().toLowerCase() : "";
        String model = device.getModel() != null ? device.getModel().toLowerCase() : "";

        // Default
        String type = prsa.egosoft.netmapper.i18n.Messages.getString("device.type.unknown");

        // Heurística por sysServices (RFC 1213)
        // bit 1: physical, 2: datalink, 3: internet (router), 4: end-to-end, 7:
        // applications
        boolean isRouter = (sysServices & 4) != 0;
        boolean isL2 = (sysServices & 2) != 0;

        if (isRouter) {
            type = prsa.egosoft.netmapper.i18n.Messages.getString("device.type.router");
        } else if (isL2) {
            type = prsa.egosoft.netmapper.i18n.Messages.getString("device.type.switch");
        }

        // Refinamiento por descripción y palabras clave
        if (descr.contains("windows")) {
            type = prsa.egosoft.netmapper.i18n.Messages.getString("device.type.pc_windows");
        } else if (descr.contains("linux") || vendor.contains("linux")) {
            if (type.equals(prsa.egosoft.netmapper.i18n.Messages.getString("device.type.unknown")))
                type = prsa.egosoft.netmapper.i18n.Messages.getString("device.type.pc_linux");
        } else if (descr.contains("iphone") || descr.contains("ipad") || descr.contains("android")) {
            type = prsa.egosoft.netmapper.i18n.Messages.getString("device.type.mobile");
        } else if (descr.contains("jetdirect") || descr.contains("hp laserjet") || descr.contains("epson")
                || descr.contains("printer") || descr.contains("impresora")) {
            type = prsa.egosoft.netmapper.i18n.Messages.getString("device.type.printer");
        } else if (descr.contains("firewall") || descr.contains("asa ") || descr.contains("fortigate")
                || descr.contains("checkpoint")) {
            type = prsa.egosoft.netmapper.i18n.Messages.getString("device.type.firewall");
        } else if (vendor.toLowerCase().contains("cisco") || vendor.toLowerCase().contains("juniper")
                || vendor.toLowerCase().contains("huawei")) {
            if (descr.contains("switch") || model.toLowerCase().contains("catalyst")
                    || model.toLowerCase().contains("nexus")) {
                type = prsa.egosoft.netmapper.i18n.Messages.getString("device.type.switch");
            } else if (descr.contains("router")) {
                type = prsa.egosoft.netmapper.i18n.Messages.getString("device.type.router");
            }
        } else if (vendor.toLowerCase().contains("vmware") || descr.contains("esxi")) {
            type = prsa.egosoft.netmapper.i18n.Messages.getString("device.type.server_virtual");
        }

        // Si sigue siendo desconocido pero tenemos vendor conocido de cliente (Apple,
        // Samsung, etc.)
        if (type.equals(prsa.egosoft.netmapper.i18n.Messages.getString("device.type.unknown"))) {
            if (vendor.toLowerCase().contains("apple") || vendor.toLowerCase().contains("samsung")
                    || vendor.toLowerCase().contains("google")) {
                type = prsa.egosoft.netmapper.i18n.Messages.getString("device.type.pc_mobile");
            }
        }

        device.setDeviceType(type);
    }

    private String formatSysServices(int sysServices) {
        List<String> services = new ArrayList<>();
        if ((sysServices & 1) != 0)
            services.add(prsa.egosoft.netmapper.i18n.Messages.getString("service.physical"));
        if ((sysServices & 2) != 0)
            services.add(prsa.egosoft.netmapper.i18n.Messages.getString("service.datalink"));
        if ((sysServices & 4) != 0)
            services.add(prsa.egosoft.netmapper.i18n.Messages.getString("service.internet"));
        if ((sysServices & 8) != 0)
            services.add(prsa.egosoft.netmapper.i18n.Messages.getString("service.endtoend"));
        if ((sysServices & 64) != 0)
            services.add(prsa.egosoft.netmapper.i18n.Messages.getString("service.applications"));

        return String.join(", ", services);
    }
}
