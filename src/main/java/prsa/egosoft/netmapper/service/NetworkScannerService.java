package prsa.egosoft.netmapper.service;

import prsa.egosoft.netmapper.Main;
import prsa.egosoft.netmapper.core.SnmpClient;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.strategy.DiscoveryStrategy;
import prsa.egosoft.netmapper.strategy.StandardMibStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.List;
import java.util.Map;

import prsa.egosoft.netmapper.util.SubnetUtils;
import prsa.egosoft.netmapper.scan.ArpScanner;
import prsa.egosoft.netmapper.scan.PcapArpScanner;

/**
 * Servicio principal para coordinar el escaneo de dispositivos.
 */
public class NetworkScannerService {
    private static final Logger logger = LoggerFactory.getLogger(NetworkScannerService.class);
    private ExecutorService executorService;

    public NetworkScannerService() {
        this.executorService = Executors.newFixedThreadPool(300); // 300 threads to process /24 in one pass
    }

    /**
     * Escanea un rango de red (CIDR o IP única). Usa la interfaz de red por
     * defecto.
     */
    public void scanNetwork(String cidrInput, String community, Consumer<NetworkDevice> onSuccess,
            Consumer<String> onError) {
        scanNetwork(cidrInput, community, onSuccess, onError, null, null, null);
    }

    public void scanNetwork(String cidrInput, String community, Consumer<NetworkDevice> onSuccess,
            Consumer<String> onError, Runnable onComplete) {
        scanNetwork(cidrInput, community, onSuccess, onError, null, null, onComplete);
    }

    /**
     * Escanea un rango de red (CIDR o IP única) usando una interfaz de red
     * específica y opcionalmente vinculando SNMP a una dirección local.
     */
    public void scanNetwork(String cidrInput, String community, Consumer<NetworkDevice> onSuccess,
            Consumer<String> onError, String interfaceName) {
        scanNetwork(cidrInput, community, onSuccess, onError, interfaceName, null, null);
    }

    /**
     * Escanea un rango de red (CIDR o IP única) usando una interfaz de red
     * específica y vinculando SNMP a una dirección local.
     */
    public void scanNetwork(String cidrInput, String community, Consumer<NetworkDevice> onSuccess,
            Consumer<String> onError, String interfaceName, String localAddress, Runnable onComplete) {
        new Thread(() -> {
            List<String> ips = SubnetUtils.getIpList(cidrInput);
            if (ips.isEmpty()) {
                if (onError != null) {
                    onError.accept(prsa.egosoft.netmapper.i18n.Messages.getString("message.error_invalid_format",
                            cidrInput));
                }
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }

            logger.info("Starting network scan: {} IPs detected in range {}", ips.size(), cidrInput);

            // 0. Active ARP Scan (Pcap4J) - Blocking in this background thread
            Map<String, String> activeArpMap = java.util.Collections.emptyMap();
            if (Main.IS_ADMIN) {
                try {
                    ArpScanner scanner = new PcapArpScanner();
                    activeArpMap = scanner.scan(ips, interfaceName);
                    logger.info("Active ARP Scan detected {} devices.", activeArpMap.size());
                } catch (Exception e) {
                    logger.warn("Could not perform active ARP scan (Pcap4J): {}", e.getMessage());
                }
            }

            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(ips.size());

            for (String ip : ips) {
                String knownMac = activeArpMap.get(ip);
                scanDevice(ip, community, device -> {
                    if (onSuccess != null) {
                        onSuccess.accept(device);
                    }
                    latch.countDown();
                }, error -> {
                    if (onError != null) {
                        onError.accept(error);
                    }
                    latch.countDown();
                }, knownMac, localAddress);
            }

            try {
                // Wait for all devices to be processed
                if (!latch.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    logger.warn("Scan timed out before all devices were processed");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Scan interrupted", e);
            }

            if (onComplete != null) {
                onComplete.run();
            }
        }).start();
    }

    public void scanDevice(String ip, String community, Consumer<NetworkDevice> onSuccess, Consumer<String> onError) {
        scanDevice(ip, community, onSuccess, onError, null, null);
    }

    /**
     * Escanea un único dispositivo IP de forma asíncrona.
     */
    public void scanDevice(String ip, String community, Consumer<NetworkDevice> onSuccess, Consumer<String> onError,
            String knownMac, String localAddress) {
        executorService.submit(() -> {
            SnmpClient client = null;
            try {
                // logger.info("Scanning {}", ip); // Reduce log noise for bulk scans
                client = new SnmpClient(community, localAddress);

                // Crear dispositivo
                NetworkDevice device = new NetworkDevice(ip);

                // Si tenemos MAC del escaneo activo, la asignamos directamente
                if (knownMac != null) {
                    device.setVendor(prsa.egosoft.netmapper.util.MacVendorUtils.getVendor(knownMac));
                    prsa.egosoft.netmapper.model.NetworkInterface ni = new prsa.egosoft.netmapper.model.NetworkInterface(
                            0, prsa.egosoft.netmapper.i18n.Messages.getString("technical.active_arp"));
                    ni.setMacAddress(knownMac);
                    ni.setIpAddress(ip);
                    device.addInterface(ni);
                }

                // Cadena de estrategias: ARP primero (rápido), luego SNMP (detallado)
                DiscoveryStrategy arpStrategy = new prsa.egosoft.netmapper.strategy.ArpDiscoveryStrategy();
                DiscoveryStrategy snmpStrategy = new StandardMibStrategy();

                // 1. ARP Discovery (rápido)
                if (Main.IS_ADMIN && arpStrategy.isApplicable(null, null)) {
                    arpStrategy.discover(client, device);
                }

                // 2. SNMP Discovery (detallado)
                // Usamos client para SNMP
                if (snmpStrategy.isApplicable(device.getSysDescr(), device.getSysObjectId())) {
                    snmpStrategy.discover(client, device);
                }

                // Solo reportar éxito si el dispositivo respondió a algo (ARP o SNMP)
                if (device.getSysDescr() != null || !device.getInterfaces().isEmpty() || device.getVendor() != null) {
                    onSuccess.accept(device);
                } else {
                    logger.debug("Dispositivo {} no respondió ni a ARP ni a SNMP.", ip);
                }

            } catch (Exception e) {
                // En escaneo masivo, es normal que muchas IPs no respondan (timeout).
                // Podríamos filtrar errores de timeout para no saturar la UI, o dejar que la UI
                // decida.
                // Para single scan (scanDevice invocado directamente) queremos ver el error.
                // Para bulk, tal vez solo loguear.
                // Decisión: propagar error. La UI puede ignorar timeouts si quiere.
                logger.error("Error scanning device {}", ip, e);
                onError.accept(
                        prsa.egosoft.netmapper.i18n.Messages.getString("message.error_accessing", ip, e.getMessage()));
            } finally {
                if (client != null) {
                    try {
                        client.stop();
                    } catch (IOException e) {
                        logger.error("Error closing SNMP client", e);
                    }
                }
            }
        });
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
