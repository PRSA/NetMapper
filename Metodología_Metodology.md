A continuación tienes la metodología completa, ya integrada con las mejoras, primero en castellano y luego en inglés. He mantenido fases numeradas y definiciones formales para que puedas copiarlas casi tal cual a un documento de trabajo.

***

## Metodología completa en castellano

### 0. Objetivo formal

Construir un grafo:

$$
G = (Nodos, Enlaces)
$$

Donde:

- **Nodos** = {switch, router, firewall, servidor, endpoint, dispositivo, switch_no_gestionado}
- **Enlaces** = {físicos, lógicos_directos}

Con atributos principales:

- **Enlaces físicos**:
    - `estado`: functional | redundant | blocked | transitional | disabled | access | suspect | asymmetric
    - `velocidad`
    - `duplex`
    - `MTU`
    - `VLANs` (native, tagged[])
    - `STP state` + `STP role`
    - `confidence` (0.0–1.0)
    - `physical_valid = true|false`
    - `mismatch_reason`
    - `visible = true|false` (para visualización/poda lógica)
- **Enlaces lógicos (L3 directos)**:
    - `tipo`: routing | túnel | gateway | peer
    - `protocolo`
    - `interfaz lógica`
    - `confidence`
- **Nodos**:
    - `id`
    - `ip[]`
    - `mac[]`
    - `hostname`
    - `type` ∈ {switch, router, firewall, servidor, host, dispositivo, unmanaged_switch, endpoint_ephemeral}
    - `layer` ∈ {core, distribution, access, edge, endpoint}
    - `discovery_sources[]`

***

### Fase 1 – Descubrimiento de nodos (L2/L3)

Objetivo: obtener el conjunto inicial de nodos visibles en la red.

Técnicas:

- Escaneo ARP
- ICMP ping / sweep
- Lectura de tabla ARP local
- Captura pasiva (opcional, si hay SPAN/tap)
- Resolución de nombres (DNS/NetBIOS si aplica)

Salida:

```json
Node {
  "id": "...",
  "ip": ["..."],
  "mac": ["..."],
  "oui": "...",
  "hostname": "...",
  "tipo_preliminar": "desconocido|host|router?",
  "discovery_sources": ["ARP","ICMP","PASSIVE"]
}
```


***

### Fase 1.5 – Seguimiento temporal ARP y “endpoints efímeros”

Objetivo: detectar hosts “silenciosos” o con comportamiento intermitente.

Procedimiento:

- Repetir la Fase 1 en una ventana de tiempo (ej. cada 30–60 s durante 5–10 minutos).
- Para cada IP/MAC:
    - Contar número de observaciones.
    - Calcular un marcador de estabilidad.

Clasificación:

- Si aparece en múltiples sondeos y se asocia luego a un puerto (FDB):
    - Se tratará como endpoint normal.
- Si aparece en ARP repetidas veces pero nunca se logra asociar de forma estable a una MAC/puerto:
    - Crear/etiquetar el nodo como:
        - `type = "endpoint_ephemeral"`
        - `confidence` bajo (ej. 0.3–0.5)
        - `notes = "ARP only, no FDB stable"`

***

### Fase 2 – Inventario SNMP estructural

Objetivo: clasificar nodos y obtener interfaces.

Consultas:

- `sysDescr`, `sysName`, `sysObjectID`
- IF-MIB mínimo:
    - ifIndex, ifDescr, ifAlias
    - ifType, ifSpeed, ifMtu
    - ifOperStatus, ifAdminStatus
- IP-MIB mínimo:
    - Interfaces L3, direcciones IP, máscaras

Clasificación:

- `type` ∈ {switch, router, firewall, servidor, host, dispositivo}
- Asociar interfaces físicas y lógicas al nodo.

Salida extendida:

```json
Node {
  "id": "sw1",
  "type": "switch",
  "layer": null,
  "interfaces": [
    {
      "ifIndex": 1,
      "name": "Gi0/1",
      "speed": "1G",
      "duplex": "full",
      "mtu": 1500,
      "status": "up"
    }
  ],
  "discovery_sources": ["SNMP","ARP"]
}
```


