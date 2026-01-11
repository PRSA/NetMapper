# NetMapper - Sistema de Mapeo de Red SNMP

NetMapper es una aplicación Java diseñada para descubrir y visualizar información detallada de dispositivos de red (Switches, Routers) mediante SNMP.

## Características

### Descubrimiento y Análisis
- **Descubrimiento Automático**: Obtiene información del sistema, interfaces, direcciones IP y máscaras
- **Detección de Fabricante/Modelo**: Identificación automática de marca y modelo del dispositivo
- **Soporte de VLANs**: Detecta VLANs configuradas mediante Q-BRIDGE-MIB y fallback para sistemas Linux (detección por nomenclatura de interfaces)
- **Mapeo de Puertos**: Identifica qué dispositivos están conectados a cada puerto mediante la tabla de direcciones MAC (BRIDGE-MIB)
- **Tabla de Rutas**: Visualiza la tabla de enrutamiento del dispositivo
- **Identificación de Fabricantes MAC**: Resolución automática de fabricantes por MAC con detección local de LAA y caché optimizada (local + lookup online con fallback) para evitar consultas redundantes.
- **Topología Física Real**: Algoritmo de eliminación de redundancias que infiere la conexión física directa entre dispositivos ignorando enlaces lógicos indirectos mediante el análisis de tablas MAC.
- **Descubrimiento ARP Multiplataforma**: Identificación inmediata de dispositivos locales mediante tabla ARP (compatible con Linux/Windows).
- **Detección de Servicios (sysServices)**: Visualización detallada de las capas de red activas (L1-L7) según el estándar RFC 1213.
- **Escaneo por Interfaz Específica (ARP/SNMP)**: El autodescubrimiento utiliza la interfaz de red correcta para escanear cada red local. El tráfico SNMP se vincula (bind) a la IP local de la interfaz para mayor precisión.
- **Escaneo de Alta Velocidad**: Pool de hilos optimizado (300 hilos) para procesar redes /24 en segundos con notificaciones de finalización en tiempo real.
- **Precisión en Topologías de Centro de Datos**: Soporte para interfaces **LAG (Link Aggregation)** y preservación inteligente de mallas de switches Core mediante listas blancas de topología.
- **Unicidad de Endpoints**: Normalización universal de MACs y enriquecimiento dinámico de etiquetas para evitar duplicados.
- **Arbitraje de Endpoints Multi-homed**: Algoritmo "Global Winner" que reduce links redundantes en endpoints, priorizando puertos de acceso sobre trunks.

### Escaneo Avanzado
- **Escaneo de Redes**: Soporte para múltiples formatos de entrada:
  - IP única: `192.168.1.1`
  - CIDR: `192.168.1.0/24`
  - IP/Máscara: `192.168.1.0/255.255.255.0`
  - Rango IP: `192.168.1.1-192.168.1.50`
- **Carga de Mapa JSON**: Visualización instantánea de mapas previamente escaneados y guardados sin necesidad de re-escarnear.
- **Internacionalización (i18n)**: Soporte completo para Español, Inglés y Chino Simplificado con cambio dinámico de idioma en la interfaz.
- **Interfaz mediante Pestañas**: Organización del árbol de dispositivos y el mapa de red en pestañas siempre accesibles.
- **Interfaz Adaptativa**: Panel de dispositivos optimizado para ocupar todo el ancho disponible y visualización clara de jerarquías de red.
- **Soporte CLI (Headless)**: Permite ejecutar escaneos y auto-descubrimiento sin interfaz gráfica, con soporte para múltiples formatos de exportación.
  - Lista separada por comas de cualquiera de los anteriores
