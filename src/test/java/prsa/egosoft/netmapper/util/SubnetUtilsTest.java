package prsa.egosoft.netmapper.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class SubnetUtilsTest {

    @Test
    public void testCompareIps() {
        // Basic Cases
        assertTrue(SubnetUtils.compareIps("192.168.1.1", "192.168.1.2") < 0);
        assertTrue(SubnetUtils.compareIps("192.168.1.2", "192.168.1.1") > 0);
        assertEquals(0, SubnetUtils.compareIps("192.168.1.1", "192.168.1.1"));

        // Different Octets
        assertTrue(SubnetUtils.compareIps("10.0.0.1", "192.168.1.1") < 0);
        assertTrue(SubnetUtils.compareIps("192.168.0.255", "192.168.1.1") < 0);

        // Edge Cases
        assertTrue(SubnetUtils.compareIps("0.0.0.0", "255.255.255.255") < 0);

        // Invalid inputs (Fallback to String compare)
        assertTrue(SubnetUtils.compareIps("abc", "def") < 0);
    }
}
