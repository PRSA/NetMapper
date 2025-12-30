package prsa.egosoft.netmapper.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Utilidad para mapear los valores numéricos de ifType (RFC 1213 y posteriores)
 * a descripciones legibles.
 */
public class InterfaceTypeUtils
{
    private static final Map<String, String> TYPE_MAP = new HashMap<>();
    
    static
    {
        TYPE_MAP.put("1", "other");
        TYPE_MAP.put("2", "regular1822");
        TYPE_MAP.put("3", "hdh1822");
        TYPE_MAP.put("4", "ddn-x25");
        TYPE_MAP.put("5", "rfc877-x25");
        TYPE_MAP.put("6", "ethernet-csmacd");
        TYPE_MAP.put("7", "iso88023-csmacd");
        TYPE_MAP.put("8", "iso88024-tokenBus");
        TYPE_MAP.put("9", "iso88025-tokenRing");
        TYPE_MAP.put("10", "iso88026-man");
        TYPE_MAP.put("11", "starLan");
        TYPE_MAP.put("12", "proteon-10Mbit");
        TYPE_MAP.put("13", "proteon-80Mbit");
        TYPE_MAP.put("14", "hyperchannel");
        TYPE_MAP.put("15", "fddi");
        TYPE_MAP.put("16", "lapb");
        TYPE_MAP.put("17", "sdlc");
        TYPE_MAP.put("18", "ds1");
        TYPE_MAP.put("19", "e1");
        TYPE_MAP.put("20", "basicISDN");
        TYPE_MAP.put("21", "primaryISDN");
        TYPE_MAP.put("22", "propPointToPointSerial");
        TYPE_MAP.put("23", "ppp");
        TYPE_MAP.put("24", "softwareLoopback");
        TYPE_MAP.put("25", "eon");
        TYPE_MAP.put("26", "ethernet-3Mbit");
        TYPE_MAP.put("27", "nsip");
        TYPE_MAP.put("28", "slip");
        TYPE_MAP.put("29", "ultra");
        TYPE_MAP.put("30", "ds3");
        TYPE_MAP.put("31", "sip");
        TYPE_MAP.put("32", "frame-relay");
        TYPE_MAP.put("33", "rs232");
        TYPE_MAP.put("34", "para");
        TYPE_MAP.put("35", "arcnet");
        TYPE_MAP.put("36", "arcnetPlus");
        TYPE_MAP.put("37", "atm");
        TYPE_MAP.put("38", "miox25");
        TYPE_MAP.put("39", "sonet");
        TYPE_MAP.put("40", "x25ple");
        TYPE_MAP.put("41", "iso88022llc");
        TYPE_MAP.put("42", "localTalk");
        TYPE_MAP.put("43", "smdssip");
        TYPE_MAP.put("44", "propFramerelay");
        TYPE_MAP.put("45", "v35");
        TYPE_MAP.put("46", "hssi");
        TYPE_MAP.put("47", "hippi");
        TYPE_MAP.put("48", "modem");
        TYPE_MAP.put("49", "aal5");
        TYPE_MAP.put("50", "sonetPath");
        TYPE_MAP.put("51", "sonetVT");
        TYPE_MAP.put("52", "smdsInterf");
        TYPE_MAP.put("53", "propVirtual");
        TYPE_MAP.put("54", "propMultiplexor");
        TYPE_MAP.put("55", "ieee80212");
        TYPE_MAP.put("56", "fibreChannel");
        TYPE_MAP.put("57", "hippiInterface");
        TYPE_MAP.put("58", "frameRelayInterconnect");
        TYPE_MAP.put("59", "aflane8023");
        TYPE_MAP.put("60", "aflane8025");
        TYPE_MAP.put("61", "cctemul");
        TYPE_MAP.put("62", "fastEther");
        TYPE_MAP.put("63", "isdn");
        TYPE_MAP.put("64", "v11");
        TYPE_MAP.put("65", "v36");
        TYPE_MAP.put("66", "g703at64k");
        TYPE_MAP.put("67", "g703at2mb");
        TYPE_MAP.put("68", "qllc");
        TYPE_MAP.put("69", "fastEtherFX");
        TYPE_MAP.put("70", "channel");
        TYPE_MAP.put("71", "ieee80211");
        TYPE_MAP.put("72", "ibm370parChan");
        TYPE_MAP.put("73", "escon");
        TYPE_MAP.put("74", "dlci");
        TYPE_MAP.put("75", "atmNoGui");
        TYPE_MAP.put("76", "atmLogical");
        TYPE_MAP.put("77", "ds0");
        TYPE_MAP.put("78", "ds0Bundle");
        TYPE_MAP.put("79", "bsc");
        TYPE_MAP.put("80", "preisdn");
        TYPE_MAP.put("81", "a6vcc");
        TYPE_MAP.put("82", "a6vpc");
        TYPE_MAP.put("83", "modemSc");
        TYPE_MAP.put("84", "atmsubIf");
        TYPE_MAP.put("85", "l3ipvlan");
        TYPE_MAP.put("86", "l3ipv6vlan");
        TYPE_MAP.put("87", "mpls");
        TYPE_MAP.put("88", "tunnel");
        TYPE_MAP.put("89", "coffee");
        TYPE_MAP.put("90", "ces");
        TYPE_MAP.put("91", "atmSubInterface");
        TYPE_MAP.put("92", "l2vlan");
        TYPE_MAP.put("93", "l3ipvlan");
        TYPE_MAP.put("94", "l3ipv6vlan");
        TYPE_MAP.put("95", "mplsTunnel");
        TYPE_MAP.put("96", "multiProtocol");
        TYPE_MAP.put("97", "http");
        TYPE_MAP.put("98", "networkService");
        TYPE_MAP.put("99", "hostService");
        TYPE_MAP.put("100", "terminalServer");
        TYPE_MAP.put("117", "gigabitEthernet");
        TYPE_MAP.put("131", "tunnel");
        TYPE_MAP.put("135", "l2vlan");
        TYPE_MAP.put("136", "l3ipvlan");
        TYPE_MAP.put("161", "ieee80211");
        TYPE_MAP.put("209", "bridge");
    }
    
    /**
     * Retorna la descripción del tipo de interfaz.
     * 
     * @param typeValue El valor numérico como String (ej: "6")
     * @return Una cadena formateada como "X (descripción)" o solo el valor si no se
     *         encuentra.
     */
    public static String formatInterfaceType(String typeValue)
    {
        if(typeValue == null || typeValue.isEmpty())
        {
            return "";
        }
        
        String description = TYPE_MAP.get(typeValue);
        if(description != null)
        {
            return typeValue + " (" + description + ")";
        }
        
        return typeValue;
    }
}
