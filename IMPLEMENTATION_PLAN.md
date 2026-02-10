# Implementation Plan - MUDFR Backend Logic

## Goal Description
Implement the backend logic for the "Unified Network Forensic Discovery Methodology" (MUDFR). This involves creating a system that infers the existence of devices ("Shadow Reality") based on infrastructure data (ARP/FDB tables) and builds a physical topology where L2 truth prevails over L7 declarations.

## User Review Required
> [!IMPORTANT]
> **Schema Changes**: Introduction of strictly typed JSON schema for nodes, including `shadow_host`, `inferred` state, and `confidence` levels.
> **Algorithm**: Implementation of the specific `triangulate_physical_location` algorithm which may alter current topology results.
> **Architecture**: Refactoring into distinct modules: Discovery (Ingestion), Inference (Triangulation), and Health (Validation).

## Proposed Changes

### Data Model & Schema (Class/Structure Schema)

#### [MODIFY] [NetworkDevice.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/model/NetworkDevice.java)
We will enhance `NetworkDevice` to support the following JSON schema properties:

```json
{
  "properties": {
    "type": { "enum": ["switch", "router", "firewall", "managed_host", "shadow_host", "shadow_device", "unmanaged_switch"] },
    "discovery_method": { "enum": ["verified_management", "arp_inference", "fdb_snoop", "passive_traffic"] },
    "mgmt_state": { "enum": ["reachable", "unreachable", "auth_failed", "policied_silence"] },
    "confidence": { "type": "number", "minimum": 0, "maximum": 1 }
  }
}
```

**Java Implementation:**
```java
public class NetworkDevice {
    // Existing fields...
    private DeviceType type; // Enum: SWITCH, ROUTER, SHADOW_HOST, ...
    private DiscoveryMethod discoveryMethod; // Enum: VERIFIED_MANAGEMENT, ARP_INFERENCE, ...
    private ManagementState mgmtState; // Enum: REACHABLE, UNREACHABLE, ...
    private float confidence;
    private long lastSeenTimestamp;
    
    // ... identification fields ...
}
```

#### [NEW] [LinkConfidence.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/model/LinkConfidence.java)
```java
public class LinkConfidence {
    private float score; // 0.0 - 1.0
    private long lastVerified;
    private DecayProfile decayProfile; // e.g., HALF_LIFE_24H
}
```

### Inference Engine (Pseudocode & Logic)

#### [NEW] [TopologyInferenceEngine.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/core/TopologyInferenceEngine.java)

**Triangulation Algorithm (L2 Physical Mapping):**
```java
/**
 * Determines the physical access port for a given MAC address.
 * @param targetMac The MAC address of the shadow node.
 * @param allSwitchesFdb Map of SwitchID -> FDB Table.
 * @param uplinkPorts Set of ports identified as uplinks (connecting infrastructure).
 */
public LocationResult triangulatePhysicalLocation(String targetMac, Map<String, FdbTable> allSwitchesFdb, Set<Port> uplinkPorts) {
    List<Location> candidateLocations = new ArrayList<>();

    for (NetworkDevice switchDevice : allSwitchesFdb.keySet()) {
        FdbTable fdb = allSwitchesFdb.get(switchDevice.getId());
        if (fdb.contains(targetMac)) {
            Port port = fdb.getPortFor(targetMac);
            
            // CRITICAL: Ignore if MAC is seen on an Uplink port
            if (uplinkPorts.contains(port)) {
                continue;
            }
            
            candidateLocations.add(new Location(switchDevice, port, fdb.getVlan(targetMac)));
        }
    }

    if (candidateLocations.isEmpty()) {
        return LocationResult.UNKNOWN; // Likely wireless or behind NAT
    } else if (candidateLocations.size() == 1) {
        return LocationResult.EDGE_PORT_FOUND(candidateLocations.get(0), 0.95f); // High confidence
    } else {
        // MAC seen on multiple access ports -> Ambiguous (Loop, VM migration, Spoofing)
        // Or properly handled by detailed analysis (e.g. unmanaged switch detection logic)
        return LocationResult.AMBIGUOUS(candidateLocations);
    }
}
```

