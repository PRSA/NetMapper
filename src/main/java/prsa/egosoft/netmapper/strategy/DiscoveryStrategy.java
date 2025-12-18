package prsa.egosoft.netmapper.strategy;

import prsa.egosoft.netmapper.core.SnmpClient;
import prsa.egosoft.netmapper.model.NetworkDevice;

/**
 * Interfaz para implementar diferentes estrategias de descubrimiento
 * dependiendo del fabricante o tipo de dispositivo.
 */
public interface DiscoveryStrategy {

    /**
     * Determina si esta estrategia es aplicable para el dispositivo dado
     * basándose en su sysDescr o sysObjectId.
     */
    boolean isApplicable(String sysDescr, String sysObjectId);

    /**
     * Ejecuta el descubrimiento de información del dispositivo.
     */
    void discover(SnmpClient snmpClient, NetworkDevice device);
}
