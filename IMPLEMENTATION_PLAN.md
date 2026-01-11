# Plan de Implementación y Arquitectura: NetMapper

Este documento describe la arquitectura, diseño e implementación completa de la aplicación **NetMapper**, una herramienta Java para el descubrimiento y visualización de dispositivos de red mediante SNMP.

## 1. Visión General del Proyecto

**NetMapper** es una aplicación de escritorio diseñada para facilitar la auditoría y gestión de redes. Permite a los administradores de red obtener rápidamente información detallada de routers y switches sin necesidad de acceder a la CLI (Command Line Interface).

### Tecnologías Clave
- **Lenguaje**: Java 11+
- **Gestión de Proyecto**: Maven
- **Protocolo**: SNMP v2c (usando biblioteca `SNMP4J`)
- **GUI**: Java Swing

## 2. Arquitectura del Sistema

El proyecto sigue un patrón de diseño **MVC (Modelo-Vista-Controlador)** simplificado para desacoplar la lógica de recolección de datos de la interfaz de usuario.

### Componentes Principales

1.  **Modelo (`prsa.egosoft.netmapper.model`)**:
    -   `NetworkDevice`: Representa un dispositivo (Router/Switch). Contiene información de sistema, lista de interfaces, tablas de enrutamiento y VLANs.
    -   `NetworkInterface`: Representa un puerto o interfaz física/lógica. Almacena IP, máscara, MAC, estado, velocidad, MTU y VLANs asociadas (nativa y etiquetadas).

2.  **Núcleo y Servicios (`prsa.egosoft.netmapper.core`, `prsa.egosoft.netmapper.service`)**:
    -   `SnmpClient`: Wrapper sobre SNMP4J. Maneja la complejidad de sesiones UDP, retries (1), timeouts (1000ms) y operaciones PDU. Soporta vinculación (bind) a direcciones IP locales para forzar el uso de interfaces específicas.
    -   `NetworkScannerService`: Orquestador que inicia el escaneo. Utiliza una estrategia definida para poblar el modelo `NetworkDevice`.

3.  **Estrategias de Descubrimiento (`prsa.egosoft.netmapper.strategy`)**:
    -   `DiscoveryStrategy`: Interfaz para definir algoritmos de descubrimiento.
    -   `StandardMibStrategy`: Implementación principal. Utiliza MIBs estándar soportados por la gran mayoría de fabricantes (Cisco, HP, Huawei, D-Link, etc.).

### 3.19 Robustez en el Descubrimiento [NUEVO]
Se ha implementado una validación de seguridad en `NetworkScannerService` para asegurar que solo los dispositivos que responden satisfactoriamente a las consultas SNMP sean reportados a la interfaz de usuario. Esto previene que el árbol de dispositivos se llene de entradas vacías o no funcionales durante escaneos de rangos extensos.

### 3.20 Optimización de Velocidad [NUEVO]
Se han realizado ajustes de rendimiento críticos para acelerar el escaneo masivo:
- **Parámetros SNMP**: Reducción de timeout a 1000ms y reintentos a 1.
- **Paralelismo**: Aumento del pool de hilos de 20 a 100 en `NetworkScannerService`.
- **Early Exit**: La estrategia de descubrimiento aborta inmediatamente si el primer probe (sysDescr) falla, ahorrando tiempo de espera en IPs vacías.

4.  **Interfaz Gráfica (`prsa.egosoft.netmapper.gui`)**:
    -   `MainWindow`: Ventana principal Swing. Muestra un árbol jerárquico (`JTree`) con los dispositivos y sus detalles, y un panel de logs.

## 3. Implementación de Funcionalidades

### 3.1 Soporte de MIBs
La aplicación consulta los siguientes MIBs estándar para garantizar la compatibilidad multi-vendor:

-   **MIB-II (RFC 1213)**:
    -   *System Group*: Nombre, Descripción, Ubicación, Contacto, Uptime, ObjectID.
    -   *Interfaces Group*: Datos básicos de interfaces (estado, MTU, velocidad, MAC).
    -   *IP Group*: Tabla de direcciones IP y máscaras.
    -   *IP Route Group*: Tabla de enrutamiento (destino y next-hop).
-   **BRIDGE-MIB (RFC 1493)**:
    -   Obtención de la tabla de direcciones MAC aprendidas por puerto (`dot1dTpFdbTable`), permitiendo ver qué equipos están conectados a cada boca del switch.
