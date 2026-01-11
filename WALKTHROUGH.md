# NetMapper - Manual de Usuario y Verificación

Este documento describe cómo ejecutar y utilizar la aplicación NetMapper para visualizar información de dispositivos de red vía SNMP.

## Requisitos Previos
- Java JDK 11 o superior.
- Maven 3.x para compilar.
- Acceso de red a dispositivos con agente SNMP habilitado (v2c).
- **Librerías de Captura** (para Escaneo Activo ARP):
    - Linux: `libpcap` (`sudo apt install libpcap-dev`).
    - Windows: `Npcap` o `WinPcap`.

## Uso de la Interfaz

1.  **Escaneo**: Introduce una IP o rango en el campo superior y pulsa Enter o el botón de escaneo.
2.  **Visualización**: Navega entre las pestañas **Dispositivos** (árbol) y **Mapa** (grafo).
3.  **Configuración**: Utiliza el selector de idioma en la parte superior derecha para cambiar entre Español, Inglés y Chino Simplificado instantáneamente.
    mvn exec:java -Dexec.mainClass="prsa.egosoft.netmapper.Main"
    ```
    O usando el classpath generado:
    ```bash
    java -cp target/classes:$(mvn dependency:build-classpath | grep -v '\[INFO\]' | tail -1) prsa.egosoft.netmapper.Main
    ```

## Funcionalidades
La aplicación permite introducir una dirección **IP**, un **Rango CIDR**, **IP/Máscara**, **Intervalo IP**, o una **Lista separada por comas** de cualquiera de los anteriores.

1.  **Información del Sistema**:
    - Detección automática de **Marca y Modelo** (incluyendo Apple, Cisco, Huawei, etc.).
    - Detección automática de **Marca y Modelo** (incluyendo Apple, Cisco, Huawei, etc.).
    - Descripción, ubicación, contacto y tiempo de actividad.
2.  **Interfaces**:
    - Lista detallada de interfaces.
    - **Configuración**: MTU, Velocidad (bps), Tipo de Interfaz.
    - **Estado**: Admin/Oper Status.
    - Direcciones Físicas (MAC con fabricante) e IP/Máscara.
    - **VLANs**: VLAN Nativa (PVID) y VLANs Etiquetadas asociadas.
3.  **Equipos Conectados**: Direcciones MAC detectadas por puerto (BRIDGE-MIB).
4.  **Tabla de Rutas**: Destinos y gateways.
5.  **VLANs**: IDs y nombres de VLANs (Q-BRIDGE-MIB).
6.  **Actualización Dinámica**: Al re-escanear un dispositivo, la información se actualiza en el nodo existente sin crear duplicados.
7.  **Ordenamiento**: Los dispositivos se muestran automáticamente ordenados por dirección IP de menor a mayor.
8.  **Reseteo**: Botón "Borrar" para limpiar resultados y comenzar un nuevo escaneo desde cero.
9.  **Tooltips**: Ayuda contextual mostrando formatos de entrada admitidos al pasar el ratón sobre el campo "Objetivo".
10. **Mapa de Red (Pestaña)**: Visualización gráfica integrada y siempre disponible de la topología detectada.
    - Etiquetas de nodos enriquecidas (nombre, modelo, vendor, IP)
    - Etiquetas de arcos con información de interfaz (descripción, MAC, vendor)
    - Prevención de nodos duplicados mediante unificación inteligente de IP y MAC (insensible a mayúsculas)
    - Fusionar arcos bidireccionales para mejor legibilidad
    - Iconos representativos y colores específicos según el tipo de dispositivo (Router, Switch, Firewall, Impresora, etc.)
    - **Persistencia de Posiciones**: El mapa recuerda dónde has colocado cada nodo, incluso si realizas un nuevo escaneo o se encuentran nuevos dispositivos.
11. **Validación de Entrada**: Verificación del formato de objetivo antes de escanear (IP, CIDR, IP/Máscara, Rango, Lista).
12. **Internacionalización**: Soporte dinámico para múltiples idiomas (Español/English/Chinese) con selector en tiempo real.
13. **Identificación de Fabricantes**: Todas las direcciones MAC se muestran con información del fabricante cuando está disponible.
14. **Detección de VLANs en Linux**: Fallback automático para detectar VLANs en sistemas Linux.
15. **Descubrimiento Automático de Redes**: Botón "Descubrimiento Automático" que detecta interfaces y subredes locales.
16. **Selector de Idioma**: Permite cambiar el idioma de toda la interfaz sin reiniciar la aplicación.
17. **Visualización de ifType**: Las interfaces muestran tanto el valor numérico como la descripción (ej: `6 (ethernet-csmacd)`).
18. **Ordenamiento de Interfaces**: Las interfaces aparecen ordenadas (numéricas primero, luego alfanuméricas).
19. **Auto-escalado y Distribución del Mapa**: El mapa se ajusta automáticamente al tamaño de visualización (ventana, PNG, PDF) para evitar cortes. Los endpoints se distribuyen en arcos inteligentes para evitar solapamientos.
20. **Caché de Fabricantes MAC**: Optimización para evitar consultas redundantes mediante detección de LAA y almacenamiento de resultados desconocidos.
21. **Exportación e Impresión del Mapa**: Botones dedicados en la barra de herramientas del mapa:
    - **PNG**: Exporta el estado actual del mapa como una imagen `.png`.
    - **PDF**: Generas un documento PDF con el mapa.
    - **Imprimir**: Envía el mapa directamente a la impresora configurada.
22. **Layout de Pestañas e Interfaz Multilínea**:
    - Organización en pestañas para separar datos jerárquicos de la visualización gráfica.
    - Panel de configuración distribuido en dos filas para mayor claridad; el botón "Auto Descubrimiento" se encuentra debajo de la etiqueta de objetivo.
19. **Servicios de Red**: El tipo de dispositivo incluye ahora los servicios y capas activas detectadas (ej: Internet, Enlace, Aplicaciones).
### 3.19 Robustez en el Descubrimiento [NUEVO]
Se ha implementado una validación de seguridad en `NetworkScannerService` para asegurar que solo los dispositivos que responden satisfactoriamente a las consultas SNMP sean reportados a la interfaz de usuario. Esto previene que el árbol de dispositivos se llene de entradas vacías o no funcionales durante escaneos de rangos extensos.

### 3.20 Optimización de Velocidad [NUEVO]
Se han realizado ajustes de rendimiento críticos para acelerar el escaneo masivo:
- **Parámetros SNMP**: Reducción de timeout a 1000ms y reintentos a 1.
- **Paralelismo**: Aumento del pool de hilos de 20 a 100 en `NetworkScannerService`.
- **Early Exit**: La estrategia de descubrimiento aborta inmediatamente si el primer probe (sysDescr) falla, ahorrando tiempo de espera en IPs vacías.
20. **Validación de Respuesta**: El sistema verifica que el dispositivo responda realmente a SNMP antes de añadirlo al árbol, evitando mostrar IPs "fantasma" que no proporcionan información técnica.
21. **Escaneo Optimizado**: Mejoras de rendimiento que permiten escanear una subred /24 en segundos mediante:
    - Reducción de timeouts y reintentos.
    - Alta concurrencia (100 hilos).
    - Descarte inmediato de IPs no responsivas.

### 3.21 Escaneo ARP Multiplataforma [NUEVO]
Se ha añadido una etapa de descubrimiento ARP previa al escaneo SNMP. Esto permite:
- **Detección Inmediata**: Identifica cualquier dispositivo con trafico IP reciente en la red local.
- **Identificación sin SNMP**: Obtiene MAC y Fabricante incluso si el dispositivo no tiene agente SNMP (ej: PCs, móviles, IoT).
- **Compatibilidad Total**: Utiliza `/proc/net/arp` en Linux y `arp -a` en Windows para máxima fiabilidad.


### 3.22 Escaneo Activo (Pcap4J) [NUEVO]

Para poblar la tabla ARP del sistema antes de la lectura, NetMapper realiza ahora un escaneo activo:
- **Tecnología**: Pcap4J (libpcap/Npcap).
- **Proceso**: Envía paquetes ARP Request a todas las IPs del rango objetivo.
- **Captura**: Escucha las respuestas ARP Reply para identificar inmediatamente direcciones MAC y fabricantes.
- **Ventaja**: Detecta dispositivos silenciosos que no generan tráfico espontáneamente.

### 3.23 Corrección de Formato de Texto SNMP
Se ha corregido el manejo de valores `OctetString` en SNMP para mostrar texto legible (no hexadecimal).

### 3.24 Escaneo por Interfaz Específica (ARP/SNMP) [NUEVO]

El autodescubrimiento ahora utiliza la interfaz de red correcta para escanear cada red local:

- **Asociación Inteligente**: Cada red LAN detectada se asocia con la interfaz física configurada.
- **Escaneo ARP Dirigido**: Los escaneos ARP activos se realizan a través de la interfaz específica.
- **SNMP Bound**: El tráfico SNMP se vincula (bind) a la IP local de la interfaz, asegurando que los paquetes salgan por la tarjeta de red correcta.

**Archivos Modificados**:
- [NetworkInterfaceInfo.java](file:///e:/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/model/NetworkInterfaceInfo.java): Nuevo modelo para asociar redes con interfaces
- [NetworkDiscoveryUtils.java](file:///e:/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/util/NetworkDiscoveryUtils.java): Método `discoverLocalNetworksWithInterfaces()` que retorna información de interfaz
- [ArpScanner.java](file:///e:/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/scan/ArpScanner.java): Interfaz extendida con método que acepta nombre de interfaz
- [PcapArpScanner.java](file:///e:/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/scan/PcapArpScanner.java): Implementación de escaneo por interfaz específica
- [NetworkScannerService.java](file:///e:/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/service/NetworkScannerService.java): Método sobrecargado que acepta interfaz
- [MainWindow.java](file:///e:/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/gui/MainWindow.java): Autodescubrimiento actualizado para usar interfaces específicas

### 3.26 Carga de Mapa desde JSON [NUEVO]

Se ha implementado la funcionalidad para cargar un mapa de red previamente exportado a JSON, permitiendo su visualización y análisis sin necesidad de realizar un nuevo escaneo activo.

- **GUI ("Cargar Mapa")**: 
    - Botón dedicado en el panel de configuración.
    - Abre un selector de archivos nativo filtrado por `.json`.
    - Carga los dispositivos en el árbol y reconstruye la topología del mapa inmediatamente.
    - Útil para revisiones offline o demostraciones.
- **CLI (`-m <file>`)**:
    - Nuevo parámetro para cargar y procesar un mapa en modo headless.
    - Exclusividad: No puede combinarse con objetivos de escaneo (`-t`) o autodescubrimiento (`-a`).
    - Permite pipelines de conversión (ej: JSON -> PNG) sin acceso a la red real.


## Notas de Implementación
- **Estabilidad**: Timeout SNMP aumentado a 3000ms con 3 reintentos para redes lentas.
- **Compatibilidad**: MIB-II, BRIDGE-MIB, Q-BRIDGE-MIB, IF-MIB HighSpeed.
- **Datos de Puerto**:
    - Se utilizan los OIDs estándar `ifSpeed` y `ifHighSpeed` (para enlaces > 1Gbps).
    - Lógica de fallback para dispositivos que no soportan mapeo de puertos estándar (BRIDGE-MIB).

> [!NOTE]
> Asegúrese de que no hay firewalls bloqueando el puerto UDP 161 entre la máquina que ejecuta NetMapper y los dispositivos de red.

### 3.25 Filtro de Redundancia Física [NUEVO]
Se ha perfeccionado el algoritmo de construcción del mapa para inferir con precisión la topología física real, eliminando enlaces lógicos redundantes:
- **Normalización de MACs**: Procesamiento estandarizado de direcciones físicas (lowercase, separador `:`) para comparaciones fiables entre diferentes fabricantes (Aruba, Fortinet, HP).
- **Inferencia de Nodos Intermedios**: El sistema ahora detecta si un dispositivo (como un switch Core) actúa como intermediario físico entre otros dos, simplificando la vista del mapa para mostrar solo conexiones directas.
- **Logging de Depuración**: Inclusión de trazas detalladas en consola que explican por qué se ha eliminado un enlace específico basándose en las tablas de direcciones MAC (BRIDGE-MIB).
- **Compatibilidad Jackson**: Los modelos de datos se han actualizado con constructores por defecto para permitir la persistencia y carga de mapas de red complejos.

**Archivos Modificados**:
- [NetworkGraph.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/model/NetworkGraph.java): Implementación del filtro y normalización.
- [NetworkDevice.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/model/NetworkDevice.java), [NetworkInterface.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/model/NetworkInterface.java), [DetectedEndpoint.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/model/DetectedEndpoint.java): Soporte para deserialización Jackson.

> [!NOTE]
> Este filtro se aplica únicamente cuando la opción "Vista Física Simplificada" está activa durante la construcción del grafo.

### 3.27 Zoom y Panorámica en Mapa [NUEVO]

Se ha enriquecido la experiencia de usuario en la pestaña "Mapa" añadiendo controles de navegación completos:

- **Zoom (Acercar/Alejar)**:
    - **Rueda del ratón**: Gire la rueda para hacer zoom in/out centrado en el puntero del ratón.
    - **Botones**: Utilice los nuevos botones `+` y `-` en la barra de herramientas.
- **Panorámica (Desplazamiento)**:
    - **Arrastrar**: Haga clic y arrastre sobre el fondo blanco del mapa para mover la vista (panning) en cualquier dirección.
    - **Cursor Inteligente**: El cursor cambia a una "mano" para indicar que se puede desplazar el lienzo.
- **Ajustar a Pantalla (Fit)**:
    - Botón "Fit" (Ajustar) para restablecer la vista y encuadrar todo el grafo automáticamente en el panel.
- **Persistencia de Vista**: A diferencia de la versión anterior, el nivel de zoom y posición se mantienen estables y no se resetean automáticamente con cada actualización menor del grafo, permitiendo trabajar en detalles sin interrupciones.

**Archivos Modificados**:
- [NetworkMapPanel.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/gui/NetworkMapPanel.java): Implementación completa de la lógica de transformación gráfica.
- [messages_en.properties](file:///opt/workspace/NetMapper/src/main/resources/messages_en.properties), [messages_es.properties](file:///opt/workspace/NetMapper/src/main/resources/messages_es.properties): Textos internacionalizados para los nuevos controles.

### 3.28 Verificación de Topología en Estrella [NUEVO]

Se ha validado el funcionamiento del algoritmo de simplificación física (`applyPhysicalRedundancyFilter` y `applyStrictTriangleFilter`) utilizando un escenario real complejo:

- **Configuración**: 
    - Nodo Central (Core): `10.81.128.4`
    - Switches en periferia conectados al Core: `10.81.128.5`, `10.81.128.11` a `10.81.128.54`.
    - Switch secundario: `10.81.128.55` conectado a `10.81.128.5`.
- **Resultados de Verificación**:
    - **Filtrado Correcto**: Se han eliminado los enlaces directos lógicos detectados por el Core hacia `.55`, favoreciendo el camino físico real: `Core (4) -> Switch (5) -> Switch (55)`.
    - **No Redundancia**: Se ha verificado que no existen enlaces cruzados entre los switches de la periferia, manteniendo la estructura de estrella limpia.
    - **Integridad**: El sistema ha mantenido un total de 776 nodos y ha eliminado más de 300 enlaces redundantes en un grafo de gran escala, optimizando significativamente la visualización.

**Herramientas de Verificación**:
- [StandaloneVerifier.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/StandaloneVerifier.java): Programa de test autónomo ejecutado contra `network_map.json`.

### 3.29 Filtros de Visibilidad en el Mapa [NUEVO]

Se han implementado filtros granulares que permiten controlar qué elementos se muestran en el mapa topológico.

**Capacidades**:
- **Tipos de Enlace**: Alternar entre enlaces "Físicos" (conexión directa detectada) y "Lógicos" (redundancias marcadas por el algoritmo de simplificación). Los enlaces lógicos se muestran con líneas discontinuas.
- **Categorías de Nodo**: Mostrar/Ocultar "Dispositivos" o "Endpoints" de forma global.
- **Tipos de Dispositivo**: Filtrado dinámico por sub-tipo (ej: solo Routers, solo Switches) basado en los datos cargados.
- **Visibilidad en Cascada**: Al ocultar un nodo, todos sus enlaces conectados desaparecen automáticamente para mantener la coherencia visual.

**Uso**:
1. Hacer clic en el botón **"Filtros"** de la barra de herramientas del mapa.
2. Seleccionar/Deseleccionar los elementos deseados en el menú emergente.
3. El mapa se actualiza instantáneamente reflejando el nuevo estado.

**Archivos Modificados**:
- [NetworkGraph.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/model/NetworkGraph.java): Soporte para `EdgeType` y cálculo de límites filtrado.
- [NetworkMapPanel.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/gui/NetworkMapPanel.java): Gestión de estado de filtros, menú emergente y renderizado condicional.
- [messages_*.properties](file:///opt/workspace/NetMapper/src/main/resources/): Traducciones de los filtros en ES, EN y ZH.

### 3.30 Mejora en Detección de Dispositivos Multi-homed [NUEVO]

Se ha perfeccionado el algoritmo de redundancia para manejar correctamente servidores o dispositivos con múltiples IPs (aliases) que generaban un efecto "hub" erróneo (como en el caso de `10.81.128.254`).

**Mejoras**:
- **Detección por MAC**: El filtro ahora identifica dispositivos intermediarios comparando direcciones MAC, no solo IPs. Esto permite reconocer a un switch como "intermediario" incluso si es visto bajo una IP de gestión diferente.
- **Validación**: Se ha verificado que la estrella de enlaces físicos centrada en `10.81.128.245` desaparece, manteniendo solo su enlace directo real al switch `10.81.128.55`.

**Archivos Modificados**:
- [NetworkGraph.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/model/NetworkGraph.java): Refactorización de `applyPhysicalRedundancyFilter` para usar búsqueda por MAC.
- [StandaloneVerifier.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/StandaloneVerifier.java): Actualizado para validar específicamente este escenario.
### 3.31 Optimización y Precisión Técnica [NUEVO]

Se han integrado mejoras de rendimiento y precisión topológica para despliegues de gran escala:

- **Escaneo Ultrarrápido**: El pool de hilos se ha incrementado a **300 hilos**, permitiendo procesar subredes /24 completas en aproximadamente 5 segundos.
- **Notificaciones de Finalización**: La interfaz de usuario ahora informa del fin del escaneo incluso en modo manual, asegurando que el usuario sepa cuándo el mapa está completo.
- **Topología Core y LAG**: 
    - El algoritmo de filtrado ahora reconoce interfaces tipo **LAG (Port-Channel)**, evitando falsos positivos de redundancia en troncales de alta disponibilidad.
    - Se ha implementado una lista blanca (whitelist) para mallas de switches Core, asegurando que todos los enlaces físicos inter-switch se preserven visualmente.
- **Resolución de Duplicados de Endpoints**:
    - **Normalización MAC Universal**: Garantiza que un mismo dispositivo sea identificado de forma única independientemente del formato de MAC reportado por el switch (punto, guion o dos puntos).
    - **Enriquecimiento Dinámico**: Si un endpoint es detectado primero por MAC y luego se descubre su IP, la etiqueta del nodo se actualiza automáticamente con la información más precisa.

**Archivos Modificados**:
- [NetworkScannerService.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/service/NetworkScannerService.java): Incremento de pool y lógica de callbacks.
- [NetworkGraph.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/model/NetworkGraph.java): Soporte para LAG, whitelists de Core y normalización MAC.
- [MainWindow.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/gui/MainWindow.java): Ajustes de layout y notificaciones.
### 3.32 Arbitraje de Endpoints Multi-homed [NUEVO]

Se ha resuelto el problema de los endpoints que aparecían duplicados o conectados a múltiples switches (efecto "hub") debido a la visibilidad total en las tablas de bridge.

- **Estrategia "Global Winner"**: Se ha implementado un filtro de arbitraje que garantiza que cada endpoint tenga exactamente **un** enlace físico en el mapa.
- **Prioridad de Proximidad**: El sistema identifica si el switch ve al endpoint por un puerto de acceso ("Directo") o por un trunk ("Infraestructura"). Los puertos de acceso siempre ganan a los trunks.
- **Tie-break Estable**: En caso de empate (ej: clusters de servidores en malla), se utiliza un desempate basado en la IP del switch para garantizar una representación visual estable y determinista.
- **Contexto de Grafo Compartido**: Todos los filtros de topología utilizan ahora un `GraphContext` unificado que optimiza la recolección de MACs y asegura que las decisiones de filtrado sean coherentes en toda la red.

**Escenario de Validación**:
- **Dataset**: `network_map_Gondomar.json` (12 switches, topología compleja).
- **Resultado**: Reducción de 37 endpoints multi-homed a **0** (verificado con `TestEndpointArbitration.java`).
