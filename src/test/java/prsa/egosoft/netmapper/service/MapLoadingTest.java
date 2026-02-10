package prsa.egosoft.netmapper.service;

import org.junit.Test;
import prsa.egosoft.netmapper.model.NetworkDevice;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class MapLoadingTest {

    @Test
    public void testExportAndLoad() throws IOException {
        NetworkController controller = new NetworkController();
        ExportService exportService = new ExportService();

        File tempFile = File.createTempFile("network_map_test", ".json");
        tempFile.deleteOnExit();

        Map<String, NetworkDevice> originalDevices = new HashMap<>();
        NetworkDevice dev1 = new NetworkDevice("192.168.1.1");
        dev1.setSysName("Router1");
        originalDevices.put(dev1.getIpAddress(), dev1);

        // Export
        exportService.exportToJSON(tempFile, originalDevices);

        // Load
        controller.loadDevicesFromJson(tempFile);

        Map<String, NetworkDevice> loadedDevices = controller.getDiscoveredDevices();

        // Verify
        // Note: processInference might add shadow nodes, but Router1 should be there
        assertTrue("Loaded devices should contain original IP", loadedDevices.containsKey("192.168.1.1"));
        assertEquals("System name should match", "Router1", loadedDevices.get("192.168.1.1").getSysName());
    }

    @Test
    public void testLoadGondomarFormat() throws IOException {
        // This test assumes Gondomar exists in the workspace
        File gondomarFile = new File("network_map_Gondomar.json");
        if (!gondomarFile.exists()) {
            return; // Skip if file not found locally
        }

        NetworkController controller = new NetworkController();
        controller.loadDevicesFromJson(gondomarFile);

        Map<String, NetworkDevice> loadedDevices = controller.getDiscoveredDevices();
        assertFalse("Gondomar map should not be empty", loadedDevices.isEmpty());

        // Check a known device from viewing the file earlier
        assertTrue("Should contain 10.47.10.10", loadedDevices.containsKey("10.47.10.10"));
        assertEquals("ADA-COR-002-SWD1-4", loadedDevices.get("10.47.10.10").getSysName());
    }
}
