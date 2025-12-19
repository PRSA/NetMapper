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
- **Detección de Servicios (sysServices)**: Visualización detallada de las capas de red activas (L1-L7) según el estándar RFC 1213.

### Escaneo Avanzado
- **Escaneo de Redes**: Soporte para múltiples formatos de entrada:
  - IP única: `192.168.1.1`
  - CIDR: `192.168.1.0/24`
  - IP/Máscara: `192.168.1.0/255.255.255.0`
  - Rango IP: `192.168.1.1-192.168.1.50`
- **Internacionalización (i18n)**: Soporte completo para Español e Inglés con cambio dinámico de idioma en la interfaz.
- **Interfaz mediante Pestañas**: Organización del árbol de dispositivos y el mapa de red en pestañas siempre accesibles.
- **Interfaz Adaptativa**: Panel de dispositivos optimizado para ocupar todo el ancho disponible y visualización clara de jerarquías de red.
  - Lista separada por comas de cualquiera de los anteriores
- **Validación de Entrada**: Verificación automática del formato antes de iniciar el escaneo
- **Análisis de Duplicados**: Actualización inteligente de dispositivos re-escaneados sin crear duplicados
- **Ordenamiento**: Dispositivos ordenados automáticamente por dirección IP
- **Descubrimiento de Redes Locales**: Botón para detectar automáticamente todas las interfaces y subnets locales del equipo e iniciar su escaneo.

### Visualización
- **Interfaz Gráfica**: Visualización clara mediante pestañas (Swing JTabbedPane)
- **Mapa de Red Topológico**: Visualización gráfica integrada siempre disponible
  - Nodos con información detallada (nombre, vendor, modelo, IP)
  - Arcos con información de interfaces (descripción, MAC, fabricante)
  - Fusión automática de conexiones bidireccionales
  - Iconos y colores diferenciales por tipo de dispositivo (Router, Switch, PC, Servidor, Impresora, etc.)
  - Prevención de nodos duplicados mediante unificación inteligente de IP/MAC
- **Tooltips Informativos**: Ayuda contextual en campos de entrada
- **Reseteo de Estado**: Botón para limpiar resultados y comenzar nuevo escaneo
- **Layout Optimizada**: Interfaz organizada en pestañas con panel de configuración multilínea para una navegación más cómoda y eficiente.

### Internacionalización
- **Soporte Dinámico Integro**: Toda la interfaz, incluyendo alertas (`JOptionPane`), mensajes de log, errores técnicos y etiquetas del árbol, están disponibles en Español e Inglés.
- **Selector de Idioma**: Selector desplegable en la UI para cambiar el idioma en tiempo real sin reiniciar.
- **Detección y Fallback**: Usa el locale del sistema al inicio (si es ES o EN), de lo contrario usa Español por defecto.

## Requisitos

- Java 11 o superior
- Maven 3.x
- Acceso a dispositivos con SNMP v2c habilitado

## Compilación y Ejecución

```bash
# Compilar proyecto y tests
mvn clean compile test

# Ejecutar aplicación
mvn exec:java -Dexec.mainClass="prsa.egosoft.netmapper.Main"

# Para ejecutar en inglés
mvn exec:java -Dexec.mainClass="prsa.egosoft.netmapper.Main" -Duser.language=en
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
4. Haga clic en "Escanear" para el objetivo manual
5. **Opcional**: Use "Descubrimiento Automático" para encontrar y escanear subredes locales automáticamente
6. Explore los resultados en la pestaña **Dispositivos**
7. Cambie a la pestaña **Mapa** para visualizar la topología de red en tiempo real
8. Use el selector de idioma en la parte superior derecha para cambiar entre Español e Inglés instantáneamente
9. Use "Borrar" para resetear el estado y comenzar de nuevo

## Estructura del Proyecto

```
src/main/java/prsa/egosoft/netmapper/
├── core/           # Cliente SNMP (SNMP4J wrapper)
├── model/          # Modelos de datos (Device, Interface, NetworkGraph)
├── strategy/       # Estrategias de recolección (MIB-II, Bridge, Q-Bridge)
├── service/        # Servicios de escaneo
├── util/           # Utilidades (SubnetUtils, MacVendorUtils)
├── gui/            # Interfaz Swing (MainWindow, NetworkMapDialog)
└── i18n/           # Internacionalización (Messages, resource bundles)

src/main/resources/
├── messages_es.properties    # Recursos en Español (por defecto)
└── messages_en.properties    # Recursos en Inglés
```

## Compatibilidad

NetMapper es compatible con dispositivos que soporten los siguientes MIBs estándar:
- MIB-II (RFC 1213)
- BRIDGE-MIB (RFC 1493)
- Q-BRIDGE-MIB (RFC 2674)
- IF-MIB (RFC 2863)

Fabricantes probados: Cisco, HP/Aruba, Huawei, D-Link, 3Com, Extreme Networks, Teldat, Tenda, Asus, EnGenius, Apple, Xiaomi, Nintendo, Intel, Samsung, y otros.