-   **Q-BRIDGE-MIB (RFC 2674)**:
    -   Obtención de VLANs (`dot1qVlanStaticName`).
    -   **VLAN por Interfaz**:
        -   VLAN Nativa/Untagged (`dot1qPvid`).
        -   VLANs Etiquetadas/Tagged (`dot1qVlanCurrentEgressPorts` - decodificación de mapa de bits).
    -   **Estrategia de Fallback**: Si el dispositivo no expone la tabla de mapeo `dot1dBasePortIfIndex`, la aplicación asume una identidad directa (Puerto Bridge = Índice de Interfaz) para intentar recuperar la información de todas formas.

### 3.2 Detección de Fabricante (Vendor Discovery)
Implementa lógica heurística para determinar la marca y modelo del dispositivo:
1.  **Por OID (`sysObjectId`)**: Mapeo de prefijos OID Enterprise conocidos (ej. `.1.3.6.1.4.1.9` = Cisco).
2.  **Por Descripción (`sysDescr`)**: Análisis de texto si el OID es desconocido (buscando cadenas como "Linux", "D-Link", "Huawei").

### 3.3 Modelo de Datos de Interfaz
Se ha refactorizado la clase `NetworkInterface` para soportar escenarios complejos de conmutación:
-   **`untaggedVlanId`**: ID de la VLAN nativa (PVID).
-   **`taggedVlans`**: Lista de IDs de VLANs etiquetadas (Trunk).
-   **Configuración**: Velocidad (soporte para `ifHighSpeed` > 4Gbps), MTU, Tipo de medio.

### 3.4 Escaneo de Redes (Subnet Scanning) [NUEVO]
Se añadirá la capacidad de escanear rangos completos de direcciones IP.

-   **Entrada**: Admite formato CIDR, Máscara de Red, Intervalo IP y **listas separadas por comas** de cualquiera de estos.
-   **`SubnetUtils`**: Nueva clase de utilidad para:
    -   Parsear notación CIDR, Máscara decimal, Rangos y Listas compuestas.
    -   Calcular primera y última IP del rango.
    -   Generar lista de IPs iterables.
-   **Concurrencia**:
    -   Aprovechar el `ExecutorService` existente en `NetworkScannerService` para lanzar tareas de escaneo en paralelo para cada IP del rango.
    -   Se limitará el número de hilos concurrentes (ej. 10-20) para no saturar la red o el equipo local.

### 3.5 Prevención de Duplicados en UI
Se ha implementado una lógica en la capa de presentación (`MainWindow`) para manejar re-escaneos:
-   **Mapeo de Nodos**: Se mantiene un mapa `deviceNodeMap` (IP -> Nodo) para rastrear los dispositivos ya mostrados.
-   **Actualización en lugar de Creación**: Si se detecta un dispositivo que ya existe en el árbol, se actualizan sus datos y sub-nodos (Info, Interfaces) en lugar de crear una nueva entrada duplicada.

### 3.6 Ordenamiento de Resultados
Para mejorar la legibilidad, los dispositivos se presentan ordenados por su dirección IP:
-   **Comparación Numérica**: Se utiliza una utilidad (`SubnetUtils.compareIps`) que convierte las IPs a valores numéricos para asegurar un ordenamiento correcto (evitando que `10.0.0.2` aparezca después de `10.0.0.10`).
-   **Inserción en Orden**: Al añadir un nuevo nodo al árbol, se busca la posición correcta de inserción iterando sobre los nodos existentes.

### 3.7 Gestión de Estado de UI
Se ha añadido funcionalidad para **Resetear** el estado de la aplicación:
-   **Botón "Borrar"**: Permite al usuario limpiar la interfaz para iniciar una nueva sesión de escaneo limpio.
-   **Acciones**: Limpia el árbol visual, el mapa interno de dispositivos (`deviceNodeMap`), el campo de entrada de IP y el área de logs.
-   **Visibilidad de Resultados**: Los nuevos dispositivos se hacen visibles automáticamente usando `scrollPathToVisible()` para mejorar la experiencia del usuario.
-   **Tooltips Informativos**: Se han añadido tooltips en el campo "Objetivo" indicando los formatos admitidos (IP, CIDR, Rango, Lista).