***

### Fase 3 – Descubrimiento de enlaces físicos directos (LLDP/CDP)

Objetivo: detectar enlaces físicos directos anunciados por discovery L2.

Regla:

Todo enlace LLDP/CDP detectado se almacena como:

```json
PhysicalLink {
  "nodeA": "...",
  "portA": "...",
  "nodeB": "...",
  "portB": "...",
  "source": "LLDP|CDP",
  "confidence": 1.0,
  "stp_state": "unknown",
  "role": "functional",
  "discovery_sources": ["LLDP"],
  "physical_valid": null,
  "visible": true
}
```

Importante:

- Nunca se elimina.
- Posteriormente podrá bajar su `confidence` si hay conflictos o envejecimiento.

***

### Fase 4 – Inferencia de enlaces físicos por tablas MAC (FDB)

Objetivo: inferir enlaces físicos a partir de FDB.

Procedimiento:

- Leer FDB de switches gestionados.
- Mapear MAC ↔ puerto ↔ nodo.

Creación de enlaces inferidos:

```json
PhysicalLink {
  "nodeA": "...",
  "portA": "...",
  "nodeB": "...",
  "portB": "...",
  "source": "FDB",
  "confidence": 0.7,
  "stp_state": "unknown",
  "role": "unknown",
  "discovery_sources": ["FDB"]
}
```

Fusión con enlaces LLDP existentes:

- Si existe un enlace LLDP que coincide en nodos y puertos:
    - Fusionar datos:
        - Mantener `source = ["LLDP","FDB"]`
        - `confidence` base sigue siendo alto (1.0 antes de aplicar modelo temporal).

Inferencia de switches no gestionados:

- Si un puerto muestra muchas MAC de distintos nodos sin corresponder a un nodo SNMP/LLDP:
    - Crear nodo:

```json
Node {
  "id": "unmanaged_X",
  "type": "unmanaged_switch",
  "discovery_sources": ["FDB_pattern"]
}
```

    - Enlazar el puerto del switch gestionado a `unmanaged_X` con `confidence` medio-bajo.

***

### Fase 5 – Clasificación de enlaces físicos mediante STP

Objetivo: clasificar enlaces sin eliminarlos.

Consultas:

- `dot1dStpPortState`
- `dot1dStpPortRole` (si disponible)

Mapeo:

- STP state → `role`:
    - `forwarding` → `functional`
    - `blocking` → `blocked`
    - `listening/learning` → `transitional`
    - `disabled` → `disabled`

Actualización:

```json
link.role = functional | blocked | transitional | disabled
link.stp_state = ...
```


***

### Fase 6 – Validación física

Objetivo: detectar inconsistencias físicas.

Comparar por cada extremo del enlace:

- `speed`
- `duplex`
- `MTU`
- Contadores de errores, discards, etc. (opcional)

Resultado:

```json
link.physical_valid = true|false
link.mismatch_reason = "speed_mismatch|duplex_mismatch|mtu_mismatch|none"
```

Nunca se elimina el enlace; solo se clasifica.

***

### Fase 7 – Asignación de VLANs

Objetivo: añadir contexto de VLAN a los enlaces.

Consultas:

- Tablas de VLAN (p. ej. Q-BRIDGE-MIB, configuración de puerto).

Atributos añadidos:

```json
link.vlan_native = <id>
link.vlan_tagged = [<id1>, <id2>, ...]
```


***

### Fase 8 – Descubrimiento de enlaces lógicos directos (L3)

Objetivo: detectar relaciones L3 reales (no transitivas):

- router ↔ router
- router ↔ firewall
- firewall ↔ core
- túneles directos

Fuentes:

- ipRouteTable / ipCidrRouteTable
- Primer salto de traceroute
- Interfaces L3
- VRRP/HSRP
- Vecinos BGP/OSPF (si SNMP/CLI)

Regla:

