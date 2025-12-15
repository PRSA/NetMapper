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

1.  **Modelo (`com.netmapper.model`)**:
    -   `NetworkDevice`: Representa un dispositivo (Router/Switch). Contiene información de sistema, lista de interfaces, tablas de enrutamiento y VLANs.
    -   `NetworkInterface`: Representa un puerto o interfaz física/lógica. Almacena IP, máscara, MAC, estado, velocidad, MTU y VLANs asociadas (nativa y etiquetadas).

2.  **Núcleo y Servicios (`com.netmapper.core`, `com.netmapper.service`)**:
    -   `SnmpClient`: Wrapper sobre SNMP4J. Maneja la complejidad de sesiones UDP, retries (3), timeouts (3000ms) y operaciones PDU (`GET`, `WALK`).
    -   `NetworkScannerService`: Orquestador que inicia el escaneo. Utiliza una estrategia definida para poblar el modelo `NetworkDevice`.

3.  **Estrategias de Descubrimiento (`com.netmapper.strategy`)**:
    -   `DiscoveryStrategy`: Interfaz para definir algoritmos de descubrimiento.
    -   `StandardMibStrategy`: Implementación principal. Utiliza MIBs estándar soportados por la gran mayoría de fabricantes (Cisco, HP, Huawei, D-Link, etc.).

4.  **Interfaz Gráfica (`com.netmapper.gui`)**:
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
-   **Prevención de Duplicados**: Los dispositivos que aparecen como endpoints en otros dispositivos solo se muestran como nodos device, con conexiones device-to-device directas.
-   **Fusión Bidireccional**: Las conexiones bidireccionales se fusionan en un solo arco con etiquetas combinadas mostrando la información de ambos dispositivos.
-   **Visualización**: `NetworkMapDialog` muestra un grafo circular con nodos coloreados (azul para dispositivos, verde para endpoints) y líneas de conexión.
-   **Botón "Mapa"**: Permite al usuario ver la topología de red detectada en cualquier momento tras un escaneo.

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

## 4. Estructura del Proyecto

```
/opt/workspace/NetMapper/NetMapper/
├── src/main/java/com/netmapper/
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
-   Exportación de resultados a formatos CSV/JSON.
-   Mapa de topología visual (dibujo de grafo de red).
-   Soporte para SNMP v3 (Autenticación y Cifrado).
-   Persistencia de datos en base de datos local (SQLite).
