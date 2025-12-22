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
3.  **Configuración**: Utiliza el selector de idioma en la parte superior derecha para cambiar entre Español e Inglés instantáneamente.
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
12. **Internacionalización**: Soporte dinámico para múltiples idiomas (Español/English) con selector en tiempo real.
13. **Identificación de Fabricantes**: Todas las direcciones MAC se muestran con información del fabricante cuando está disponible.
14. **Detección de VLANs en Linux**: Fallback automático para detectar VLANs en sistemas Linux.
15. **Descubrimiento Automático de Redes**: Botón "Descubrimiento Automático" que detecta interfaces y subredes locales.
16. **Selector de Idioma**: Permite cambiar el idioma de toda la interfaz sin reiniciar la aplicación.
17. **Exportación e Impresión del Mapa**: Botones dedicados en la barra de herramientas del mapa:
    - **PNG**: Exporta el estado actual del mapa como una imagen `.png`.
    - **PDF**: Generas un documento PDF con el mapa.
    - **Imprimir**: Envía el mapa directamente a la impresora configurada.
18. **Layout de Pestañas e Interfaz Multilínea**:
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

### 3.23 Corrección de Formato de Texto SNMP [NUEVO]

Se ha corregido el manejo de valores `OctetString` en SNMP para mostrar texto legible:
- **Problema**: Los campos de texto como `sysContact`, `sysLocation` y `sysName` se mostraban en formato hexadecimal (ej: `48:65:6C:6C:6F` en lugar de `Hello`).
- **Solución**: El cliente SNMP detecta ahora valores `OctetString` y los convierte correctamente a cadenas de caracteres legibles.
- **Impacto**: Toda la información de contacto, ubicación y nombres del sistema se muestra ahora en formato de texto normal.

## Notas de Implementación
- **Estabilidad**: Timeout SNMP aumentado a 3000ms con 3 reintentos para redes lentas.
- **Compatibilidad**: MIB-II, BRIDGE-MIB, Q-BRIDGE-MIB, IF-MIB HighSpeed.
- **Datos de Puerto**:
    - Se utilizan los OIDs estándar `ifSpeed` y `ifHighSpeed` (para enlaces > 1Gbps).
    - Lógica de fallback para dispositivos que no soportan mapeo de puertos estándar (BRIDGE-MIB).

> [!NOTE]
> Asegúrese de que no hay firewalls bloqueando el puerto UDP 161 entre la máquina que ejecuta NetMapper y los dispositivos de red.
