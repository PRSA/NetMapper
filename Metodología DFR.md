**Metodología Unificada de Descubrimiento Forense de Redes (MUDFR)**.

### ---

**1\. Principios Fundamentales (Axiomas del Sistema)**

1. **Existencia por Inferencia (Shadow Reality):** Un dispositivo existe si la infraestructura de red (switches/routers) lo ve conmutar tráfico, independientemente de si responde a Ping/SNMP. La "no respuesta" es un atributo de gestión (mgmt\_state="unreachable"), no de existencia.  
2. **Jerarquía de la Verdad:**  
   * **Verdad Física (Capa 1/2):** Lo que dicen las tablas FDB (MAC Address Tables) de los switches gestionados. Es irrefutable sobre la ubicación.  
   * **Verdad Lógica (Capa 3):** Lo que dicen las tablas ARP y de Rutas.  
   * **Verdad Declarada (Capa 7):** Lo que el dispositivo dice de sí mismo (SNMP/SSH). Es la menos confiable en seguridad (puede mentir) y la más difícil de obtener (firewalls).  
3. **No Transitividad de la Confianza:** Que A hable con B y B hable con C, no implica que A pueda hablar con C. El grafo debe modelar "quién ve a quién".  
4. **Triangulación Negativa:** Si una MAC se ve en el puerto X, y el puerto X *no* es un enlace entre switches (uplink), entonces el dispositivo está físicamente ahí (o en un hub colgado de ahí).

### ---

**2\. Flujo Operativo Paso a Paso**

#### **Fase 0: Definición de Perfiles y "Reglas de Silencio"**

*(Humano)*

1. **Inventario de "Testigos" (Infraestructura):** Credenciales solo para Switches, Routers y Firewalls Core.  
   * *Nota:* Aquí se define la mezcla SNMPv1/2c/3. El sistema probará en cascada *solo contra estos equipos de confianza*.  
2. **Zonas de Silencio (Black Holes):**  
   * Subredes donde **jamás** se envía un paquete activo (ej. redes SCADA, electromedicina).  
   * discovery\_mode \= "passive\_triangulation\_only".  
3. **Configuración de Ingesta:**  
   * Frecuencia de dumps de tablas ARP/FDB (ej. cada 15 min).  
   * Frecuencia de LLDP (ej. cada 1 hora).

#### **Fase 1: Ingesta de Infraestructura (Los Cimientos)**

*(Sistema \- Automático)*  
El sistema interroga **solamente** a los equipos de red (los "Testigos") usando el perfil SNMP/SSH adecuado.

1. **Recolección Masiva:**  
   * lldpRemTable (Topología Core).  
   * dot1dTpFdbTable / Q-BRIDGE-MIB (Tablas MAC de cada puerto).  
   * ipNetToMediaTable / ipNetToPhysicalTable (Tablas ARP globales).  
   * ipRouteTable (Lógica L3).  
2. **Construcción del Esqueleto (Core Backbone):**  
   * Se crean nodos type="switch" o type="router".  
   * Se crean enlaces type="physical\_verified" **solo** si hay confirmación bidireccional LLDP/CDP.  
   * **Acción Crítica:** Se marcan los puertos involucrados en estos enlaces como role="uplink".

#### **Fase 2: Generación de Nodos Sombra (La Realidad Oculta)**

*(Motor de Inferencia)*  
Aquí detectamos los dispositivos que no responden (securizados).

1. **Fusión ARP (L3 \-\> L2):**  
   * Entrada: IP 10.0.0.5 \-\> MAC AA:BB:CC... (visto en Router Core).  
   * Acción: Buscar si la IP ya existe. Si no, crear Node:  
     * id: shadow\_10.0.0.5  
     * type: shadow\_host  
     * state: inferred  
     * discovery\_source: arp\_snoop  
2. **Fusión L2 Pura (L2 Only):**  
   * Entrada: MAC DD:EE:FF... (visto en FDB Switch Acceso) que **no** tiene entrada ARP asociada.  
   * Acción: Crear Node:  
     * id: shadow\_l2\_DD:EE:FF  
     * type: shadow\_device (posible bridge transparente, o dispositivo en subred muda).

#### **Fase 3: Triangulación FDB (El Mapeo Físico)**

*(Algoritmo Crítico \- Ver detalle en sección 3\)*  
El sistema ubica físicamente a cada "Nodo Sombra" sin enviarle ni un solo paquete.

1. Para cada shadow\_node con MAC conocida:  
   * Buscar esa MAC en todas las tablas FDB recolectadas.  
   * Descartar ocurrencias en puertos marcados como role="uplink" (Fase 1).  
2. **Resultado Determinista:**  
   * Si queda **un solo puerto** candidato (Switch X, Puerto 12):  
     * Crear Enlace Físico: Switch X:Port 12 \<--\> Shadow Node.  
     * confidence: 0.95.  
     * method: fdb\_triangulation.  
3. **Resultado Heurístico (Hubs/Unmanaged):**  
   * Si en el Puerto 12 del Switch X aparecen 5 MACs distintas (y no es uplink):  
     * Inferir: Existe un unmanaged\_switch colgado del Puerto 12\.  
     * Crear Enlace: Switch X:Port 12 \<--\> Unmanaged Switch.  
     * Crear Enlaces: Unmanaged Switch \<--\> Shadow Node 1..5.

#### **Fase 4: Enriquecimiento Quirúrgico (Opcional y Controlado)**

*(Activo \- Solo si política permite)*  
Solo ahora intentamos hablar con el dispositivo.

