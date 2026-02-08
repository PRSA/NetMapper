# MUDFR Backend Logic Implementation - Walkthrough

This document outlines the implementation of the **Unified Network Forensic Discovery Methodology (MUDFR)** backend logic.

## Changes Overview

We have implemented the core logic to infer "Shadow Reality" (devices that exist but don't respond to standard management) and build a physical topology where Layer 2 truth prevails.

### 1. Data Model Enhancements
**File:** `src/main/java/prsa/egosoft/netmapper/model/NetworkDevice.java`
- Added comprehensive Enums: `DeviceType` (including `SHADOW_HOST`, `SHADOW_DEVICE`), `DiscoveryMethod`, and `ManagementState`.
- Added `confidence` score and `lastSeenTimestamp` to support dynamic trust modeling.

**File:** `src/main/java/prsa/egosoft/netmapper/model/LinkConfidence.java`
- Created a new model to track link confidence with temporal decay (Half-Life).

### 2. Topology Inference Engine
**File:** `src/main/java/prsa/egosoft/netmapper/core/TopologyInferenceEngine.java`
This is the core brain of the new system. It implements:

#### Phase 2: Shadow Node Generation
- **ARP Fusion:** Converts IP+MAC entries from Router ARP tables into `SHADOW_HOST` nodes.
- **L2 Fusion:** Converts MAC-only entries from Switch FDB tables into `SHADOW_DEVICE` nodes (for devices without IP or prolonged silence).

#### Phase 3: Physical Triangulation
- **Algorithm:** `triangulatePhysicalLocation(targetMac)`
- **Uplink Filtering:** critical feature to ignore MACs seen on Core Uplinks, preventing false triangulation to core switches.
- **Unmanaged Switch Detection:** Implemented heuristic where **>1 MACs on a non-uplink Access Port** implies an unmanaged switch.
    - **Result:** `SHARED_SEGMENT_FOUND`

#### Phase 11: Dynamic Confidence
- **Logic:** `confidence = base_score * decay_factor`.
- **Decay:** Implemented 24-hour half-life for confidence degradation.

### 3. Topology Validation
**File:** `src/main/java/prsa/egosoft/netmapper/core/TopologyValidator.java`
- Implemented checks for Physical Layer consistency:
    - **Duplex Mismatch:** Warnings for Full vs Half duplex.
    - **MTU Mismatch:** Errors for MTU differences (e.g. 1500 vs 9000).
    - **Speed Mismatch:** Warnings for speed differences.

## Verification Results

### Unit Tests
We created `src/test/java/prsa/egosoft/netmapper/core/TopologyInferenceEngineTest.java` to verify the logic.

**Test Summary:**
- `testInferShadowNodesFromArp`: **PASSED** (Correctly creates SHADOW_HOST from Router ARP)
- `testL2Fusion`: **PASSED** (Correctly creates SHADOW_DEVICE from Switch FDB)
- `testTriangulatePhysicalLocation_EDGE_PORT_FOUND`: **PASSED** (Correctly identifies unique access port)
- `testTriangulatePhysicalLocation_IgnoreUplink`: **PASSED** (Correctly ignores uplinks)
- `testUnmanagedSwitchDetection`: **PASSED** (Correctly enables Shared Segment logic when multiple MACs are present)

**Execution Log:**
```
[INFO] Running prsa.egosoft.netmapper.core.TopologyInferenceEngineTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## UI Enhancements
- **Device Tree**: Updated to display MUDFR-specific fields in the "System Information" node:
    - **Discovery Method** (e.g., VERIFIED_MANAGEMENT, ARP_INFERENCE)
    - **Management State** (e.g., REACHABLE, UNREACHABLE)
    - **Confidence** (percentage)
    - **Last Seen** (timestamp)
- **Localization**: Added support for these new fields in English and Spanish.

### 4. Application Integration
**File:** `src/main/java/prsa/egosoft/netmapper/service/NetworkController.java`
- The `TopologyInferenceEngine` is now a core component of the `NetworkController`.
- **Automatic Inference**: The system automatically triggers the MUDFR inference pass (`processInference()`) in the following scenarios:
    - After successfully loading a network map from a JSON file.
    - Upon completion of an asynchronous network scan.
    - After a blocking (synchronous) network scan finishes.
- This ensures that "Shadow Reality" is always up-to-date with the latest discovered infrastructure data.
