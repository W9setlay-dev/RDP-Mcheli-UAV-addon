# Changelog

## 2026-10-14

### Features
- Added an optional cruise-altitude setting for long-range missions; the selected altitude is retained per mission and swarm member, while terrain checks can still require a safer, higher route.
- Added local UAV-feed perception modes for normal viewing, visual-assist overlays, and pipeline diagnostics, with an F7 mode toggle and clearer capture-to-display status.

### Bug fixes
- Fixed a ModularUI version mismatch that could break UAV-feed widget integration.
- Improved mission and flight reliability by preserving aircraft control during mission pauses, clearing stale flight data on release, and handling invalid motion samples safely.
- Tightened client vision and mission validation, including removal of an incorrect restriction that rejected otherwise valid swarm missions.

### Chores
- Updated development runtime setup and expanded automated coverage and documentation for mission routing, vision lifecycle, and validation boundaries.

### Breaking changes
- No user-facing breaking changes.

## 2026-09-24

### Features
- UAV control ownership is now enforced at the server boundary, so only the authorized mission controller can steer an aircraft.
- Mission flow was streamlined around clearer APIs to make target handling and sequencing more reliable for operators.
- Terminal observation orbits were disabled to keep guided runs focused on the intended target instead of circling unpredictably.

### Bug fixes
- Fixed control-authority issues that could allow conflicting flight inputs during active missions.
- Reduced terminal tracking problems by preventing mission observation loops from keeping aircraft in a non-productive orbit.

### Chores
- Continued routine project maintenance and incremental mission updates during the current development pass.
- Kept the workspace aligned with the ongoing mission rework and validation work across the UAV stack.

### Breaking changes
- No breaking changes were introduced in the recent commit set.