### 3.8 Visualización de Topología de Red
Se ha implementado una funcionalidad de mapa/grafo de red:
-   **Modelo de Grafo**: `NetworkGraph` extrae nodos (dispositivos y endpoints) y aristas (conexiones) de los dispositivos escaneados.
-   **Etiquetas Enriquecidas**: Los nodos muestran nombre de dispositivo, vendor, modelo e IP. Los endpoints muestran IP/MAC y vendor.
-   **Etiquetas de Arcos**: Las conexiones muestran descripción de interfaz, MAC address y fabricante de la interfaz.
-   **Prevención de Duplicados**: Se ha implementado un mecanismo robusto de unificación de nodos en `NetworkGraph` utilizando tanto direcciones IP como MAC.
-   **Fusión Bidireccional**: Las conexiones bidireccionales se fusionan en un solo arco con etiquetas combinadas.
-   **Visualización**: El mapa se visualiza en una pestaña dedicada siempre disponible, permitiendo ver la evolución de la red conforme se escanea.

### 3.9 Validación de Entrada
Se ha implementado validación completa del campo "Objetivo" antes de iniciar escaneos:
-   **Formatos Soportados**: IP única, CIDR, IP/Máscara, Rango IP, Lista separada por comas.
-   **Validación en Tiempo Real**: Verifica sintaxis antes de permitir el escaneo.
-   **Mensajes de Error Informativos**: Dialog con ejemplos de formatos válidos.

### 3.10 Internacionalización (i18n)
Se ha diseñado el sistema de internacionalización para soportar múltiples idiomas con cambio dinámico.

-   **Consolidación de Recursos**: Uso de `messages_es.properties`, `messages_en.properties` y `messages_zh_CN.properties`.
-   **Arquitectura Centralizada**: La clase `Messages` gestiona los idiomas disponibles (`getAvailableLanguages()`) y los locales asociados.
-   **Refresco Dinámico**: Uso del patrón observador para actualizar toda la UI en tiempo real cuando se cambia el idioma en el JComboBox de configuración.
-   **Detección y Fallback**: Usa el locale del sistema (ES, EN, ZH) o Español por defecto.

### 3.11 Visualización de Vendor en Todas las MACs
Se ha completado la integración de información de fabricante en todas las ubicaciones donde se muestran direcciones MAC:
-   **Interfaces de Dispositivos**: En el árbol de detalles, las MACs de interfaces ahora se muestran con formato "MAC: XX:XX:XX:XX:XX:XX (Vendor Name)".
-   **Endpoints Detectados**: Los equipos detectados en puertos ya mostraban vendor desde su implementación inicial.
-   **Mapa de Red**: Las etiquetas de arcos y nodos ya incluían información de vendor para interfaces y endpoints.
-   **Mecanismo Unificado**: Todas las ubicaciones utilizan `MacVendorUtils.getVendor()` que implementa:
    -   Cache local persistente en `mac_vendors.properties`.
    -   **Detección Local de LAA**: Identificación automática de direcciones administradas localmente para evitar consultas online.
    -   **Caché Negativa**: Almacenamiento de resultados "Desconocidos" en memoria para evitar reintentos de consulta en la misma sesión.
    -   Autodescubrimiento mediante APIs online (api.macvendors.com + fallback a macvendorlookup.com).
    -   Actualización automática del cache con nuevos vendors descubiertos.

### 3.12 Detección de VLANs en Sistemas Linux
Se ha implementado un mecanismo de fallback para detectar VLANs en sistemas que no exponen Q-BRIDGE-MIB:
-   **Problema**: Linux y otros sistemas crean interfaces lógicas con nomenclatura `interfaz.vlanid` (ej: `wlp0s20f3.35` para VLAN 35).
-   **Solución**: Análisis de nombres de interfaces mediante expresión regular `^(.+)\.(\d{1,4})$`.
-   **Validación**: Verifica que el VLAN ID esté en rango válido IEEE 802.1Q (1-4094).
-   **Integración**: Se ejecuta como fallback después de intentar Q-BRIDGE-MIB, sin afectar switches estándar.
-   **Atribución**: Las VLANs detectadas se marcan claramente indicando el método de detección.
-   **Método**: `detectVlansFromInterfaceNames()` en `StandardMibStrategy`.

### 3.13 Descubrimiento Automático de Redes Locales [NUEVO]

