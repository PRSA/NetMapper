package prsa.egosoft.netmapper.model;

/**
 * Encapsula la información de una red junto con su interfaz de red.
 */
public class NetworkInterfaceInfo
{
    private String cidr;
    private String interfaceName;
    private String interfaceDisplayName;
    private String localIp;
    
    public NetworkInterfaceInfo(String cidr, String interfaceName, String interfaceDisplayName, String localIp)
    {
        this.cidr = cidr;
        this.interfaceName = interfaceName;
        this.interfaceDisplayName = interfaceDisplayName;
        this.localIp = localIp;
    }
    
    public String getCidr()
    {
        return cidr;
    }
    
    public void setCidr(String cidr)
    {
        this.cidr = cidr;
    }
    
    public String getInterfaceName()
    {
        return interfaceName;
    }
    
    public void setInterfaceName(String interfaceName)
    {
        this.interfaceName = interfaceName;
    }
    
    public String getInterfaceDisplayName()
    {
        return interfaceDisplayName;
    }
    
    public void setInterfaceDisplayName(String interfaceDisplayName)
    {
        this.interfaceDisplayName = interfaceDisplayName;
    }
    
    public String getLocalIp()
    {
        return localIp;
    }
    
    public void setLocalIp(String localIp)
    {
        this.localIp = localIp;
    }
    
    @Override
    public String toString()
    {
        return "NetworkInterfaceInfo{" + "cidr='" + cidr + '\'' + ", interfaceName='" + interfaceName + '\''
                + ", interfaceDisplayName='" + interfaceDisplayName + '\'' + ", localIp='" + localIp + '\'' + '}';
    }
}
