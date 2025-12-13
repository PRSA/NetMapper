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
    mvn exec:java -Dexec.mainClass="com.netmapper.Main"
    ```
    O usando el classpath generado:
    ```bash
    java -cp target/classes:$(mvn dependency:build-classpath | grep -v '\[INFO\]' | tail -1) com.netmapper.Main
    ```

## Funcionalidades
La aplicación permite introducir una dirección **IP** y una **Comunidad** (por defecto `public`). Al pulsar "Escanear":

1.  **Información del Sistema**: 
    - Detección automática de **Marca y Modelo** (incluyendo Apple, Cisco, Huawei, etc.).
    - Descripción, ubicación, contacto y tiempo de actividad.
2.  **Interfaces**: 
    - Lista detallada de interfaces.
    - **Configuración**: MTU, Velocidad (bps), Tipo de Interfaz.
    - **Estado**: Admin/Oper Status.
    - Direcciones Físicas (MAC) e IP/Máscara.
3.  **Equipos Conectados**: Direcciones MAC detectadas por puerto (BRIDGE-MIB).
4.  **Tabla de Rutas**: Destinos y gateways.
5.  **VLANs**: IDs y nombres de VLANs (Q-BRIDGE-MIB).

## Notas de Implementación
- **Estabilidad**: Timeout SNMP aumentado a 3000ms con 3 reintentos para redes lentas.
- **Compatibilidad**: MIB-II, BRIDGE-MIB, Q-BRIDGE-MIB.
- **Datos de Puerto**: Se utilizan los OIDs estándar `ifSpeed`, `ifMtu`, `ifType` para enriquecer la información.

> [!NOTE]
> Asegúrese de que no hay firewalls bloqueando el puerto UDP 161 entre la máquina que ejecuta NetMapper y los dispositivos de red.
