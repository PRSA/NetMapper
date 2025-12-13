package com.netmapper.service;

import com.netmapper.core.SnmpClient;
import com.netmapper.model.NetworkDevice;
import com.netmapper.strategy.DiscoveryStrategy;
import com.netmapper.strategy.StandardMibStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.netmapper.util.SubnetUtils; // Import SubnetUtils
import java.util.List;

/**
 * Servicio principal para coordinar el escaneo de dispositivos.
 */
public class NetworkScannerService {
    private static final Logger logger = LoggerFactory.getLogger(NetworkScannerService.class);
    private ExecutorService executorService;

    public NetworkScannerService() {
        this.executorService = Executors.newFixedThreadPool(20); // Aumentado a 20 hilos para escaneos de red
    }

    /**
     * Escanea un rango de red (CIDR o IP única).
     */
    public void scanNetwork(String cidrInput, String community, Consumer<NetworkDevice> onSuccess,
            Consumer<String> onError) {
        List<String> ips = SubnetUtils.getIpList(cidrInput);
        if (ips.isEmpty()) {
            onError.accept("Formato de red inválido: " + cidrInput);
            return;
        }

        logger.info("Iniciando escaneo de red: {} IPs detectadas en rango {}", ips.size(), cidrInput);

        for (String ip : ips) {
            scanDevice(ip, community, onSuccess, onError);
        }
    }

    /**
     * Escanea un único dispositivo IP de forma asíncrona.
     */
    public void scanDevice(String ip, String community, Consumer<NetworkDevice> onSuccess, Consumer<String> onError) {
        executorService.submit(() -> {
            SnmpClient client = null;
            try {
                // logger.info("Escaneando {}", ip); // Reducir ruido en log si son muchas
                client = new SnmpClient(community);

                // Crear dispositivo y estrategia
                NetworkDevice device = new NetworkDevice(ip);
                DiscoveryStrategy strategy = new StandardMibStrategy(); // Por defecto

                // Validar conectividad básica
                if (strategy.isApplicable(null, null)) {
                    strategy.discover(client, device);
                    onSuccess.accept(device);
                }

            } catch (Exception e) {
                // En escaneo masivo, es normal que muchas IPs no respondan (timeout).
                // Podríamos filtrar errores de timeout para no saturar la UI, o dejar que la UI
                // decida.
                // Para single scan (scanDevice invocado directamente) queremos ver el error.
                // Para bulk, tal vez solo loguear.
                // Decisión: propagar error. La UI puede ignorar timeouts si quiere.
                logger.error("Error escaneando device {}", ip, e);
                onError.accept("Error accediendo a " + ip + ": " + e.getMessage());
            } finally {
                if (client != null) {
                    try {
                        client.stop();
                    } catch (IOException e) {
                        logger.error("Error cerrando cliente SNMP", e);
                    }
                }
            }
        });
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
