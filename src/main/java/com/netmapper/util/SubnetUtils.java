package com.netmapper.util;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class SubnetUtils {

    /**
     * Valida si el string es un formato válido o una lista de ellos separados por
     * comas.
     */
    public static boolean isValidInput(String input) {
        if (input == null || input.isEmpty())
            return false;

        // Soporte para listas separadas por comas
        if (input.contains(",")) {
            String[] parts = input.split(",");
            for (String part : parts) {
                if (!isValidSingleInput(part.trim())) {
                    return false;
                }
            }
            return true;
        }
        return isValidSingleInput(input.trim());
    }

    private static boolean isValidSingleInput(String input) {
        if (input == null || input.isEmpty())
            return false;

        // Caso Rango: "IP_START - IP_END"
        if (input.contains("-")) {
            String[] parts = input.split("-");
            if (parts.length != 2)
                return false;
            try {
                InetAddress.getByName(parts[0].trim());
                InetAddress.getByName(parts[1].trim());
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        if (!input.contains("/")) {
            // Asumimos IP simple
            try {
                InetAddress.getByName(input);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        String[] parts = input.split("/");
        if (parts.length != 2)
            return false;
        try {
            InetAddress.getByName(parts[0]);

            // Chequear si es máscara (contiene puntos) o prefijo
            String suffix = parts[1];
            if (suffix.contains(".")) {
                // Validar que sea una IP válida
                InetAddress.getByName(suffix);
                return true;
            } else {
                int mask = Integer.parseInt(suffix);
                return mask >= 0 && mask <= 32;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Genera una lista de IPs desde entradas múltiples (separadas por coma).
     * Soporta: CIDR, IP única, máscara o rango (start - end).
     */
    public static List<String> getIpList(String input) {
        List<String> allIps = new ArrayList<>();
        if (input == null || input.isEmpty())
            return allIps;

        String[] inputs = input.split(",");
        for (String block : inputs) {
            allIps.addAll(getIpListFromSingleInput(block.trim()));
        }
        return allIps;
    }

    private static List<String> getIpListFromSingleInput(String input) {
        List<String> ips = new ArrayList<>();
        if (!isValidSingleInput(input))
            return ips;

        // Caso Rango: "IP_START - IP_END"
        if (input.contains("-")) {
            try {
                String[] parts = input.split("-");
                String startIpStr = parts[0].trim();
                String endIpStr = parts[1].trim();

                long start = ipToLong(InetAddress.getByName(startIpStr));
                long end = ipToLong(InetAddress.getByName(endIpStr));

                if (start > end) {
                    long temp = start;
                    start = end;
                    end = temp;
                }

                for (long i = start; i <= end; i++) {
                    ips.add(longToIp(i));
                }
                return ips;
            } catch (Exception e) {
                e.printStackTrace();
                return ips;
            }
        }

        if (!input.contains("/")) {
            ips.add(input);
            return ips;
        }

        try {
            String[] parts = input.split("/");
            String ip = parts[0];
            int prefix;

            if (parts[1].contains(".")) {
                prefix = netmaskToPrefix(parts[1]);
            } else {
                prefix = Integer.parseInt(parts[1]);
            }

            if (prefix == 32) {
                ips.add(ip);
                return ips;
            }

            long ipVal = ipToLong(InetAddress.getByName(ip));
            long maskVal = -1L << (32 - prefix);
            long networkVal = ipVal & maskVal;
            long broadcastVal = networkVal | ~maskVal;

            // Iteramos desde network + 1 hasta broadcast - 1 (para /31 o /32 se comportaría
            // distinto, pero es simple)
            // Para /31 (P2P), network y broadcast son usables. Para /24, 0 y 255 no.
            // Asumimos comportamiento estándar de host para redes >= /30: skip network &
            // broadcast.
            // Para redes pequeñas incluimos todo por seguridad.
            long start = (prefix < 31) ? networkVal + 1 : networkVal;
            long end = (prefix < 31) ? broadcastVal - 1 : broadcastVal;

            for (long i = start; i <= end; i++) {
                ips.add(longToIp(i));
            }

        } catch (Exception e) {
            // En caso de error, devolver lista vacía o lo que se pudo
            e.printStackTrace();
        }
        return ips;
    }

    private static int netmaskToPrefix(String netmask) throws Exception {
        long mask = ipToLong(InetAddress.getByName(netmask));
        int prefix = 0;
        // Contar bits encendidos. Una máscara válida debe tener 1s contiguos desde MSB.
        // Simplificación: contamos bits en 1.
        for (int i = 0; i < 32; i++) {
            if ((mask & (1L << i)) != 0) {
                prefix++;
            }
        }
        return prefix;
    }

    private static long ipToLong(InetAddress ip) {
        byte[] octets = ip.getAddress();
        long result = 0;
        for (byte octet : octets) {
            result <<= 8;
            result |= octet & 0xFF;
        }
        return result & 0xFFFFFFFFL; // Mask to unsigned 32-bit
    }

    private static String longToIp(long ip) {
        return ((ip >> 24) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                (ip & 0xFF);
    }
}
