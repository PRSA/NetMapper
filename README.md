# NetMapper - Sistema de Mapeo de Red SNMP

NetMapper es una aplicación Java diseñada para descubrir y visualizar información detallada de dispositivos de red (Switches, Routers) mediante SNMP.

## Características

- **Descubrimiento Automático**: Obtiene información del sistema, interfaces, direcciones IP y máscaras.
- **Soporte de VLANs**: Detecta VLANs configuradas (Q-BRIDGE-MIB).
- **Mapeo de Puertos**: Identifica qué dispositivos están conectados a cada puerto mediante la tabla de direcciones MAC (BRIDGE-MIB).
- **Identificación de Fabricantes**: Resolución automática de fabricantes por MAC (Offline + Online con Fallback).
- **Tabla de Rutas**: Visualiza la tabla de enrutamiento del dispositivo.
- **Interfaz Gráfica**: Visualización clara en árbol mediante Java Swing.

## Requisitos

- Java 11 o superior.
- Maven 3.x.
- Acceso a dispositivos con SNMP v2c habilitado.

## Compilación y Ejecución

```bash
# Compilar proyecto y tests
mvn clean compile test

# Ejecutar aplicación
mvn exec:java -Dexec.mainClass="com.netmapper.Main"
```

## Uso

1. Inicie la aplicación.
2. Ingrese la IP del dispositivo objetivo (ej. Gateway o Switch Core).
3. Ingrese la comunidad SNMP (por defecto `public`).
4. Haga clic en "Escanear".
5. Explore los resultados en el árbol de la izquierda.

## Estructura del Proyecto

- `com.netmapper.core`: Cliente SNMP.
- `com.netmapper.model`: Modelos de datos (Device, Interface).
- `com.netmapper.strategy`: Estrategias de recolección (MIB-II, Bridge, Q-Bridge).
- `com.netmapper.gui`: Interfaz Swing.
