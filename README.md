# NetMapper - Sistema de Mapeo de Red SNMP

NetMapper es una aplicación Java diseñada para descubrir y visualizar información detallada de dispositivos de red (Switches, Routers) mediante SNMP.

## Características

### Descubrimiento y Análisis
- **Descubrimiento Automático**: Obtiene información del sistema, interfaces, direcciones IP y máscaras
- **Detección de Fabricante/Modelo**: Identificación automática de marca y modelo del dispositivo
- **Soporte de VLANs**: Detecta VLANs configuradas mediante Q-BRIDGE-MIB y fallback para sistemas Linux (detección por nomenclatura de interfaces)
- **Mapeo de Puertos**: Identifica qué dispositivos están conectados a cada puerto mediante la tabla de direcciones MAC (BRIDGE-MIB)
- **Tabla de Rutas**: Visualiza la tabla de enrutamiento del dispositivo
- **Identificación de Fabricantes MAC**: Resolución automática de fabricantes por MAC en todas las visualizaciones (interfaces, endpoints, mapa de red) usando cache local + lookup online con fallback

### Escaneo Avanzado
- **Escaneo de Redes**: Soporte para múltiples formatos de entrada:
  - IP única: `192.168.1.1`
  - CIDR: `192.168.1.0/24`
  - IP/Máscara: `192.168.1.0/255.255.255.0`
  - Rango IP: `192.168.1.1-192.168.1.50`
  - Lista separada por comas de cualquiera de los anteriores
- **Validación de Entrada**: Verificación automática del formato antes de iniciar el escaneo
- **Gestión de Duplicados**: Actualización inteligente de dispositivos re-escaneados sin crear duplicados
- **Ordenamiento**: Dispositivos ordenados automáticamente por dirección IP

### Visualización
- **Interfaz Gráfica**: Visualización clara en árbol mediante Java Swing
- **Mapa de Red Topológico**: Visualización gráfica de la topología de red detectada
  - Nodos con información detallada (nombre, vendor, modelo, IP)
  - Arcos con información de interfaces (descripción, MAC, fabricante)
  - Fusión automática de conexiones bidireccionales
  - Prevención de nodos duplicados
- **Tooltips Informativos**: Ayuda contextual en campos de entrada
- **Reseteo de Estado**: Botón para limpiar resultados y comenzar nuevo escaneo

### Internacionalización
- **Soporte Multiidioma**: Interfaz disponible en Español e Inglés
- **Detección Automática**: Usa el locale del sistema por defecto

## Requisitos

- Java 11 o superior
- Maven 3.x
- Acceso a dispositivos con SNMP v2c habilitado

## Compilación y Ejecución

```bash
# Compilar proyecto y tests
mvn clean compile test

# Ejecutar aplicación
mvn exec:java -Dexec.mainClass="com.netmapper.Main"

# Para ejecutar en inglés
mvn exec:java -Dexec.mainClass="com.netmapper.Main" -Duser.language=en
```

## Uso

1. Inicie la aplicación
2. Ingrese el objetivo en el campo "Objetivo":
   - Una IP individual
   - Un rango CIDR
   - Una IP con máscara
   - Un rango de IPs
   - Una lista separada por comas
3. Ingrese la comunidad SNMP (por defecto `public`)
4. Haga clic en "Escanear"
5. Explore los resultados en el árbol de la izquierda
6. Haga clic en "Mapa" para visualizar la topología de red
7. Use "Borrar" para limpiar y comenzar de nuevo

## Estructura del Proyecto

```
src/main/java/com/netmapper/
├── core/           # Cliente SNMP (SNMP4J wrapper)
├── model/          # Modelos de datos (Device, Interface, NetworkGraph)
├── strategy/       # Estrategias de recolección (MIB-II, Bridge, Q-Bridge)
├── service/        # Servicios de escaneo
├── util/           # Utilidades (SubnetUtils, MacVendorUtils)
├── gui/            # Interfaz Swing (MainWindow, NetworkMapDialog)
└── i18n/           # Internacionalización (Messages, resource bundles)

src/main/resources/
├── messages.properties       # Recursos por defecto (Español)
├── messages_es.properties    # Recursos en Español
└── messages_en.properties    # Recursos en Inglés
```

## Compatibilidad

NetMapper es compatible con dispositivos que soporten los siguientes MIBs estándar:
- MIB-II (RFC 1213)
- BRIDGE-MIB (RFC 1493)
- Q-BRIDGE-MIB (RFC 2674)
- IF-MIB (RFC 2863)

Fabricantes probados: Cisco, HP/Aruba, Huawei, D-Link, 3Com, Extreme Networks, Teldat, Tenda, Asus, EnGenius, Apple, Xiaomi, Nintendo, Intel, Samsung, y otros.