Para facilitar el uso sin conocimiento previo de la red, se ha implementado un sistema de descubrimiento automático.

- **Componente**: `NetworkDiscoveryUtils`.
- **Lógica**:
    - Enumeración de todas las `NetworkInterface` del sistema.
    - Filtrado de interfaces (solo IPv4, activas, no loopback).
    - **Algoritmo de Unificación**: Si se detectan múltiples subredes, se analizan solapamientos. Si una red engloba a otra (ej. `/16` que contiene una `/24`), se prioriza la de mayor rango para evitar escaneos duplicados.
- **Integración GUI**: Nuevo botón que invoca el descubrimiento y lanza escaneos concurrentes para cada red detectada.

### 3.14 Refactorización de Idiomas y Selector Dinámico [NUEVO]

Se ha rediseñado el sistema de internacionalización para permitir cambios en caliente.

- **Consolidación de Recursos**: Eliminación de `messages.properties` para usar solo `_es` y `_en`.
- **Lógica de Fallback**: Si el locale del sistema no es ES o EN, `Messages.java` fuerza el uso de Español.
- **Placeholder Standard**: Se ha migrado del formato `%s/%d` al formato `{0}` de `MessageFormat` para asegurar la compatibilidad con diferentes idiomas y orden de argumentos.
- **Patrón de Observador**: `Messages` permite registrar `Runnable` que se ejecutan al cambiar el idioma.
- **Refresco Dinámico**: `MainWindow` implementa un método `updateUITexts()` que se registra como observador y re-asocia todos los textos de labels, botones y tooltips al cambiar la selección en el `JComboBox` de idiomas.
- **Internacionalización Integral**: Se han localizado también todas las alertas (`JOptionPane`), mensajes de error técnicos, unidades (bps), estados de interfaces y categorías de dispositivos, eliminando cualquier cadena de texto dependiente del código.

### 3.15 Carga de Mapas y Persistencia JSON [NUEVO]
Se ha cerrado el ciclo de persistencia implementando la **carga** de mapas:
- **`NetworkController.loadDevicesFromJson(File)`**: Utiliza Jackson `ObjectMapper` para deserializar el JSON a `Map<String, NetworkDevice>`. Gracias a los constructores vacíos añadidos previamente, la reconstrucción del grafo de objetos es automática.
- **Integración UI**: El método `loadMap()` en `MainWindow` gestiona la selección de fichero y la actualización asíncrona (SwingWorker/Thread) de los modelos de árbol y mapa.
- **Modo CLI Híbrido**: El flag `-m` permite iniciar la aplicación con un estado pre-cargado, habilitando casos de uso como "visualizar snapshot de ayer" o "convertir log JSON a reporte PDF" scriptable.


### 3.16 Exportación e Impresión del Mapa de Red
Se ha implementado la capacidad de exportar el estado actual del mapa de red a formatos digitales y soporte para impresión física.
- **Componente**: `NetworkMapPanel` con una barra de herramientas `JToolBar` integrada.
- **Formatos de Exportación**:
    - **PNG**: Captura de pantalla del grafo como imagen rasterizada.
    - **PDF**: Generación de documento PDF vectorial usando **Apache PDFBox** y `PdfBoxGraphics2D`.
- **Impresión**: Integración con `java.awt.print.PrinterJob` para enviar el grafo a la impresora.
- **Interfaz**: Barra de herramientas con botones totalmente localizados.

### 3.16 Layout de Pestañas e Interfaz Integrada
Se ha optimizado la navegación y el uso del espacio mediante un diseño basado en pestañas.
- **JTabbedPane**: Reemplaza el diseño estático por uno dinámico que separa los datos tabulares del árbol de la visualización gráfica del mapa.
- **Pestaña "Dispositivos"**: Ofrece la vista detallada en árbol a ancho completo.
- **Pestaña "Mapa"**: Muestra el grafo de topología, eliminando la necesidad de ventanas emergentes y permitiendo monitorizar el progreso del mapa en tiempo real.
- **Persistencia de Posiciones**: El mapa guarda las coordenadas de los nodos movidos manualmente para mantener el layout personalizado tras actualizaciones de escaneo.

