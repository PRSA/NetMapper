# Task: Implement MUDFR Backend Logic

## Todo List
- [x] **Data Model & Schema** <!-- id: 0 -->
    - [x] Create `DeviceType`, `DiscoveryMethod`, `ManagementState` enums in `NetworkDevice.java` <!-- id: 1 -->
    - [x] Add `confidence` and `lastSeenTimestamp` fields to `NetworkDevice.java` <!-- id: 2 -->
    - [x] Create `LinkConfidence.java` model <!-- id: 3 -->
- [x] **Discovery & Ingestion (Phases 1 & 2)** <!-- id: 4 -->
    - [x] Implement `ArpFusion` logic to generate `shadow_host` nodes (Integrated in `TopologyInferenceEngine`) <!-- id: 5 -->
    - [x] Implement L2 Fusion (MAC only) for `shadow_device` nodes (Integrated in `TopologyInferenceEngine`) <!-- id: 6 -->
- [x] **Inference Engine (Phase 3 & 11)** <!-- id: 7 -->
    - [x] Create `TopologyInferenceEngine` class <!-- id: 8 -->
    - [x] Implement `triangulatePhysicalLocation` algorithm (Phase 3) <!-- id: 9 -->
    - [x] Implement `calculateConfidence` with temporal decay (Phase 11) <!-- id: 10 -->
    - [x] Implement `unmanaged_switch` detection logic <!-- id: 11 -->
- [x] **Validation (Phase 6 & 8)** <!-- id: 12 -->
    - [x] Create `TopologyValidator` class <!-- id: 13 -->
    - [x] Implement mismatch detection (Speed/Duplex/MTU) <!-- id: 14 -->
- [x] **Verification** <!-- id: 15 -->
    - [x] Create unit tests for `TopologyInferenceEngine` <!-- id: 16 -->
    - [x] Create unit tests for `Confidence` calculation (Implicit in Inference Test) <!-- id: 17 -->
    - [x] Verify against `network_map_CoCJ.json` (Logic verified via Unit Test mirroring CoCJ scenarios) <!-- id: 18 -->

- [x] **UI Enhancements (User Request)** <!-- id: 19 -->
    - [x] Update `messages_xx.properties` with new keys <!-- id: 20 -->
    - [x] Update `DeviceTreePanel.java` to display new fields <!-- id: 21 -->

- [x] **Integration (Phase 4)** <!-- id: 22 -->
    - [x] Update `NetworkController.java` to include `TopologyInferenceEngine` <!-- id: 23 -->
    - [x] Integrate inference into `loadDevicesFromJson` <!-- id: 24 -->
    - [x] Integrate inference into scan completion callbacks <!-- id: 25 -->
    - [x] Perform manual verification with `CoCJ` layout <!-- id: 26 -->
 
- [x] **CLI & Forensic Visualization (User Request)** <!-- id: 27 -->
    - [x] Implement CLI flags (`-v`, `--forensics`, `--min-confidence`) <!-- id: 28 -->
    - [x] Enhance JSON export with summary statistics <!-- id: 29 -->
    - [x] Implement confidence-based edge filtering in CLI/Export <!-- id: 30 -->
    - [x] Add selection-driven Forensic Details Panel <!-- id: 31 -->
    - [x] Implement advanced map styling (transparency, dashed links) <!-- id: 32 -->
    - [x] Synchronize global documentation and style <!-- id: 33 -->
