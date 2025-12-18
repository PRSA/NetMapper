package prsa.egosoft.netmapper.util;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class NetworkDiscoveryUtilsTest {

    @Test
    public void testDiscoverLocalNetworks() {
        // This test will run on the actual machine and might vary.
        // We mainly want to ensure it doesn't crash and returns something if there are
        // interfaces.
        List<String> networks = NetworkDiscoveryUtils.discoverLocalNetworks();
        assertNotNull(networks);
        for (String network : networks) {
            assertTrue(network.matches("\\d+\\.\\d+\\.\\d+\\.\\d+/\\d+"));
            System.out.println("Discovered network: " + network);
        }
    }

    // Since NetworkDiscoveryUtils.NetworkInfo is private, we can't easily test the
    // filtering logic directly
    // without making it package-private or using reflection.
    // Let's add a public testable method for the logic if needed, but for now, the
    // discovery itself is the main thing.
}
