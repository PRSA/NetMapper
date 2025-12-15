package com.netmapper.test;

import com.netmapper.core.SnmpClient;
import com.netmapper.model.NetworkDevice;
import com.netmapper.model.NetworkInterface;
import com.netmapper.strategy.StandardMibStrategy;

/**
 * Test program to verify Linux VLAN detection from interface names
 */
public class TestLinuxVlanDetection {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java TestLinuxVlanDetection <ip> [community]");
            System.out.println("Example: java TestLinuxVlanDetection 192.168.100.186 public");
            System.exit(1);
        }

        String ip = args[0];
        String community = args.length > 1 ? args[1] : "public";

        System.out.println("=== Testing Linux VLAN Detection ===");
        System.out.println("Target: " + ip);
        System.out.println("Community: " + community);
        System.out.println();

        try {
            SnmpClient snmp = new SnmpClient(community);
            NetworkDevice device = new NetworkDevice(ip);
            StandardMibStrategy strategy = new StandardMibStrategy();

            System.out.println("Discovering device...");
            strategy.discover(snmp, device);

            System.out.println("\n=== RESULTS ===");
            System.out.println("Device: " + device.getSysName());
            System.out.println("Vendor: " + device.getVendor());
            System.out.println();

            System.out.println("Interfaces:");
            for (NetworkInterface ni : device.getInterfaces()) {
                System.out.println("  - " + ni.getDescription() + " (Index: " + ni.getIndex() + ")");
                if (ni.getUntaggedVlanId() > 0) {
                    System.out.println("    VLAN: " + ni.getUntaggedVlanId());
                }
            }

            System.out.println();
            System.out.println("VLANs detected:");
            for (String vlan : device.getVlans()) {
                System.out.println("  - " + vlan);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