- **Validación de Entrada**: Verificación automática del formato antes de iniciar el escaneo
- **Análisis de Duplicados**: Actualización inteligente de dispositivos re-escaneados sin crear duplicados
- **Ordenamiento Lógico**: Dispositivos ordenados por IP e interfaces ordenadas (numéricas primero) en el árbol.
- **Visualización de Mapa Topológico**: Grafo interactivo con **zoom (rueda/botones)**, **panorámica (arrastrar)** y **filtros de visibilidad**. Permite alternar entre enlaces físicos y lógicos, y filtrar por tipo de dispositivo (Router, Switch, PC, etc.) o por categoría (Dispositivo, Endpoint). Distribución optimizada para máxima legibilidad.
- **Descubrimiento de Redes Locales**: Botón para detectar automáticamente todas las interfaces locales e iniciar su escaneo.

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
- **Soporte Dinámico Integro**: Toda la interfaz, incluyendo alertas (`JOptionPane`), mensajes de log, errores técnicos y etiquetas del árbol, están disponibles en Español, Inglés y Chino Simplificado.
- **Selector de Idioma**: Selector desplegable en la UI para cambiar el idioma en tiempo real sin reiniciar.
- **Detección y Fallback**: Usa el locale del sistema al inicio (si es ES, EN o ZH), de lo contrario usa Español por defecto.

## Requisitos

- Java 11 o superior
- Maven 3.x
- Acceso a dispositivos con SNMP v2c habilitado
- **Captura de Paquetes (ARP Scan)**:
  - **Linux**: Librería `libpcap` instalada (`sudo apt install libpcap-dev`) y permisos de root/admin para ejecutar el escaneo activo (o `setcap`).
  - **Windows**: [Npcap](https://npcap.com/) o WinPcap instalado.

## Compilación y Ejecución

```bash
# Compilar proyecto y tests
mvn clean compile test

# Ejecutar aplicación (GUI)
mvn exec:java -Dexec.mainClass="prsa.egosoft.netmapper.Main"

# Ejecutar aplicación (CLI - Headless)
# Ejemplo: Escanear subred y exportar a JSON y PNG
mvn exec:java -Dexec.mainClass="prsa.egosoft.netmapper.Main" -Dexec.args="-c public -t 192.168.1.0/24 -json mapa.json -png mapa.png"

# Para ejecutar en inglés (GUI)
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
6. **Opcional**: Use el botón "Cargar Mapa" en la pestaña de Configuración para cargar un mapa JSON previamente guardado.
7. Explore los resultados en la pestaña **Dispositivos**
7. Cambie a la pestaña **Mapa** para visualizar la topología de red en tiempo real
8. Use el selector de idioma en la parte superior derecha para cambiar entre Español, Inglés y Chino Simplificado instantáneamente
9. Use "Borrar" para resetear el estado y comenzar de nuevo

## Parámetros CLI (Headless)

- `-c <community>`: (Obligatorio) Comunidad SNMP.
- `-m <file>`: Cargar mapa de red desde un archivo JSON. Mutuamente exclusivo con `-t` y `-a`.
- `-t <targets>`: Objetivo de escaneo (IP, CIDR, rango, lista). Mutuamente exclusivo con `-a`.
- `-a`: Descubrimiento automático de redes locales. Mutuamente exclusivo con `-t`.
- `-png <path>`: Exportar mapa a imagen PNG.
- `-pdf <path>`: Exportar mapa a documento PDF.
- `-json <path>`: Exportar inventario a fichero JSON.
- `-h`: Mostrar ayuda.

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
├── messages_en.properties    # Recursos en Inglés
└── messages_zh_CN.properties # Recursos en Chino Simplificado
```

## Compatibilidad

NetMapper es compatible con dispositivos que soporten los siguientes MIBs estándar:
- MIB-II (RFC 1213)
- BRIDGE-MIB (RFC 1493)
- Q-BRIDGE-MIB (RFC 2674)
- IF-MIB (RFC 2863)

Fabricantes probados: Cisco, HP/Aruba, Huawei, D-Link, 3Com, Extreme Networks, Teldat, Tenda, Asus, EnGenius, Apple, Xiaomi, Nintendo, Intel, Samsung, y otros.
