# Software Reference Universe for RDP-UAV-Addon

## 1. Purpose and scope

This document inventories the software ecosystem that is relevant to building a practical autonomous UAV/robotics framework around Minecraft 1.12.2, Forge, McHeliCE, and the existing RDP mission/control architecture.

The goal is not to copy an unrelated stack into Minecraft. The goal is to assemble a disciplined reference universe: a set of proven projects, algorithms, and architectural patterns that can inform the design of a controlled, server-authoritative UAV system with mission logic, target tracking, navigation, perception, and operator tooling.

This reference set is intentionally broader than UAV software alone. A project is included when it provides a useful architecture, algorithm, API boundary, or operational pattern that could inform RDP-UAV's design.

## 2. Classification rules

Every project below is classified into one of four buckets:

- A. Direct integration candidate
  - Usable directly or through a thin adapter.
- B. Architectural reference
  - Not a direct integration target, but structurally important.
- C. Algorithm reference
  - Provides a planning, estimation, tracking, or control concept.
- D. Research / future reference
  - Relevant for advanced future work, not immediate implementation.

## 3. Executive summary

For the short-term implementation path, the strongest references are:

- McHeliCE / WingMan / Minecraft vehicle control as the runtime integration boundary.
- PX4, ArduPilot, and MAVLink for mission/control patterns and authoritative state handling.
- ROS 2 + Nav2 + MoveIt for message-driven robotics architecture and lifecycle separation.
- OpenCV + PCL + RTAB-Map / Cartographer for perception, mapping, and localization.
- Gazebo Classic / AirSim / Webots for simulation and sensor-fusion testing.
- QGroundControl and MAVSDK for operator telemetry and control UI patterns.
- BehaviorTree.CPP, OMPL, and A*/RRT planner patterns for mission logic and route selection.

The strongest architectural boundary remains: server-authoritative mission state, client-only visualization, and a single control owner for each aircraft. That is the critical design constraint that the reference universe must support.

## 4. Reference catalog

### 4.1 Minecraft / McHeli / vehicle runtime systems

| Project | Category | Why it matters |
| --- | --- | --- |
| McHeli / McHeliCE / WingMan | A | The native UAV/vehicle runtime that RDP-UAV must integrate with. It defines entity physics, control flow, mission adapters, and the actual aircraft authority boundary inside Minecraft. |
| Minecraft vehicle / aircraft mods | A | Useful as a practical reference for client/server authority, entity control, networking, GUI overlays, and mission-state synchronization. |
| Forge mod ecosystem | A | Relevant where compatibility, registration, pack layering, and server/client authority patterns are needed for a 1.12.2 runtime. |
| Minecraft pathfinding and AI vehicle mods | C | Useful for route planning, task scheduling, and entity-level behaviors in constrained simulation worlds. |

### 4.2 Autopilots and flight stacks

| Project | Category | Why it matters |
| --- | --- | --- |
| PX4 Autopilot | B | Mature UAV flight stack with a clear missions/modes/control architecture. Strong reference for mission states, flight modes, estimator structures, and safety boundaries. |
| ArduPilot | B | Broad multi-vehicle autopilot architecture. Valuable for mission execution, control loops, vehicle-mode transitions, and fault handling. |
| Paparazzi UAV | B | Reference design for UAV control software and flight-mode sequencing, especially for mission phases and guidance logic. |
| Cleanflight / Betaflight / iNav | C | Relevant for real-time attitude control and tuning patterns; useful for flight-control loops and stabilization concepts, though not a full mission stack. |
| MAVLink | A | Lightweight protocol and telemetry contract; useful for commands, state reporting, and mission sequencing patterns. |
| MAVSDK | A | Modern client SDK for mission and vehicle control; useful as a reference for command abstraction and telemetry handling. |
| QGroundControl | B | Excellent operator UI reference for telemetry, mode selection, map overlays, mission editing, and vehicle status. |
| Mission Planner | B | Practical GCS reference for route workflows, mode changes, and operator-visible mission health. |

### 4.3 Robotics middleware and command architecture

| Project | Category | Why it matters |
| --- | --- | --- |
| ROS 2 | B | The dominant robotics middleware reference for modular services, topics, messages, lifecycle, and node design. Strong influence for clean subsystem boundaries. |
| Nav2 | B | Mission execution, localization, planning, and recovery architecture for autonomous robots. Useful for route planning and safety policy design. |
| MoveIt | B | Strong reference for planning and execution separation, motion constraints, and task orchestration. |
| BehaviorTree.CPP | A | Useful for decision logic and mission task trees, especially when mission phases need deterministic branching and fallback logic. |
| OMPL | C | Reference for path planning and motion planning algorithms such as RRT and sampling-based planners. |
| OpenRAVE / planning stacks | C | Useful for task-space planning and motion constraints when mission planning grows beyond simple waypoint logic. |

### 4.4 Perception, tracking, localization, and mapping

