Lógica de construcción del **Grafo de Topología Real (GTR)**. 

### ---

**1\. Definición del Grafo Multi-Capa**

El sistema no genera un solo mapa plano, sino un grafo con atributos dimensionales.

* **Nodos (Vértices):** Dispositivos (Switches, Routers, Hosts, Nodos Sombra, Inferencias).  
* **Aristas (Enlaces):**  
  * L1\_Physical: Cable real (Puerto A \<-\> Puerto B).  
  * L2\_Association: Pertenencia a VLAN/Broadcast Domain.  
  * L3\_Direct: Adyacencia IP (mismo segmento de red).  
  * L3\_Overlay: Túneles (GRE, IPSec, VXLAN).

### ---

**2\. Motor 1: Construcción del Backbone (La Verdad Verificada)**

Este paso establece el esqueleto de la red usando los dispositivos que controlamos (credenciales válidas).  
**Lógica de Algoritmo:**

1. **Ingesta de Vecindad (LLDP/CDP):**  
   * Para cada Switch S\_local:  
     * Leer tabla de vecinos.  
     * Si Vecino S\_remote es conocido (está en inventario):  
       * **ACCIÓN:** Crear enlace L1\_Physical entre S\_local:Port\_X y S\_remote:Port\_Y.  
       * **ETIQUETADO CRÍTICO:** Marcar S\_local:Port\_X y S\_remote:Port\_Y con el atributo role="UPLINK".  
       * *Razón:* Un puerto Uplink conecta infraestructura. No puede tener usuarios finales conectados directamente (salvo errores de diseño).  
2. **Detección de Agregación (LACP/Port-Channel):**  
   * Si existen múltiples enlaces L1\_Physical paralelos entre los mismos dos nodos:  
     * Consultar configuración LACP/EtherChannel.  
     * **ACCIÓN:** Agruparlos en una entidad lógica Link\_Group.  
     * En el grafo visual: Mostrar 1 línea gruesa. En el grafo de datos: Mantener los N cables físicos individuales (importante para redundancia).  
3. **Detección de Stacking (Pilas de Switches):**  
   * Si el inventario reporta múltiples números de serie para una sola IP de gestión.  
   * **ACCIÓN:** Modelar como un Cluster\_Node que contiene múltiples Physical\_Chassis. Los puertos se nombran Unit:Slot:Port.

### ---

**3\. Motor 2: Construcción del Edge (La Inferencia Física)**

Aquí es donde resolvemos la ubicación de los dispositivos "mudos" (PC, Impresoras, PLCs, Cámaras) usando la **Triangulación FDB**.  
**Lógica de Algoritmo:**

1. **Limpieza de Ruido:**  
   * Ignorar MACs de Multicast/Broadcast.  
   * Ignorar MACs propias de las interfaces de los switches (CPU, VLAN interfaces).  
2. **Mapeo de Candidatos (Triangulación Negativa):**  
   * Tomar una MAC M\_target (descubierta vía ARP pasivo o FDB).  
   * Buscar M\_target en **todas** las tablas FDB de todos los switches.  
   * **FILTRO:** Si la MAC aparece en un puerto marcado como role="UPLINK" (fase anterior), **DESCARTAR esa entrada**.  
     * *Explicación:* Si el Switch A ve la MAC en su puerto Uplink hacia el Switch B, significa que el dispositivo *no* está en el Switch A, sino "detrás" del Switch B.  
