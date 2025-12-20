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
    -   `SnmpClient`: Wrapper sobre SNMP4J. Maneja la complejidad de sesiones UDP, retries (3), timeouts (3000ms) y operaciones PDU (`GET`, `WALK`).
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
Se ha preparado la aplicación para soportar múltiples idiomas:
-   **Infraestructura**: Clase `Messages` para gestión de ResourceBundles.
-   **Resource Bundles**: `messages.properties` (default ES), `messages_es.properties`, `messages_en.properties`.
-   **Componentes Traducidos**: Todos los elementos de UI principales (títulos, botones, etiquetas, tooltips, mensajes de validación).
-   **Detección Automática**: Usa el locale del sistema por defecto, con capacidad de cambio manual.

### 3.11 Visualización de Vendor en Todas las MACs
Se ha completado la integración de información de fabricante en todas las ubicaciones donde se muestran direcciones MAC:
-   **Interfaces de Dispositivos**: En el árbol de detalles, las MACs de interfaces ahora se muestran con formato "MAC: XX:XX:XX:XX:XX:XX (Vendor Name)".
-   **Endpoints Detectados**: Los equipos detectados en puertos ya mostraban vendor desde su implementación inicial.
-   **Mapa de Red**: Las etiquetas de arcos y nodos ya incluían información de vendor para interfaces y endpoints.
-   **Mecanismo Unificado**: Todas las ubicaciones utilizan `MacVendorUtils.getVendor()` que implementa:
    -   Cache local persistente en `mac_vendors.properties`.
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



### 3.15 Exportación e Impresión del Mapa de Red
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

### 3.18 Visualización de sysServices
Se ha implementado el soporte para mostrar los servicios de red activos según RFC 1213:
- **Lógica**: Decodificación de los bits del campo `sysServices` para identificar capas (Physical, Datalink, Internet, End-to-End, Applications).
- **Visualización**: Se añaden entre paréntesis al tipo de dispositivo en el árbol de resultados.

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
