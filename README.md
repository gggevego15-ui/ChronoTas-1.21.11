# ChronoTAS

Client-side TAS practice tools for Minecraft Java 1.21.11 + Fabric.

## Controls
- **Right Shift** — open the in-game menu
- **F6** — start/stop input recording; saves a JSON recording with an initial world snapshot and observed block changes
- **F7** — restore the recording's initial snapshot and play/stop the latest recording
- **F8** — pause/resume client ticks
- **F9** — advance one client tick while paused
- **F10** — save a quick state to `config/chronotas/states/quick-state.json`
- **F11** — restore that quick state

Recordings are stored in `config/chronotas/recordings/`. GitHub Actions builds a JAR on each push and manual dispatch; download it from the run's Artifacts section.

## Snapshot and replay behavior

The initial snapshot records the player's position/rotation, blocks in an 8-block radius around the player, and nearby non-player entity positions, rotations, velocities, and living-entity health. While recording, a World mixin remembers the first pre-change state for each changed block; playback restores those blocks too, then replays the recorded inputs. This is intended for repeatable single-player TAS practice in the same dimension and world.

This is **not** a complete Minecraft save-state engine. Entities that died, despawned, were newly spawned, or changed identity cannot always be recreated by moving surviving entities back. Entity AI, random ticks, scheduled block ticks, inventories, container contents, effects, projectiles, redstone timing, chunks outside the captured area that were not changed during recording, mouse deltas, attacks/interactions, and other simulation state are not fully snapshotted. Online/multiplayer servers may overwrite client-side restoration. Always test in a disposable single-player world and make a normal world backup first.

ChronoTAS records TAS inputs as JSON; it does **not** record or encode gameplay video and does not replace OBS. Built-in video capture is not implemented yet.

Requires Java 21, Fabric Loader and Fabric API for Minecraft 1.21.11.
