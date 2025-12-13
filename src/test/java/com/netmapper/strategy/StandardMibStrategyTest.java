package com.netmapper.strategy;

import com.netmapper.core.SnmpClient;
import com.netmapper.model.NetworkDevice;
import com.netmapper.model.NetworkInterface;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class StandardMibStrategyTest {

    @Mock
    private SnmpClient snmpClient;

    private StandardMibStrategy strategy;
    private NetworkDevice device;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        strategy = new StandardMibStrategy();
        device = new NetworkDevice("192.168.1.1");
    }

    @Test
    public void testDiscoverSystemInfo() {
        // Mock responses for System Group
        when(snmpClient.get(anyString(), eq("1.3.6.1.2.1.1.1.0")))
                .thenReturn("Cisco IOS Software, C2960 Software (C2960-LANBASEK9-M), Version 12.2");
        when(snmpClient.get(anyString(), eq("1.3.6.1.2.1.1.2.0"))).thenReturn("1.3.6.1.4.1.9.1.564"); // Cisco OID
        when(snmpClient.get(anyString(), eq("1.3.6.1.2.1.1.5.0"))).thenReturn("Switch-Test");
        when(snmpClient.get(anyString(), eq("1.3.6.1.2.1.1.6.0"))).thenReturn("Lab");

        // Mock responses for Walks (empty for cleanliness in this specific test)
        when(snmpClient.walk(anyString(), anyString())).thenReturn(new HashMap<>());

        strategy.discover(snmpClient, device);

        assertEquals("Switch-Test", device.getSysName());
        assertEquals("Lab", device.getSysLocation());

        // Verificar detección de marca y modelo
        assertEquals("Cisco", device.getVendor());
        assertEquals("C2960 Software (C2960-LANBASEK9-M)", device.getModel());
    }

    @Test
    public void testDiscoverArubaVendor() {
        // Mock OID para Aruba
        when(snmpClient.get(anyString(), eq("1.3.6.1.2.1.1.1.0")))
                .thenReturn("ArubaOS (MODEL: 2930F-24G-PoE+), Version KA.16.04.0008");
        when(snmpClient.get(anyString(), eq("1.3.6.1.2.1.1.2.0"))).thenReturn("1.3.6.1.4.1.14823.1.1.1"); // Aruba
                                                                                                          // Enterprise
                                                                                                          // OID
        when(snmpClient.walk(anyString(), anyString())).thenReturn(new HashMap<>());

        strategy.discover(snmpClient, device);

        assertEquals("Aruba", device.getVendor());
        assertEquals("ArubaOS (MODEL: 2930F-24G-PoE+), Version KA.16.04.0008", device.getModel());
    }

    @Test
    public void testDiscoverInterfaces() {
        // Setup mock data for interfaces
        Map<String, String> ifDescr = new HashMap<>();
        ifDescr.put("1.3.6.1.2.1.2.2.1.2.1", "FastEthernet0/1");
        ifDescr.put("1.3.6.1.2.1.2.2.1.2.2", "FastEthernet0/2");

        Map<String, String> ifPhysAddr = new HashMap<>();
        ifPhysAddr.put("1.3.6.1.2.1.2.2.1.6.1", "00:11:22:33:44:55");

        Map<String, String> ifAdminStatus = new HashMap<>();
        ifAdminStatus.put("1.3.6.1.2.1.2.2.1.7.1", "1"); // UP
        ifAdminStatus.put("1.3.6.1.2.1.2.2.1.7.2", "2"); // DOWN

        // Wire mocks
        when(snmpClient.walk(anyString(), eq("1.3.6.1.2.1.2.2.1.2"))).thenReturn(ifDescr);
        when(snmpClient.walk(anyString(), eq("1.3.6.1.2.1.2.2.1.6"))).thenReturn(ifPhysAddr);
        when(snmpClient.walk(anyString(), eq("1.3.6.1.2.1.2.2.1.7"))).thenReturn(ifAdminStatus);
        when(snmpClient.walk(anyString(), eq("1.3.6.1.2.1.2.2.1.8"))).thenReturn(new HashMap<>()); // OperStatus empty

        strategy.discover(snmpClient, device);

        List<NetworkInterface> interfaces = device.getInterfaces();
        assertEquals(2, interfaces.size());

        // Verify Interface 1
        NetworkInterface int1 = interfaces.stream().filter(i -> i.getIndex() == 1).findFirst().orElse(null);
        assertNotNull(int1);
        assertEquals("FastEthernet0/1", int1.getDescription());
        assertEquals("00:11:22:33:44:55", int1.getMacAddress());
        assertEquals("UP", int1.getAdminStatus());
    }

    @Test
    public void testVlanDiscovery() {
        Map<String, String> vlanNames = new HashMap<>();
        vlanNames.put("1.3.6.1.2.1.17.7.1.4.3.1.1.1", "default");
        vlanNames.put("1.3.6.1.2.1.17.7.1.4.3.1.1.10", "management");

        when(snmpClient.walk(anyString(), eq("1.3.6.1.2.1.17.7.1.4.3.1.1"))).thenReturn(vlanNames);

        strategy.discover(snmpClient, device);

        List<String> vlans = device.getVlans();
        assertEquals(2, vlans.size());
        assertTrue(vlans.contains("VLAN 1: default"));
        assertTrue(vlans.contains("VLAN 10: management"));
    }
}
