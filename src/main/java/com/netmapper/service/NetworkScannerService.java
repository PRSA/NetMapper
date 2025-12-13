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

/**
 * Servicio principal para coordinar el escaneo de dispositivos.
 */
public class NetworkScannerService {
    private static final Logger logger = LoggerFactory.getLogger(NetworkScannerService.class);
    private ExecutorService executorService;

    public NetworkScannerService() {
        this.executorService = Executors.newFixedThreadPool(5); // Pool de 5 hilos
    }

    /**
     * Escanea un único dispositivo IP de forma asíncrona.
     */
    public void scanDevice(String ip, String community, Consumer<NetworkDevice> onSuccess, Consumer<String> onError) {
        executorService.submit(() -> {
            SnmpClient client = null;
            try {
                logger.info("Iniciando escaneo de {}", ip);
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
