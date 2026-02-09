### ROL
Actúa como un **Arquitecto de Software Senior y Especialista en Teoría de Grafos** con experiencia avanzada en protocolos de red (L2/L3).

### OBJETIVO
Tu tarea es diseñar la arquitectura e implementar la lógica central del **Grafo de Topología Real (GTR)** (leer "Lógica GTR.md"), partiendo de la base de que ya existe un sistema implementado y debe ser adaptado a esta lógica. Este sistema ingiere datos crudos de red y construye un grafo multi-capa unificado.
Debes proveer la estructura de código y la lógica algorítmica para implementar esto en un lenguaje de alto rendimiento (preferiblemente **Java** o **Rust**).

### ESPECIFICACIÓN TÉCNICA: PROTOCOLO GTR

A continuación se detalla la lógica de negocio que debes convertir en código.

#### 1. Definición del Modelo de Datos (Graph Schema)
El sistema no es un mapa plano, es un grafo con atributos dimensionales.
* **Nodos:** `ManagedDevice` (Switch/Router), `Host` (PC/IoT), `ShadowNode` (Inferido), `Cluster` (Stack).
* **Aristas (Edges):**
    * `L1_Physical`: Conexión física verificada.
    * `L2_Association`: Pertenencia a Broadcast Domain.
    * `L3_Direct`: Adyacencia IP (mismo segmento).
    * `L3_Route`: Next-hop lógico.

#### 2. MOTOR 1: Construcción del Backbone (Determinista)
**Input:** Tablas de vecinos (LLDP/CDP) y Configuración de Puertos (LACP).
**Algoritmo:**
1.  **Ingesta de Vecindad:** Iterar sobre cada `Switch_Local`.
    * Si `Switch_Remoto` en tabla de vecinos existe en Inventario -> Crear arista `L1_Physical`.
    * **Regla de Negocio:** Marcar puertos origen/destino como `role = UPLINK`.
2.  **Agregación (LACP):**
    * Detectar enlaces paralelos entre el mismo par de nodos `(A, B)`.
    * Agrupar visualmente en `Link_Group` pero mantener referencias a los N cables físicos en la estructura de datos para redundancia.
3.  **Stacking:**
    * Si múltiples Serial Numbers comparten una IP de gestión -> Instanciar `Cluster_Node` conteniendo múltiples `Physical_Chassis`.

#### 3. MOTOR 2: Construcción del Edge (Inferencia Probabilística)
**Input:** Tablas FDB (MAC Address Tables) y datos ARP.
**Algoritmo de Triangulación Negativa (Core Logic):**
1.  **Filtrado de Ruido:** Descartar MACs Multicast, Broadcast y MACs propias de interfaces (SVI/CPU).
2.  **Mapeo de Candidatos:**
    * Para una `MAC_Target`: Buscar en *todas* las tablas FDB de todos los switches.
    * **CRÍTICO (Negative Filter):** Si la `MAC_Target` aparece en un puerto con `role == UPLINK` (detectado en Motor 1), **DESCARTAR** esa entrada. (Significa que la MAC está transitando, no conectada ahí).
3.  **Resolución de Posición:**
    * **Caso A (Unicidad):** Si tras el filtro, la MAC solo reside en `Switch_Z:Port_10` -> Crear arista `L1_Physical` (Confianza: Verified).
    * **Caso B (Hub/Unmanaged):** Si `Switch_Z:Port_20` tiene > N MACs (ej. >3) y el puerto NO es Uplink -> Inferir nodo intermedio `Inferred_Switch`. Conectar `Switch_Z` <-> `Inferred_Switch` <-> `Hosts`.
    * **Caso C (Loop/Movilidad):** Si la MAC aparece como "local" en Switch A y Switch B simultáneamente:
        * Comparar `last_seen`. Si es idéntico -> **Error/Loop**. Si difiere -> **Movilidad (Roaming)**, quedarse con el timestamp más reciente.

#### 4. MOTOR 3: Capa Lógica (L3)
**Algoritmo:**
1.  **Peer Detection:** Si Node A y Node B tienen IPs en la misma subred (ej. /24) y comparten VLAN -> Crear arista `L3_Direct`.
2.  **Routing:** Parsear tabla de rutas. Si ruta `0.0.0.0/0` apunta a Gateway X -> Crear arista `L3_Route`.
3.  **Inferencia de Seguridad:** Si existe camino físico (L1) pero `Traceroute` falla o muestra anomalías -> Marcar enlace lógico con `traffic_inspection = suspected` (Firewall transparente).

#### 5. Estructura de Salida (JSON Contract)
El objeto Enlace final debe seguir esta estructura:
```json
{
  "link_id": "UUID",
  "source": { "node_id": "...", "port": "...", "type": "managed" },
  "target": { "node_id": "...", "mac": "...", "type": "shadow" },
  "layers": {
    "physical": { "status": "verified|inferred", "method": "lldp|fdb_triangulation" },
    "logical": { "subnet": "10.0.0.0/24", "vlan": 10 }
  }
}