package prsa.egosoft.netmapper.util;

import java.util.HashMap;
import java.util.Map;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

import java.util.concurrent.ConcurrentHashMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for MAC Address OUI lookup with persistence and online update.
 */
public class MacVendorUtils {

    private static final String CACHE_FILE = "mac_vendors.properties";
    private static final ConcurrentHashMap<String, String> OUI_DB = new ConcurrentHashMap<>();
    private static boolean loaded = false;

    // Load cache from file
    // Load cache from file
    private static synchronized void loadCache() {
        if (loaded)
            return;
        File file = new File(CACHE_FILE);
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int splitIndex = line.indexOf('=');
                    if (splitIndex > 0) {
                        String key = line.substring(0, splitIndex).trim();
                        String value = line.substring(splitIndex + 1).trim();
                        OUI_DB.put(key, value);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error loading vendor cache: " + e.getMessage());
            }
        }
        loaded = true;
    }

    private static synchronized void saveCache(String oui, String vendor) {
        OUI_DB.put(oui, vendor);

        // Use TreeMap to sort by OUI
        Map<String, String> sortedMap = new TreeMap<>(OUI_DB);

        try (PrintWriter writer = new PrintWriter(new FileWriter(CACHE_FILE))) {
            writer.println("# Local Cache of MAC Vendors");
            writer.println("# Format: XX:XX:XX=Vendor Name");

            for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
                writer.println(entry.getKey() + "=" + entry.getValue());
            }
        } catch (IOException e) {
            System.err.println("Error saving vendor cache: " + e.getMessage());
        }
    }

    public static String getVendor(String macAddress) {
        loadCache(); // Ensure loaded

        if (macAddress == null || macAddress.length() < 8) {
            return "Unknown";
        }

        // Normalize MAC: UPPERCASE and use : as separator
        String cleaned = macAddress.toUpperCase().replace("-", ":").replace(".", ":");
        // Ensure format XX:XX:XX
        if (cleaned.length() >= 8) {
            String oui = cleaned.substring(0, 8); // "XX:XX:XX"

            // 1. Check Local Cache
            if (OUI_DB.containsKey(oui)) {
                return OUI_DB.get(oui);
            }

            // 2. Online Lookup
            String vendor = lookupOnline(macAddress); // Pass full MAC often required by APIs
            if (vendor != null && !vendor.isEmpty() && !"Unknown".equals(vendor)) {
                saveCache(oui, vendor);
                return vendor;
            }
        }
        return "Unknown";
    }

    private static String lookupOnline(String mac) {
        // 1. Primary Source: macvendors.com
        try {
            String vendor = queryApi("https://api.macvendors.com/" + mac);
            if (vendor != null && !vendor.trim().isEmpty()) {
                return vendor;
            }
        } catch (Exception e) {
            System.err.println("Primary API lookup failed for " + mac + ": " + e.getMessage());
        }

        // 2. Secondary Source: macvendorlookup.com
        try {
            // This API requires the full MAC address (XX:XX:XX:XX:XX:XX)
            String response = queryApi("https://www.macvendorlookup.com/api/v2/" + mac);
            if (response != null && !response.trim().isEmpty()) {
                // Parse JSON response using Regex to extract "company"
                // Response format: [{"...","company":"Vendor Name","..."}]
                Pattern pattern = Pattern.compile("\"company\"\\s*:\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(response);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            System.err.println("Secondary API lookup failed for " + mac + ": " + e.getMessage());
        }

        return null;
    }

    private static String queryApi(String urlString) {
        try {
            System.out.println("DEBUG: Querying online vendor for " + urlString);
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000); // 2 seconds
            conn.setReadTimeout(2000);

            int status = conn.getResponseCode();
            if (status == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    return br.readLine(); // Vendor name is usually raw body
                }
            } else if (status == 429) {
                System.err.println("Rate limit exceeded for MAC Vendor API");
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}