1. **Rate Limiting Estricto:** Máximo 1 host simultáneo por subred.  
2. **Ping Check (Si permitido):** ICMP Echo. Si responde, icmp\_status="active".  
3. **Identificación de Puertos (Stealth):** Scan TCP SYN (Half-open) a puertos clave (80, 443, 22, 161, 502 Modbus).  
4. **Consulta SNMP en Cascada:**  
   * Si puerto 161 abierto:  
     1. Intentar SNMPv3 (AuthPriv).  
     2. Intentar SNMPv3 (NoAuth).  
     3. Intentar SNMPv2c (Comunidad Read-Only).  
     4. Intentar SNMPv1.  
   * Si éxito: Promocionar nodo de shadow\_host a managed\_host. Actualizar os, serial, etc.  
   * Si fallo: Mantener como shadow\_host, marcar mgmt\_defect="auth\_failed".

#### **Fase 5: Construcción de la Capa Lógica (L3 y Overlay)**

1. **Enlaces Lógicos Directos:**  
   * Basado en subredes comunes (/30, /29) entre routers.  
2. **Túneles y Overlays:**  
   * Detección de interfaces TunnelX, VlanX (L3).  
   * Mapeo de vecindades OSPF/BGP.  
3. **Inferencia de Firewalls Transparentes:**  
   * Si la topología física (FDB) dice que A y B están en el mismo switch/VLAN, pero el Traceroute muestra saltos o bloqueos selectivos \-\> Inferir logical\_barrier o microsegmentation.

### ---

**3\. Algoritmo de Triangulación FDB (Pseudocódigo para implementación)**

Este es el núcleo lógico que debes programar para ubicar nodos mudos.

Python

def triangulate\_physical\_location(target\_mac, all\_switches\_fdb, uplink\_ports):  
    """  
    Determina el puerto de acceso real de una MAC.  
    uplink\_ports: set de tuplas (switch\_id, port\_id) identificados vía LLDP.  
    """  
    candidate\_locations \= \[\]

    for switch in all\_switches\_fdb:  
        if target\_mac in switch.fdb\_table:  
            port \= switch.fdb\_table\[target\_mac\].port  
              
            \# CRÍTICO: Ignorar si la MAC se ve en un puerto de Uplink (core)  
            if (switch.id, port) in uplink\_ports:  
                continue  
              
            candidate\_locations.append({  
                "switch\_id": switch.id,  
                "port\_id": port,  
                "vlan": switch.fdb\_table\[target\_mac\].vlan  
            })

    \# Lógica de decisión  
    if len(candidate\_locations) \== 0:  
        return "UNKNOWN\_LOCATION" (Posiblemente Wireless o tras router NAT)  
      
    elif len(candidate\_locations) \== 1:  
        return "EDGE\_PORT\_FOUND", candidate\_locations\[0\]  
      
    else:  
        \# La MAC se ve en múltiples puertos de acceso.   
        \# Causa probable: Loop L2, VM migrando, o spoofing.  
        return "AMBIGUOUS\_LOCATION", candidate\_locations

### ---

**4\. Modelo de Datos JSON (Schema Integrado y Final)**

Actualizado para soportar los conceptos de inferencia y nodos sombra.

JSON

{  
  "$schema": "https://json-schema.org/draft/2020-12/schema",  
  "definitions": {  
    "confidence\_level": { "type": "number", "minimum": 0, "maximum": 1 }  
  },  
  "properties": {  
    "nodes": {  
      "items": {  
        "properties": {  
          "id": { "type": "string" },  
          "type": {   
            "type": "string",   
            "enum": \["switch", "router", "firewall", "managed\_host", "shadow\_host", "shadow\_device", "unmanaged\_switch"\]   
          },  
          "discovery\_method": {  
            "type": "string",  
            "enum": \["verified\_management", "arp\_inference", "fdb\_snoop", "passive\_traffic"\]  
          },  
          "mgmt\_state": {  
            "type": "string",  
            "enum": \["reachable", "unreachable", "auth\_failed", "policied\_silence"\]  
          },  
          "snmp\_details": {  
            "type": "object",  
            "properties": {  
               "version\_working": { "enum": \["v1", "v2c", "v3"\] },  
               "is\_mixed\_mode": { "type": "boolean", "description": "Si responde a v2c pero requiere v3 para writes" }  
            }  
          }  
        }  
      }  
    },  
    "links": {  
      "items": {  
        "properties": {  
          "source": { "type": "string" },  
          "target": { "type": "string" },  
          "layer": { "enum": \["physical", "logical", "overlay"\] },  
          "method": {  
            "enum": \["lldp\_verified", "fdb\_triangulation", "subnet\_inference", "manual\_override"\]  
          },  
          "is\_uplink": { "type": "boolean", "description": "Si conecta dos dispositivos de infraestructura" },  
          "confidence": { "$ref": "\#/definitions/confidence\_level" }  
        }  
      }  
    }  
  }  
}

### **Resumen de la Corrección Intelectual**

1. **Verificación de Suposiciones:** Hemos eliminado la suposición de que "si está en la red, responderá". Ahora asumimos que **no responderá**.  
2. **Corrección Lógica:** Hemos separado el **Descubrimiento de Existencia** (Pasivo/Infraestructura) del **Enriquecimiento de Detalles** (Activo/Directo). Esto permite construir el grafo físico incluso si fallan todas las credenciales o firewalls bloquean el acceso directo a los hosts.  
3. **Contraargumento Atendido:** "¿Qué pasa si hay mezcla de SNMP?" \-\> Se gestiona en la Fase 1 (para infraestructura) y Fase 4 (para hosts), con lógica de fallback en cascada, pero nunca detiene la creación del nodo en el grafo.