3. **Resolución de Posición Final:**  
   * **Caso A: Unicidad (El caso ideal)**  
     * Si después del filtro, la MAC solo aparece en Switch\_Z:Port\_10.  
     * **ACCIÓN:** Crear enlace L1\_Physical entre Switch\_Z:Port\_10 y Shadow\_Node(M\_target).  
     * *Confianza:* Alta (0.95).  
   * **Caso B: Multiplicidad en Puerto (Hubs/Unmanaged/Wifi)**  
     * Si en Switch\_Z:Port\_20 aparecen múltiples MACs de dispositivos finales (ej. \> 3\) y NO es un Uplink conocido.  
     * **INFERENCIA:** Hay un dispositivo de capa 2 no gestionado intermedio.  
     * **ACCIÓN:**  
       1. Crear nodo type="inferred\_switch" (o AP si las MACs OUI son de móviles).  
       2. Conectar Switch\_Z:Port\_20 \<--\> Inferred\_Switch.  
       3. Conectar Inferred\_Switch \<--\> Todos los Shadow\_Nodes detectados ahí.  
   * **Caso C: Multiplicidad en Switches (Loop o VM Móvil)**  
     * Si la MAC aparece como "local" (no uplink) en Switch A y Switch B simultáneamente.  
     * **ANÁLISIS TEMPORAL:** Chequear last\_seen. Si es simultáneo \-\> **ALERTA**: Posible Loop de L2 o MAC Spoofing.  
     * Si hay diferencia de tiempo \-\> Es una VM que migró (vMotion) o un portátil que se movió. Tomar el last\_seen más reciente.

### ---

**4\. Motor 3: Construcción de la Capa Lógica (L3 \- No Transitiva)**

Este motor dibuja cómo fluye el tráfico IP, independiente de los cables.  
**Lógica de Algoritmo:**

1. **Enlaces Directos (Subnet Peer):**  
   * Si Node\_A tiene IP 192.168.10.1/24 y Node\_B tiene IP 192.168.10.2/24.  
   * Y ambos son detectados en el mismo dominio de broadcast (VLAN).  
   * **ACCIÓN:** Crear enlace L3\_Direct.  
   * *Nota:* Esto confirma que pueden hablarse sin pasar por un Gateway.  
2. **Enlaces de Routing (Next Hop):**  
   * Analizar tabla de rutas de Router\_A.  
   * Entrada: 0.0.0.0/0 via 10.1.1.1.  
   * Buscar quién tiene la IP 10.1.1.1 (ej. Firewall\_Core).  
   * **ACCIÓN:** Crear enlace L3\_Route dirigido Router\_A \-\> Firewall\_Core.  
3. **Detección de Seguridad (Firewalling Invisible):**  
   * Si existe enlace L1\_Physical o L2\_Association entre A y B.  
   * PERO el traceroute de A a B falla o muestra saltos extraños (estrellas \* \* \*).  
   * **ACCIÓN:** Insertar atributo en el enlace lógico: traffic\_inspection="suspected". Esto indica que aunque hay cable, hay una política de seguridad o un firewall transparente en medio.

### ---

**5\. Resumen del Grafo Resultante (Ejemplo de Estructura de Datos)**

Para que esto sea programable, el objeto "Enlace" final debe tener esta riqueza de metadatos:

JSON

{  
  "link\_id": "LNK-5521",  
  "source": { "node": "SW-ACCESO-01", "port": "Gi1/0/5", "type": "managed\_switch" },  
  "target": { "node": "HOST-X-SHADOW", "mac": "aa:bb:cc:...", "type": "shadow\_host" },  
    
  "layers": {  
    "physical": {  
      "status": "verified",  
      "method": "fdb\_triangulation\_single\_port",  
      "cable\_type": "copper"  
    },  
    "logical": {  
      "status": "inferred",  
      "subnet": "10.20.30.0/24",  
      "vlan\_id": 30  
    }  
  },  
    
  "security\_context": {  
    "is\_authorized": false,  // MAC no estaba en whitelist  
    "port\_security\_status": "locked", // El puerto se bloqueó tras la detección  
    "is\_silent": true // El target no responde ping  
  }  
}

### **6\. Validación Humana (El paso final)**

El sistema presenta el grafo con códigos de color:

* **Verde Sólido:** Enlace físico verificado (LLDP).  
* **Verde Punteado:** Enlace físico inferido (FDB Triangulation, alta confianza).  
* **Amarillo:** Inferencia de Switch no gestionado (Hub detectado).  
* **Rojo:** Conflicto (La misma MAC en dos sitios físicos distintos al mismo tiempo).

Esta lógica permite construir la topología real incluso si el 90% de la red son dispositivos industriales, médicos o IoT que no tienen capacidad de hablar SNMP ni responder Ping, basándose puramente en la "evidencia forense" que dejan en los switches.