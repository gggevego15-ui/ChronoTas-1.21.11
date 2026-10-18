# ChronoTAS

Client-side TAS practice tools for Minecraft Java 1.21.11 + Fabric.

## Controls
- **Right Shift** — open the in-game menu
- **F6** — start/stop recording; saves a JSON recording
- **F7** — play/stop the latest saved recording
- **F8** — pause/resume client ticks
- **F9** — advance one client tick while paused

Recordings are stored in `config/chronotas/recordings/`. GitHub Actions builds a JAR on each push and manual dispatch; download it from the run's Artifacts section.

## Current limitations
This is an early input-recorder prototype, not a full deterministic TAS engine. It records W/A/S/D, jump, sprint, sneak and player yaw/pitch per client tick. It does not record mouse deltas, inventory/hotbar actions, attacks, interactions, audio or gameplay video. Playback assumes the same world and starting point. It does not restore a complete world state or guarantee identical entity randomness.

**This does not replace OBS or encode video.** It records TAS input data as JSON. Built-in video capture is a separate feature.

Test in a disposable single-player world first. Pausing the client tick can affect asynchronous systems differently from normal gameplay.

Requires Java 21, Fabric Loader and Fabric API for Minecraft 1.21.11.
