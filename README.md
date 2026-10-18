# ChronoTAS

Client-side TAS practice tools for Minecraft Java 1.21.11 + Fabric.

## Controls
- **Right Shift** — open the in-game menu
- **F4** — start/stop built-in MP4 gameplay video capture (no OBS); videos are saved to `config/chronotas/videos/`
- **F6** — start/stop input recording; saves JSON with an initial world snapshot and observed block changes
- **F7** — restore the recording's initial snapshot and play/stop the latest recording
- **F8** — pause/resume client ticks
- **F9** — advance one client tick while paused
- **F10** — save a quick state to `config/chronotas/states/quick-state.json`
- **F11** — restore that quick state
- The menu has replay-speed controls: 1x, 0.5x, 0.25x and 0.1x.
- A HUD overlay shows the replay timer and live WASD/jump/sneak/sprint/attack/use key states.

## Video capture
ChronoTAS reads the rendered framebuffer and encodes an MP4 at 30 FPS using bundled pure-Java JCodec. No external recorder is needed. Capturing and encoding are separate: frames are queued to a background encoder; a very full queue may drop frames if the computer cannot encode quickly enough. Video files are stored under `config/chronotas/videos/`.

## Snapshot and replay behavior

The initial snapshot records player position/rotation, blocks in an 8-block radius, and nearby non-player entity UUID/type, positions, rotations, velocities, and living-entity health. During playback, if an entity from a recorded frame no longer exists, ChronoTAS attempts to recreate it with the same UUID and restore its basic position, velocity, rotation and health. This lets many killed/despawned mobs reappear, but it does not restore all mob-specific NBT such as sheep color, inventories, villager trades, effects, equipment, breeding state, or exact AI state. Entity AI, random ticks, scheduled block ticks, container contents, projectiles, redstone timing, chunks outside the captured area, mouse deltas and other simulation state are not fully snapshotted. Online servers can overwrite client-side restoration.

Use this in a disposable single-player world and make a normal world backup first. ChronoTAS is designed for repeatable TAS practice, not perfect deterministic replay in every world.

Requires Java 21, Fabric Loader and Fabric API for Minecraft 1.21.11.