Solo enlaces directos:

```json
LogicalLink {
  "nodeA": "...",
  "nodeB": "...",
  "protocol": "OSPF|BGP|static|IPSEC|...",
  "interfaceA": "...",
  "interfaceB": "...",
  "confidence": <0.0–1.0>,
  "discovery_sources": ["ROUTING","TRACEROUTE"]
}
```

No válidos:

- host → internet
- host → servidor remoto a través de N saltos.

***

### Fase 9 – Resolución de endpoints

Objetivo: vincular hosts/endpoints a puertos de acceso.

Procedimiento:

- Usar FDB + ARP +, si existe, información de switch de acceso (port-security, etc.).

Se crea:

```json
PhysicalLink {
  "type": "physical",
  "nodeA": "hostX",
  "portA": "eth0",
  "nodeB": "sw_access",
  "portB": "Gi0/10",
  "role": "access",
  "confidence": ...,
  "discovery_sources": ["FDB","ARP"]
}
```


***

### Fase 10 – Clasificación jerárquica de nodos

Objetivo: etiquetar nodos según su función.

Atributo:

```json
node.layer = "core" | "distribution" | "access" | "edge" | "endpoint"
```

Criterios (flexibles):

- `core`: baja cantidad de puertos de acceso, muchas conexiones troncales.
- `distribution`: une varios switches de acceso.
- `access`: la mayoría de puertos son de acceso a endpoints.
- `edge`: firewalls, routers de borde.
- `endpoint`: hosts/servidores/dispositivos finales.

***

### Fase 11 – Modelo de “confidence” y grafo final (sin poda destructiva)

Objetivo: calcular `confidence` final para cada enlace y nodo y construir el grafo.

Modelo:

- `confidence = source_weight × temporal_decay × consistency × (1 - conflict_penalty)`

Donde:

- `source_weight` (ejemplo):
    - LLDP/CDP: 1.0
    - FDB: 0.7
    - STP: 0.5
    - Routing/OSPF/BGP: 0.7–0.8
- `temporal_decay = e^(-age_hours / half_life)` (p. ej. half_life = 24 h).
- `consistency = observaciones_en_ventana / observaciones_esperadas`.
- `conflict_penalty`:
    - 0.0 si no hay conflicto.
    - 0.3 si LLDP vs FDB se contradicen, etc.

Poda lógica (no destructiva):

- Definir umbral de visualización, p. ej. `threshold_visible = 0.15`.
- Si `confidence < threshold_visible`:
    - `link.visible = false`
- En el grafo se incluyen todos los enlaces, pero las herramientas de visualización pueden filtrar por `visible` o por `confidence`.

Salida final (esquema):

```json
{
  "nodes": [ ... ],
  "links": [ ... ],
  "statistics": { ... },
  "metadata": { ... }
}
```


***

## Full methodology in English

### 0. Formal objective

Build a graph:

$$
G = (Nodes, Links)
$$

Where:

- **Nodes** = {switch, router, firewall, server, endpoint, device, unmanaged_switch}
- **Links** = {physical, logical_direct}

Main attributes:

- **Physical links**:
    - `role`: functional | redundant | blocked | transitional | disabled | access | suspect | asymmetric
    - `speed`
    - `duplex`
    - `MTU`
    - `VLANs` (native, tagged[])
    - `STP state` + `STP role`
    - `confidence` (0.0–1.0)
    - `physical_valid = true|false`
    - `mismatch_reason`
    - `visible = true|false`
- **Logical (L3 direct) links**:
    - `type`: routing | tunnel | gateway | peer
    - `protocol`
    - `logical_interface`
    - `confidence`
- **Nodes**:
    - `id`
    - `ip[]`
    - `mac[]`
    - `hostname`
    - `type` ∈ {switch, router, firewall, server, host, device, unmanaged_switch, endpoint_ephemeral}
    - `layer` ∈ {core, distribution, access, edge, endpoint}
    - `discovery_sources[]`

***

### Phase 1 – Node discovery (L2/L3)

