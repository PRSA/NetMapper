package com.netmapper.core;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TreeUtils;
import org.snmp4j.util.TreeEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cliente SNMP wrapper para simplificar operaciones GET y WALK.
 */
public class SnmpClient {

    private static final Logger logger = LoggerFactory.getLogger(SnmpClient.class);

    private Snmp snmp;
    private String community;
    private static final int RETRIES = 3;
    private static final int TIMEOUT = 3000;

    public SnmpClient(String community) throws IOException {
        this.community = community;
        start();
    }

    private void start() throws IOException {
        TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
        snmp = new Snmp(transport);
        transport.listen();
    }

    public void stop() throws IOException {
        if (snmp != null) {
            snmp.close();
        }
    }

    /**
     * Realiza una operación SNMP GET para un OID específico.
     *
     * @param ip  Dirección IP del dispositivo.
     * @param oid OID a consultar.
     * @return El valor obtenido o null si falla.
     */
    public String get(String ip, String oid) {
        try {
            CommunityTarget target = createTarget(ip);
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid)));
            pdu.setType(PDU.GET);

            ResponseEvent event = snmp.send(pdu, target);
            PDU response = event.getResponse();

            if (response != null && response.getErrorStatus() == PDU.noError) {
                return response.get(0).getVariable().toString();
            } else {
                logger.warn("Error en respuesta SNMP GET para {}: {}", ip,
                        response != null ? response.getErrorStatusText() : "Timeout");
                return null;
            }
        } catch (Exception e) {
            logger.error("Excepción durante SNMP GET a {}", ip, e);
            return null;
        }
    }

    /**
     * Realiza un SNMP WALK para un OID base.
     *
     * @param ip      Dirección IP del dispositivo.
     * @param rootOid OID raíz para el walk.
     * @return Mapa de OID -> Valor.
     */
    public Map<String, String> walk(String ip, String rootOid) {
        Map<String, String> result = new TreeMap<>();
        CommunityTarget target = createTarget(ip);
        TreeUtils treeUtils = new TreeUtils(snmp, new DefaultPDUFactory());

        List<TreeEvent> events = treeUtils.getSubtree(target, new OID(rootOid));

        if (events == null || events.isEmpty()) {
            logger.warn("No se obtuvo respuesta para WALK en {} OID {}", ip, rootOid);
            return result;
        }

        for (TreeEvent event : events) {
            if (event != null) {
                if (event.isError()) {
                    logger.error("Error en SNMP WALK a {}: {}", ip, event.getErrorMessage());
                    continue;
                }

                VariableBinding[] varBindings = event.getVariableBindings();
                if (varBindings != null) {
                    for (VariableBinding vb : varBindings) {
                        result.put(vb.getOid().toString(), vb.getVariable().toString());
                    }
                }
            }
        }
        return result;
    }

    private CommunityTarget createTarget(String ip) {
        Address targetAddress = GenericAddress.parse("udp:" + ip + "/161");
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString(community));
        target.setAddress(targetAddress);
        target.setRetries(RETRIES);
        target.setTimeout(TIMEOUT);
        target.setVersion(SnmpConstants.version2c);
        return target;
    }
}