**Confidence Inference Engine:**
```java
/**
 * Calculates dynamic confidence based on source consistency and time.
 */
public float calculateConfidence(DiscoverySource source, long lastSeenTime) {
    float baseConfidence = getBaseConfidence(source); // e.g., FDB=0.95, ARP=0.8, SNMP=1.0
    
    long timeDiff = System.currentTimeMillis() - lastSeenTime;
    float timeDecay = calculateDecay(timeDiff, HALF_LIFE_24H);
    
    return baseConfidence * timeDecay;
}

private float calculateDecay(long timeDiff, long halfLife) {
    return (float) Math.pow(0.5, (double) timeDiff / halfLife);
}
```

### Discovery & Ingestion (Phases 1 & 2)
#### [MODIFY] [NetworkScannerService.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/service/NetworkScannerService.java)
- **Ingestion**: Separate collection from processing.
- **Shadow Node Generation**:
    - Iterate through ARP tables of core routers.
    - If IP is not in `known_managed_devices`:
        - Create `NetworkDevice` with `type=SHADOW_HOST`, `discoveryMethod=ARP_INFERENCE`.
    - Iterate through FDB tables.
    - If MAC is not in ARP table or known devices:
        - Create `NetworkDevice` with `type=SHADOW_DEVICE` (L2 only), `discoveryMethod=FDB_SNOOP`.

### Logical Layer & Validation
#### [NEW] [TopologyValidator.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/core/TopologyValidator.java)
- **Consistency Checks**:
    - Warnings if a device is claimed by multiple discrete physical locations (impossible unless valid mobility).
    - Mismatch detection: Speed/Duplex settings on linked ports.

## Verification Plan

### Automated Tests
- **Unit Tests**:
    - `TopologyInferenceTest`: Mock FDB tables and verify `triangulate_physical_location` correctly places nodes (Single match -> Physical Link, Multiple match -> Unmanaged Switch).
    - `ConfidenceTest`: Verify decay calculation over time.
    - `ArpFusionTest`: Verify shadow node creation from ARP data.
- **Command**: `mvn test`

### Manual Verification
- **Visual Inspection**:
    - Run the application against the `network_map_CoCJ.json` dataset.
    - Verify that known "silent" devices appear as `shadow_host` with inferred links.
    - Check that core links are marked as `uplink` and not used for endpoint triangulation.

### UI Enhancements
#### [MODIFY] [DeviceTreePanel.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/gui/DeviceTreePanel.java)
- Update `createSystemInfoNode` to display:
    - Discovery Method
    - Management State
    - Confidence (formatted as %)
    - Last Seen (formatted date)

#### [MODIFY] [messages_xx.properties](file:///opt/workspace/NetMapper/src/main/resources/messages_en.properties)
- Add keys for new labels:
    - `info.discovery_method`
    - `info.mgmt_state`
    - `info.confidence`
    - `info.last_seen`

### Integration
#### [MODIFY] [NetworkController.java](file:///opt/workspace/NetMapper/src/main/java/prsa/egosoft/netmapper/service/NetworkController.java)
- Add `TopologyInferenceEngine` instance.
- Add `processInference()` method to trigger shadow node discovery.
- Update `scanNetworkAsync`, `scanNetworkBlocking`, and `loadDevicesFromJson` to call `processInference()`.
 
### Forensic Visibility Phase (Completed)
- **CLI Flags**: Implementation of `-v`, `--forensics`, `--min-confidence` in `Main.java`.
- **JSON Statistics**: Addition of `summary` object in `ExportService.java`.
- **GUI Selection**: Selection listener wiring between `NetworkMapPanel`, `DeviceTreePanel`, and `DetailsPanel`.
- **Visual Styles**: Implementation of dashed strokes and transparency in `NetworkMapPanel.java`.

### Robustness & UI Synchronization Phase (Completed)
- **NPE Prevention**: Defensive null checks in `NetworkGraph` and ID assignment for shadow devices.
- **UI Refresh**: Centralized `refreshUI()` method in `MainWindow` to synchronize discoveredDevices with the GUI.
- **i18n Cleanup**: Final externalization of all MUDFR-related technical labels.
