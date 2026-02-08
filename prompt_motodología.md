### Tu Prompt Optimizado para el Agente Antigravity

**Rol:** Actúa como un **Arquitecto de Software Senior y Especialista en Teoría de Grafos de Red**. Tu objetivo es diseñar e implementar la lógica de backend para un sistema de descubrimiento forense basado en la **Metodología Unificada de Descubrimiento Forense de Redes (MUDFR)** (leer "Metodología DFR.md"), partiendo de la base de que ya existe un sistema de recolección de datos y debe ser adaptado a esta metodología.

**Contexto del Sistema:**
La MUDFR se basa en el axioma de la **Existencia por Inferencia (Shadow Reality)**: un dispositivo existe si la infraestructura lo ve, aunque no responda a ping o SNMP. El sistema debe construir un grafo dinámico  donde la "Verdad Física" (Capa 2) prevalece sobre la "Verdad Declarada" (Capa 7).

**Instrucciones de Implementación (Fases Críticas):**

1. **Motor de Ingesta y Nodos Sombra (Fases 1 y 2):**
* Diseña estructuras en [Java/Rust] para representar nodos `shadow_host` e `inferred`.
* Implementa la lógica de **Fusión ARP (L3 -> L2)**: si una IP aparece en el router pero no en el inventario, genera automáticamente un nodo sombra.


2. **Algoritmo de Triangulación FDB (Fase 3):**
* **Core Logic:** Debes programar una función que recorra las tablas de forwarding (FDB) de todos los switches.
* **Restricción:** Ignorar cualquier coincidencia de MAC en puertos marcados como `uplink` (identificados vía LLDP/CDP en Fase 1).
* **Salida:** Si la MAC es única en un puerto de acceso, crea un `PhysicalLink` con `confidence: 0.95`. Si hay múltiples MACs, infiere un `unmanaged_switch`.


3. **Modelo de Confianza Dinámica (Fase 11):**
* Implementa el cálculo: `confidence = source_weight × temporal_decay × consistency`.
* Aplica un `half-life` de 24 horas para la degradación temporal de los enlaces no vistos recientemente.


4. **Capa Lógica y Validación (Fases 6 y 8):**
* Implementa validadores que detecten `mismatch` de velocidad, dúplex o MTU entre extremos de un enlace.



**Requerimientos de Salida del Código:**

* **Modularidad:** Separa los recolectores (SNMP/SSH/ARP) de los procesadores de inferencia (Triangulador/Calculador de Confianza).
* **Tipado:** Define un esquema JSON estricto para los nodos que incluya `discovery_sources[]`, `mgmt_state` y `layer`.
* **Performance:** El algoritmo de triangulación debe ser capaz de procesar grafos de miles de nodos eficientemente.

**Tarea Inmediata:**
Analiza los documentos de la metodología proporcionados y genera el **Esquema de Clases/Estructuras** y el **Pseudocódigo Detallado** para los módulos de Triangulación L2 y el Motor de Inferencia de Confianza.

---

### Qué ha cambiado (Análisis de Lyra)

* **Enfoque de Programación:** He sustituido la descripción narrativa por una jerarquía de "Instrucciones de Implementación". Esto le indica al agente que no debe solo resumir, sino generar lógica programable (clases, funciones, validadores).
* **Fusión de Documentos:** El prompt ahora integra el algoritmo específico de la "Fase 3" del primer documento con el modelo matemático de confianza de la "Fase 11" del segundo, evitando contradicciones.
* **Priorización de Axiomas:** He reforzado el concepto de "Uplink" como filtro crítico para la triangulación, que es el error más común al programar estos sistemas.
* **Criterios de Calidad de Código:** He añadido requisitos sobre modularidad y manejo de estados (Shadow vs Managed), fundamentales para el desarrollo en Java o Rust.

**Pro Tip para Antigravity:** Divide la implementación en tres microservicios/módulos: Discovery (Ingesta), Inference (Triangulación y Grafo) y Health (Validación física y Confianza)"*. Esto forzará una arquitectura mucho más limpia.