### 3.17 Refactorización de Panel de Configuración
Se ha rediseñado el panel superior para mejorar la ergonomía:
- **GridBagLayout**: Permite una distribución flexible de componentes en múltiples filas.
- **Reubicación de Botones**: El botón "Auto Descubrimiento" se sitúa en una segunda línea para no saturar la primera fila y mejorar la jerarquía visual debajo de la etiqueta de objetivo.

### 3.18 Visualización de sysServices y ifType
Se ha implementado el soporte para mostrar los servicios de red activos según RFC 1213 y tipos de interfaz detallados:
- **sysServices**: Decodificación de los bits del campo `sysServices` para identificar capas (Physical, Datalink, Internet, End-to-End, Applications).
- **ifType**: Integración de `InterfaceTypeUtils` para mapear valores numéricos a descripciones IANA estándar (ej: 6 -> ethernet-csmacd, 24 -> softwareLoopback).

### 3.19 Optimización de Densidad Visual
Para mejorar la usabilidad en redes complejas, se han ajustado los parámetros gráficos del mapa:
- **NODE_RADIUS**: Reducido de 30 a 15 píxeles.
- **Iconos**: Tamaño de fuente Segoe UI Emoji ajustado a 15px para legibilidad en nodos reducidos.

### 3.20 Ordenamiento Lógico en el Árbol
Las interfaces se ordenan mediante un comparador personalizado en `DeviceTreePanel`:
- **Criterio**: Las descripciones numéricas se comparan como enteros (Long) para asegurar que "2" preceda a "10".
- **Prioridad**: Los identificadores numéricos aparecen siempre antes que los alfanuméricos.
### 3.21 Escaneo ARP Multiplataforma [NUEVO]

Se ha añadido la capacidad de realizar un descubrimiento rápido basado en ARP:

- **Estrategia**: `ArpDiscoveryStrategy` implementa `DiscoveryStrategy`.
- **Lógica**:
    -   **Fase 1**: Lee la tabla ARP del sistema para encontrar MACs asociadas a IPs.
    -   **Fase 2 (Linux)**: Parsea `/proc/net/arp`.
    -   **Fase 2 (Windows)**: Ejecuta y parsea `arp -a`.
    -   **Fase 3**: Asocia MAC y Vendor al dispositivo antes de iniciar SNMP.
-   **Beneficio**: Detecta equipos no gestionables (sin SNMP) y acelera el inventariado inicial.

### 3.22 Escaneo por Interfaz Específica (ARP y SNMP) [NUEVO]

Se ha mejorado el sistema de autodescubrimiento para utilizar la interfaz de red correcta al escanear cada red local:

- **Modelo de Datos**: `NetworkInterfaceInfo` encapsula la red (CIDR) y su interfaz asociada (nombre descriptivo e IP local).
- **Descubrimiento Mejorado**: `NetworkDiscoveryUtils.discoverLocalNetworksWithInterfaces()` captura la IP local de cada interfaz.
- **Escaneo ARP Dirigido**: Los escaneos ARP activos se realizan a través del handle de la interfaz específica.
- **Escaneo SNMP Dirigido (Bind)**: El `SnmpClient` vincula su socket UDP a la IP local de la interfaz, forzando al sistema operativo a usar esa interfaz para el tráfico SNMP.
- **Optimización**: Se mantienen los filtros de solapamiento de redes y escaneo único por red.



### 3.23 Filtro de Redundancia Física [NUEVO]

Se ha implementado una optimización en `NetworkGraph.java` para deducir la topología física real a partir de las tablas de direcciones MAC (BRIDGE-MIB), eliminando enlaces lógicos que no representan conexiones físicas directas.

- **Normalización**: Uso de un método `normalizeMac` centralizado que convierte todas las direcciones a minúsculas y utiliza `:` como separador, garantizando la consistencia en las comparaciones de datos provenientes de distintos fabricantes.
- **Lógica de Inferencia**: 
    - Se analiza cada par de dispositivos conectados lógicamente ($A$ y $B$).
    - Se busca un tercer dispositivo ($I$) que aparezca en el mismo puerto que $B$ desde la perspectiva de $A$.
    - Si $I$ ve a $A$ y a $B$ en **puertos diferentes**, se confirma que $I$ es un nodo intermedio físico.
    - En caso positivo, el enlace directo $A \leftrightarrow B$ se elimina por ser redundante.