| Project | Category | Why it matters |
| --- | --- | --- |
| OpenCV | A | Primary CV library for feature tracking, filtering, detection, and image processing patterns. Essential for sensor-derived perception. |
| PCL (Point Cloud Library) | A | Reference for 3D perception, point cloud processing, clustering, and geometric filtering. |
| RTAB-Map | B | Good reference for SLAM and map maintenance in real-world environments. |
| Cartographer | B | Strong mapping and localization reference, especially for multi-sensor fusion and pose estimation. |
| ORB-SLAM / ORB-SLAM2 / ORB-SLAM3 | C | Important for visual SLAM and tracking references; useful for target tracking and localization concepts. |
| Open3D | C | Reference for 3D geometry processing, reconstruction, and scene understanding. |
| Darknet / YOLO / ultralytics | C | Useful for object detection pipelines and deployment-oriented perception stacks. |
| Apriltag / fiducial tracking | C | Useful for precise marker detection, localization, and operator calibration references. |

### 4.5 Simulation, sensor fusion, and world modeling

| Project | Category | Why it matters |
| --- | --- | --- |
| Gazebo Classic | B | Comprehensive robotics simulator with dynamics, sensors, and multi-robot scenarios. Strong world-model and testing reference. |
| AirSim | B | Simulation stack for drone autonomy and perception testing, especially for camera and flight simulation. |
| Webots | B | Strong robotics simulator and control environment for sensor-driven autonomous behaviors. |
| FlightGear | B | Useful for broader aircraft/flight physics and operator-visualization references. |
| Unreal Engine / custom simulation stacks | D | Helpful only for advanced synthetic-data generation or high-fidelity visual simulation pipelines. |
| TensorFlow / PyTorch | D | Useful for learned perception and planning, but too broad and too high-level for immediate use in the mod runtime. |

### 4.6 Search, guidance, planning, and state estimation

| Project | Category | Why it matters |
| --- | --- | --- |
| A* / Theta* / D* families | C | Core graph-search and route-planning references for patrol routes and target travel paths. |
| RRT / RRT* | C | Useful for non-holonomic path planning and navigation in constrained 3D/2.5D spaces. |
| Kalman filters / EKF / UKF | C | Core state-estimation references for vehicle state, target state, and fuse-based tracking. |
| particle filters | C | Useful for target localization, tracking under uncertainty, and multi-hypothesis filtering. |
| PID / cascaded control loops | C | Fundamental control references for attitude, throttle, and stabilization loops. |
| MPC and trajectory optimization | D | Relevant for advanced guidance but beyond the immediate scope of a Minecraft UAV mod. |

### 4.7 UI, telemetry, and mission orchestration

| Project | Category | Why it matters |
| --- | --- | --- |
| QGroundControl | A | Best reference for a practical operator console: mission editing, telemetry panels, warnings, and vehicle state presentation. |
| Mission Planner | A | Similar operator workflow patterns for mission creation, status, and control-state display. |
| Grafana / Prometheus / Loki | A | Useful to think about observability, metrics, logs, health checks, and mission state telemetry. |
| OpenTelemetry | A | Good boundary for instrumentation, tracing, and runtime observability as the project grows. |
| Electron / web-dashboard references | A | Useful for richer overlays and operator tools, although the current project remains native Minecraft GUI-centric. |

### 4.8 Future research and advanced autonomy

| Project | Category | Why it matters |
| --- | --- | --- |
| Reinforcement learning stacks | D | Relevant for learned control and policy generation, but not an immediate foundation. |
| Neural SLAM / world models | D | High-value future direction for perception and long-horizon mission reasoning. |
| Multi-agent coordination frameworks | D | Useful for future swarm behavior beyond the current mission and assignment architecture. |
| Synthetic-data pipelines | D | Useful for training detection models or simulation-based benchmarking later on. |

## 5. Recommended shortlist for the next implementation phase

The most credible near-term reference set for RDP-UAV is:

1. McHeliCE / WingMan / Minecraft control layer
2. PX4 + ArduPilot for authoritative mission/control patterns
3. ROS 2 + Nav2 for subsystem boundaries and lifecycle design
4. OpenCV + PCL for perception/target extraction
5. Gazebo Classic / AirSim for testing and simulation
6. QGroundControl for mission UI and telemetry patterns
7. BehaviorTree.CPP for mission logic and fallback states
8. MAVLink / MAVSDK for command and telemetry abstractions

This set covers the full reference arc from low-level vehicle control, through mission execution, to perception and operator tooling without forcing a brittle or over-engineered architecture.

## 6. Design implications for RDP-UAV

The reference universe supports a few concrete design principles for this addon:

- Mission state must be server-authoritative.
- The client GUI must be visualization-only, not an authority source.
- A single control owner must exist for each aircraft at any point in time.
- Mission lifecycle should be explicit: validation -> acceptance -> bind -> acquire control -> execute -> complete/fail.
- Perception should be treated as a sensor pipeline, not a single raycast or a single camera trick.
- Navigation and target tracking should be broken into distinct stages: recognition, tracking, prediction, and reacquisition.
- Safety and recovery logic must be explicit and observable, not hidden inside the user interface.

## 7. Bottom line

The most valuable references are not “big AI frameworks” in the abstract. They are toolchains that force clear separation between:

- control authority,
- mission logic,
- planning,
- perception,
- localization,
- telemetry,
- and operator-facing UI.

That separation is exactly what a credible RDP-UAV implementation needs, and it is the right lens for evaluating any hardware, software, or simulator reference that is brought into the project.