Goal: build the initial set of visible nodes.

Techniques:

- ARP scan
- ICMP ping / sweep
- Local ARP table read
- Passive capture (optional, SPAN/tap)
- Name resolution (DNS/NetBIOS where applicable)

Output:

```json
Node {
  "id": "...",
  "ip": ["..."],
  "mac": ["..."],
  "oui": "...",
  "hostname": "...",
  "preliminary_type": "unknown|host|router?",
  "discovery_sources": ["ARP","ICMP","PASSIVE"]
}
```


***

### Phase 1.5 – Temporal ARP tracking and “ephemeral endpoints”

Goal: detect “silent” or intermittent hosts.

Procedure:

- Repeat Phase 1 over a time window (e.g. every 30–60 s for 5–10 minutes).
- For each IP/MAC:
    - Count number of observations.
    - Compute a stability score.

Classification:

- If it appears in multiple scans and later gets a stable FDB/port association:
    - Treat as a normal endpoint.
- If it appears repeatedly in ARP but never gets a stable FDB/port association:
    - Create/mark node as:
        - `type = "endpoint_ephemeral"`
        - `confidence` low (e.g. 0.3–0.5)
        - `notes = "ARP only, no stable FDB"`

***

### Phase 2 – Structural SNMP inventory

Goal: classify nodes and get their interfaces.

Queries:

- `sysDescr`, `sysName`, `sysObjectID`
- IF-MIB (minimal):
    - ifIndex, ifDescr, ifAlias
    - ifType, ifSpeed, ifMtu
    - ifOperStatus, ifAdminStatus
- IP-MIB (minimal):
    - L3 interfaces, IP addresses, masks

Classification:

- `type` ∈ {switch, router, firewall, server, host, device}
- Attach physical and logical interfaces to nodes.

Output:

```json
Node {
  "id": "sw1",
  "type": "switch",
  "layer": null,
  "interfaces": [
    {
      "ifIndex": 1,
      "name": "Gi0/1",
      "speed": "1G",
      "duplex": "full",
      "mtu": 1500,
      "status": "up"
    }
  ],
  "discovery_sources": ["SNMP","ARP"]
}
```


***

### Phase 3 – Direct physical link discovery (LLDP/CDP)

Goal: detect direct physical links announced by L2 discovery protocols.

Rule:

Every LLDP/CDP link is stored as:

```json
PhysicalLink {
  "nodeA": "...",
  "portA": "...",
  "nodeB": "...",
  "portB": "...",
  "source": "LLDP|CDP",
  "confidence": 1.0,
  "stp_state": "unknown",
  "role": "functional",
  "discovery_sources": ["LLDP"],
  "physical_valid": null,
  "visible": true
}
```

Important:

- Never deleted.
- Confidence may later be reduced due to conflicts or aging.

***

### Phase 4 – Physical link inference from MAC tables (FDB)

Goal: infer physical links from forwarding databases.

Procedure:

- Read FDB on managed switches.
- Map MAC ↔ port ↔ node.

Create inferred links:

```json
PhysicalLink {
  "nodeA": "...",
  "portA": "...",
  "nodeB": "...",
  "portB": "...",
  "source": "FDB",
  "confidence": 0.7,
  "stp_state": "unknown",
  "role": "unknown",
  "discovery_sources": ["FDB"]
}
```

Merge with existing LLDP links:

- If an LLDP link exists with same nodes and ports:
    - Merge data:
        - `source = ["LLDP","FDB"]`
        - Keep high base `confidence` before temporal model.

Unmanaged switch inference:

- If a port shows many stable MACs for different endpoints and no SNMP/LLDP device on that segment:
    - Create node:

```json
Node {
  "id": "unmanaged_X",
  "type": "unmanaged_switch",
  "discovery_sources": ["FDB_pattern"]
}
```

    - Link managed switch port to `unmanaged_X` with medium/low confidence.

***

### Phase 5 – Physical link classification via STP

Goal: classify physical links using STP without pruning.