- **Persistencia Jackson**: Se han añadido constructores sin argumentos a las clases `NetworkDevice`, `NetworkInterface` y `DetectedEndpoint` para permitir la serialización y deserialización JSON sin errores, facilitando herramientas de verificación y auditoría.
### 3.24 Optimizaciones de Alto Rendimiento y Precisión [NUEVO]

Se han refinado los componentes centrales para soportar despliegues de infraestructura crítica:

- **Efectividad del Paralelismo**: El pool de hilos de `NetworkScannerService` se ha escalado a **300 hilos**. Esta arquitectura permite que un escaneo de una red Clase C (/24) se realice de forma casi instantánea, procesando cada IP en hilos independientes sin bloqueos de UI.
- **Topología en Mallas Core y LAG**:
    - **Reconocimiento de LAG**: El sistema identifica interfaces tipo **LAG/Port-Channel** (basado en `ifType` y patrones de nombre). Esto es crucial para el algoritmo de simplificación, ya que los LAGs agrupan múltiples interfaces físicas y deben tratarse como un único canal transparente.
    - **Whitelist de Core**: Se ha implementado un mecanismo de exclusión para evitar la simplificación errónea de enlaces en la "malla" de switches Core (Full Mesh), preservando todas las interconexiones físicas entre nodos de alta disponibilidad.
- **Normalización MAC Universal y Enriquecimiento Dinámico**:
    - **Estandarización**: `normalizeMac()` aplica un formato canónico (lowercase + `:`) a todas las MACs, eliminando duplicados causados por diferencias de sintaxis entre fabricantes (que pueden usar `.`, `-` o `:`).
    - **State Upgrade**: La lógica de construcción del grafo ahora permite actualizar el estado de un nodo existente. Si se encuentra una dirección IP para un dispositivo que previamente solo se conocía por su MAC, el nodo se "enriquece" dinámicamente con la nueva información sin generar duplicados.

## 4. Estructura del Proyecto

```
/opt/workspace/NetMapper/NetMapper/
├── src/main/java/prsa/egosoft/netmapper/
│   ├── Main.java                 # Punto de entrada
│   ├── core/SnmpClient.java      # Wrapper SNMP de bajo nivel
│   ├── model/                    # POJOs (Device, Interface)
│   ├── gui/MainWindow.java       # Interfaz de Usuario
│   ├── service/                  # Lógica de negocio
│   └── strategy/                 # Lógica de descubrimiento (MIBs)
├── src/test/java/                # Pruebas unitarias
├── pom.xml                       # Configuración Maven
├── TASKS.md                      # Registro de tareas realizadas
└── WALKTHROUGH.md                # Manual de usuario y verificación
```

## 5. Próximos Pasos / Mejoras Futuras
-   Exportación de resultados del árbol a formatos CSV/JSON.
-   Soporte para SNMP v3 (Autenticación y Cifrado).
-   Persistencia de datos del historial de escaneos en base de datos local (SQLite).
-   Detección de tipología de red más allá de SNMP (ej. LLDP/CDP si están disponibles).
### 3.25 Arbitraje de Endpoints Multi-homed [NUEVO]

Se ha implementado una capa de arbitraje en `NetworkGraph` para resolver la redundancia de links en endpoints (que a menudo se ven en múltiples switches debido a la inundación de tablas MAC).

- **GraphContext Unificado**: Refactorización de todos los filtros (`applyPhysicalRedundancyFilter`, `applyStrictTriangleFilter`, `applyEndpointArbitrationFilter`) para usar un contexto compartido que pre-calcula la visibilidad de MACs y puertos de infraestructura.
- **Algoritmo Global Winner**:
    1.  **Categorización de Puertos**: Cada enlace se marca como `DIRECT` (puerto de acceso) o `INFRA` (trunk) basándose en si se ven otros switches por ese puerto.
    2.  **Selección del Ganador**: Para cada endpoint, se elige exactamente un switch "ganador". Los enlaces directos tienen prioridad absoluta sobre los trunks.
    3.  **Tie-breaking**: Si existen múltiples candidatos del mismo tipo, se realiza un desempate estable comparando los IDs (IPs) de los dispositivos, garantizando coherencia en la visualización.
- **Normalización de MACs**: Se ha estandarizado el uso de `normalizeMac()` en todas las búsquedas de bridge table para evitar fallos por discrepancias de formato (case-sensitivity o separadores).
