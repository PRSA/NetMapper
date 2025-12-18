# NetMapper - Manual de Usuario y Verificación

Este documento describe cómo ejecutar y utilizar la aplicación NetMapper para visualizar información de dispositivos de red vía SNMP.

## Requisitos Previos
- Java JDK 11 o superior.
- Maven 3.x para compilar.
- Acceso de red a dispositivos con agente SNMP habilitado (v2c).

## Compilación y Pruebas

1.  **Compilar y Ejecutar Tests:**
    ```bash
    mvn clean test
    ```
    Resultados esperados:
    > Tests run: 4, Failures: 0, Errors: 0, Skipped: 0

2.  **Ejecutar la aplicación:**
    ```bash
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
10. **Mapa de Red**: Visualización gráfica de la topología detectada mostrando dispositivos y endpoints conectados.
    - Etiquetas de nodos enriquecidas (nombre, modelo, vendor, IP)
    - Etiquetas de arcos con información de interfaz (descripción, MAC, vendor)
    - Prevención de nodos duplicados mediante unificación inteligente de IP y MAC (insensible a mayúsculas)
    - Fusionar arcos bidireccionales para mejor legibilidad
    - Iconos representativos y colores específicos según el tipo de dispositivo (Router, Switch, Firewall, Impresora, etc.)
11. **Validación de Entrada**: Verificación del formato de objetivo antes de escanear (IP, CIDR, IP/Máscara, Rango, Lista).
12. **Internacionalización**: Soporte para múltiples idiomas (Español/English) basado en locale del sistema.
13. **Identificación de Fabricantes**: Todas las direcciones MAC se muestran con información del fabricante cuando está disponible (interfaces, endpoints, mapa de red).
14. **Detección de VLANs en Linux**: Fallback automático para detectar VLANs en sistemas Linux mediante análisis de nomenclatura de interfaces (ej: `wlp0s20f3.35` → VLAN 35).

## Notas de Implementación
- **Estabilidad**: Timeout SNMP aumentado a 3000ms con 3 reintentos para redes lentas.
- **Compatibilidad**: MIB-II, BRIDGE-MIB, Q-BRIDGE-MIB, IF-MIB HighSpeed.
- **Datos de Puerto**: 
    - Se utilizan los OIDs estándar `ifSpeed` y `ifHighSpeed` (para enlaces > 1Gbps).
    - Lógica de fallback para dispositivos que no soportan mapeo de puertos estándar (BRIDGE-MIB).

> [!NOTE]
> Asegúrese de que no hay firewalls bloqueando el puerto UDP 161 entre la máquina que ejecuta NetMapper y los dispositivos de red.
