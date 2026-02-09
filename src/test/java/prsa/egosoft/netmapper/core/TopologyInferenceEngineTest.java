package prsa.egosoft.netmapper.core;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import prsa.egosoft.netmapper.model.DetectedEndpoint;
import prsa.egosoft.netmapper.model.NetworkDevice;
import prsa.egosoft.netmapper.model.NetworkDevice.DeviceType;

import java.util.*;

public class TopologyInferenceEngineTest {

    private TopologyInferenceEngine engine;

    @Before
    public void setUp() {
        engine = new TopologyInferenceEngine();
    }

    @Test
    public void testInferShadowNodesFromArp() {
        // Setup Router with ARP table
        NetworkDevice router = new NetworkDevice("10.0.0.1");
        router.setSysName("CoreRouter");
        router.setTypeEnum(DeviceType.ROUTER);

        List<DetectedEndpoint> arpEntries = new ArrayList<>();
        arpEntries.add(new DetectedEndpoint("AA:BB:CC:DD:EE:FF", "10.0.0.50", "TestVendor"));

        Map<Integer, List<DetectedEndpoint>> macTable = new HashMap<>();
        macTable.put(1, arpEntries);
        router.setMacAddressTable(macTable); // Using MacTable field to mock ARP storage per service impl

        Map<String, NetworkDevice> devices = new HashMap<>();
        devices.put(router.getIpAddress(), router);

        // Run Inference
        Map<String, NetworkDevice> result = engine.inferShadowNodes(devices);

        // Verify
        Assert.assertTrue("Should contain original router", result.containsKey("10.0.0.1"));
        Assert.assertTrue("Should contain shadow node", result.containsKey("10.0.0.50"));

        NetworkDevice shadow = result.get("10.0.0.50");
        Assert.assertEquals(DeviceType.SHADOW_HOST, shadow.getTypeEnum());
        Assert.assertEquals("TestVendor", shadow.getVendor());
    }

    @Test
    public void testL2Fusion() {
        // Setup Switch with FDB (MAC only, no IP known)
        NetworkDevice sw = new NetworkDevice("10.0.0.2");
        sw.setTypeEnum(DeviceType.SWITCH);

        List<DetectedEndpoint> fdbEntries = new ArrayList<>();
        fdbEntries.add(new DetectedEndpoint("11:22:33:44:55:66", null, "UnknownVendor"));

        Map<Integer, List<DetectedEndpoint>> fdb = new HashMap<>();
        fdb.put(5, fdbEntries);
        sw.setMacAddressTable(fdb);

        Map<String, NetworkDevice> devices = new HashMap<>();
        devices.put(sw.getIpAddress(), sw);

        // Run Inference
        Map<String, NetworkDevice> result = engine.inferShadowNodes(devices);

        // Verify
        Assert.assertTrue("Should contain shadow device by MAC", result.containsKey("11:22:33:44:55:66"));
        NetworkDevice shadow = result.get("11:22:33:44:55:66");
        Assert.assertEquals(DeviceType.SHADOW_DEVICE, shadow.getTypeEnum());
    }

    @Test
    public void testTriangulatePhysicalLocation_EdgeFound() {
        NetworkDevice sw = new NetworkDevice("10.0.0.2");
        List<DetectedEndpoint> fdbEntries = new ArrayList<>();
        fdbEntries.add(new DetectedEndpoint("AA:AA:AA:AA:AA:AA", null, null));

        Map<Integer, List<DetectedEndpoint>> fdb = new HashMap<>();
        fdb.put(10, fdbEntries); // Port 10 has target MAC
        sw.setMacAddressTable(fdb);

        // No uplinks

        TopologyInferenceEngine.LocationResult result = engine.triangulatePhysicalLocation(
                "AA:AA:AA:AA:AA:AA", Collections.singletonList(sw));

        Assert.assertEquals(TopologyInferenceEngine.LocationResult.Type.EDGE_PORT_FOUND, result.type);
        Assert.assertEquals(10, result.candidates.get(0).portIndex);
    }

    @Test
    public void testTriangulatePhysicalLocation_IgnoreUplink() {
        NetworkDevice sw = new NetworkDevice("10.0.0.2");
        List<DetectedEndpoint> fdbEntries = new ArrayList<>();
        fdbEntries.add(new DetectedEndpoint("AA:AA:AA:AA:AA:AA", null, null));

        Map<Integer, List<DetectedEndpoint>> fdb = new HashMap<>();
        fdb.put(48, fdbEntries); // Port 48 has target MAC
        sw.setMacAddressTable(fdb);

        // Mark Port 48 as Uplink in the model
        prsa.egosoft.netmapper.model.NetworkInterface iface = new prsa.egosoft.netmapper.model.NetworkInterface(48,
                "Port 48");
        iface.setRole(prsa.egosoft.netmapper.model.NetworkInterface.PortRole.UPLINK);
        sw.addInterface(iface);

        TopologyInferenceEngine.LocationResult result = engine.triangulatePhysicalLocation(
                "AA:AA:AA:AA:AA:AA", Collections.singletonList(sw));

        // Result should be UNKNOWN because it was on an UPLINK port (logic inside
        // triangulate now checks role)
        Assert.assertEquals(TopologyInferenceEngine.LocationResult.Type.UNKNOWN, result.type);
    }

    @Test
    public void testUnmanagedSwitchDetection() {
        NetworkDevice sw = new NetworkDevice("10.0.0.2");
        List<DetectedEndpoint> fdbEntries = new ArrayList<>();
        fdbEntries.add(new DetectedEndpoint("AA:AA:AA:AA:AA:AA", null, null)); // Target
        fdbEntries.add(new DetectedEndpoint("BB:BB:BB:BB:BB:BB", null, null));
        fdbEntries.add(new DetectedEndpoint("CC:CC:CC:CC:CC:CC", null, null));
        fdbEntries.add(new DetectedEndpoint("DD:DD:DD:DD:DD:DD", null, null));

        Map<Integer, List<DetectedEndpoint>> fdb = new HashMap<>();
        fdb.put(5, fdbEntries); // Port 5 has 4 MACs (> 3)
        sw.setMacAddressTable(fdb);

        // Result should be SHARED because of > 3 MACs on port 5

        TopologyInferenceEngine.LocationResult result = engine.triangulatePhysicalLocation(
                "AA:AA:AA:AA:AA:AA", Collections.singletonList(sw));

        Assert.assertEquals(TopologyInferenceEngine.LocationResult.Type.SHARED_SEGMENT_FOUND, result.type);
        Assert.assertTrue(result.candidates.get(0).isSharedSegment);
    }
}