Queries:

- `dot1dStpPortState`
- `dot1dStpPortRole` (where available)

Mapping:

- STP state → `role`:
    - `forwarding` → `functional`
    - `blocking` → `blocked`
    - `listening/learning` → `transitional`
    - `disabled` → `disabled`

Update:

```json
link.role = functional | blocked | transitional | disabled
link.stp_state = ...
```


***

### Phase 6 – Physical validation

Goal: detect physical inconsistencies.

Compare per link endpoint:

- `speed`
- `duplex`
- `MTU`
- Error counters, discards, etc. (optional)

Result:

```json
link.physical_valid = true|false
link.mismatch_reason = "speed_mismatch|duplex_mismatch|mtu_mismatch|none"
```

Links are never deleted, only flagged.

***

### Phase 7 – VLAN assignment

Goal: enrich links with VLAN information.

Queries:

- VLAN tables (e.g. Q-BRIDGE-MIB or configuration).

Added attributes:

```json
link.vlan_native = <id>
link.vlan_tagged = [<id1>, <id2>, ...]
```


***

### Phase 8 – Direct logical link discovery (L3)

Goal: detect real (non-transitive) L3 relationships:

- router ↔ router
- router ↔ firewall
- firewall ↔ core
- direct tunnels

Sources:

- ipRouteTable / ipCidrRouteTable
- First hop in traceroute
- L3 interfaces
- VRRP/HSRP
- BGP/OSPF neighbors (if available)

Rule:

Only direct links:

```json
LogicalLink {
  "nodeA": "...",
  "nodeB": "...",
  "protocol": "OSPF|BGP|static|IPSEC|...",
  "interfaceA": "...",
  "interfaceB": "...",
  "confidence": <0.0–1.0>,
  "discovery_sources": ["ROUTING","TRACEROUTE"]
}
```

Invalid examples:

- host → internet
- host → remote server via multiple hops.

***

### Phase 9 – Endpoint resolution

Goal: attach endpoints/hosts to access switch ports.

Procedure:

- Use FDB + ARP + access switch info (port-security, etc. when available).

Create:

```json
PhysicalLink {
  "type": "physical",
  "nodeA": "hostX",
  "portA": "eth0",
  "nodeB": "sw_access",
  "portB": "Gi0/10",
  "role": "access",
  "confidence": ...,
  "discovery_sources": ["FDB","ARP"]
}
```


***

### Phase 10 – Node hierarchical classification

Goal: label nodes by their functional role/layer.

Attribute:

```json
node.layer = "core" | "distribution" | "access" | "edge" | "endpoint"
```

Heuristic criteria:

- `core`: few access ports, many uplinks/trunks.
- `distribution`: aggregates multiple access switches.
- `access`: mostly access ports to endpoints.
- `edge`: firewalls, WAN routers.
- `endpoint`: hosts/servers/devices.

***

### Phase 11 – Confidence model and final graph (non-destructive)

Goal: compute final `confidence` for all links and nodes and build the final graph.

Model:

- `confidence = source_weight × temporal_decay × consistency × (1 - conflict_penalty)`

Where:

- `source_weight` (example):
    - LLDP/CDP: 1.0
    - FDB: 0.7
    - STP: 0.5
    - Routing/OSPF/BGP: 0.7–0.8
- `temporal_decay = e^(-age_hours / half_life)` (e.g. half_life = 24 h).
- `consistency = observations_in_window / expected_observations`.
- `conflict_penalty`:
    - 0.0 when no conflict.
    - 0.3 when LLDP vs FDB or STP vs FDB disagree.

Logical (non-destructive) pruning:

- Define visualization threshold, e.g. `threshold_visible = 0.15`.
- If `confidence < threshold_visible`:
    - `link.visible = false`
- The graph contains all discovered links; visualization tools filter by `visible` or `confidence`.

Final output (schema):

```json
{
  "nodes": [ ... ],
  "links": [ ... ],
  "statistics": { ... },
  "metadata": { ... }
}
```


***
