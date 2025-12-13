package com.netmapper.strategy;

import com.netmapper.core.SnmpClient;
import com.netmapper.model.NetworkDevice;

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
