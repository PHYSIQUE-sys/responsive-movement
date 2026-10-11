# Responsive Movement

A RuneLite plugin by **PHYSIQUE-sys** that smooths the **local player's displayed
movement** and reacts to observed scene and minimap clicks. Walking, running,
turning, and interaction approaches use native player animations at the displayed
position.

The game still decides your actual location, route, speed, attacks, and actions.
The plugin observes normal input; it does not send actions or move your character
on the server. An early visual preview can be corrected when server movement
differs. **Movement speed** changes presentation pacing, not gameplay speed.

## Features

- Responsive starts and direction changes for scene Walk, right-click Walk here,
  minimap clicks, and supported object, item, and NPC interactions.
- Collision-checked, time- and distance-bounded previews that reconcile to actual
  movement.
- Adjustable gentle catch-up behind the true tile and slowdown ahead of it.
- Optional true-tile highlight with configurable colours, border and feathering,
  plus native between-tick movement-tile tracking.
- Smooth turning and native equipment, action animations, and spot effects.
- Optional adaptive camera following the displayed player, with native zoom and
  input handling.
- Prayer icons, skulls, text, health bars, and hitsplats at the displayed position.
- Native presentation handoff for explicit player/NPC Follow.

## Requirements and setup

- Use RuneLite with **GPU** or **117 HD** enabled. Unsupported rendering falls
  back to the native player and displays a renderer requirement message.
- Disable **True Tile Movement**: both plugins manage local-player presentation,
  and Responsive Movement declares a conflict with it.
- Enable **Responsive Movement** in the plugin configuration panel.

This repository is being prepared for Plugin Hub submission; it is not published
by this preparation work. Use the development instructions below to try it.

## Main settings

### Saved profiles

Enable the plugin and open **Responsive Movement profiles** from RuneLite's sidebar
(the stacked-cards icon). It contains a **Profiles** list and **Load**, **Save** and
**Delete** buttons at the bottom.

Selecting a row only selects it. **Load** applies that profile's movement, pacing,
true-tile, camera and overhead settings immediately. The panel reminds you that
you may need to back out of the standard plugin settings and reopen them to refresh
the displayed sliders/checks. **No game restart or plugin restart is needed.**

**Save** captures your current settings and opens a name dialog prefilled with the
last loaded/saved name. Saving the same name updates it; a new name creates another
profile, and Cancel leaves the saves untouched. **Delete** asks "Are you sure?"
before removing the selected save; it does not change your currently applied settings.
Saves persist in your current RuneLite configuration profile, including saves made
with the earlier sidebar. Debugging controls stay independent of loaded profiles.

| Setting | Default | What it controls |
| --- | --- | --- |
| Responsive starts | On | Early visual previews from observed clicks. |
| Movement speed multiplier | 5.0 | Displayed movement pace; 5.0 is normal, range 0.5–10.0. |
| Turning speed | 30 | Maximum custom turning rate, scaled by frame time. |
| Walk-click smoothing (ms) | 50 | Wait up to 0–300 ms from click observation; the next game tick ends a nonzero wait early. 0 disables the wait. |
| Face interactions on arrival | Off | Begin smoothly turning toward the clicked NPC/object as soon as displayed movement arrives, using Turning speed. |
| Original player when aligned | On | Native rendering when stationary position, pose, and facing agree. |
| Adaptive camera | On | Follow displayed movement during drawing; native camera mode is restored before input. |
| Custom overhead rendering | On | Draw local-player overheads at the displayed position. |
| Show native-position camera marker | Off | Fixed orb at the hidden native position while it differs from the displayed position. |

### Movement pacing

Both adjustments and their strength settings now have their own config section.
Existing catch-up settings are retained.

| Setting | Default | What it controls |
| --- | --- | --- |
| Catch up to true tile | On | Gently boost displayed speed while completing confirmed movement to the true tile. |
| Catch-up speed boost (%) | 10 | Maximum extra displayed speed, range 0–50%; tapers near the true tile. |
| Slow down ahead of true tile | On | Gently slow a checked preview that leads true-tile progress. |
| Ahead slowdown (%) | 10 | Maximum displayed speed reduction, range 0–50%; builds over the first tile ahead. |

Strengths are relative to your Movement speed setting: **10%** allows up to
**1.10×** that displayed pace while catching up, or reduces it toward **0.90×**
when ahead. Each toggle is independent; 0 also disables its adjustment. Settings
can be changed while moving without resetting position. Existing combat pursuit,
rejection recovery and the small continuous-run arrival reserve retain their own
pacing. Collision checks and prediction limits still apply.

### True tile

This has its own config section, independent of custom body and overhead rendering.

| Setting | Default | What it controls |
| --- | --- | --- |
| Show true tile | Off | Enable the local player's native/server tile highlight. |
| Highlight tracking | Server true tile | Server-authoritative tile, or native movement tiles between ticks. |
| Fill colour | Translucent cyan | Fill colour and opacity. |
| Border colour | Cyan | Border colour and opacity. |
| Border thickness | 2 | Screen pixels, range 0–10; 0 hides the border. |
| Edge feather (px) | 0 | Soft fill edge and border, range 0–20; 0 keeps a crisp edge. |

**Server true tile** highlights the authoritative location, which can move two tiles
per running tick. **Native movement tiles** samples the hidden native player's
location every overlay frame, so it can show intermediate running tiles. It is
the native client's interpolation rather than server authority, and never uses
Responsive Movement's predicted/displayed position. Low frame rates, loading and
teleports can still skip observations; no missing server route is invented.

### Debugging

**Debugging** is the final, initially collapsed section in the plugin settings.
It contains **Explain movement starts**, **Record movement trace**, and the optional
**Trace bookmark hotkey** (unset by default). Existing persisted debugging keys are
retained. Bookmarks are currently available through the configured hotkey while recording.

Press the configured hotkey immediately after something looks wrong.
A bookmark records the local timestamp with UTC offset, settings and current
display/native/true-tile/route context in the same asynchronous trace. A local
message confirms submission. It does not move the player or restart movement;
the hotkey observes its key without consuming game input, so choose a key that
doesn't conflict with your other bindings.

## Limitations and validation

Displayed position can briefly lead or trail the actual tile. Enable **True tile →
Show true tile** with **Server true tile** tracking to see the authoritative location. Unsupported movement,
teleports, unavailable models, and scene changes can use native presentation.
The plugin does not remove the native client's loading stall.

Custom hitsplats keep four stable display slots. During an ambiguous overflow burst,
overheads temporarily use the native player position so the game selects the visible
hits; custom overheads resume after the burst expires. Displayed body movement continues.

The user confirms the prepared plugin is working as expected in their in-game
testing with **117 HD**. Automated regressions and user reports cover many
movement situations,
but do not establish every interaction or renderer combination. See
[validation notes](docs/VALIDATION.md) for confirmation scope and useful regression
checks, including gates, region crossings, minimap input, and interaction approaches.
The user reports substantially smoother circles and improved repeated knights.
The latest user confirmation says the plugin is working well, including the recent
native Walk observation and startup-timing fixes, and supersedes older pending notes.
The user also confirms the speed/camera/overhead/combat review fixes are working well.
The user confirms the compact profiles-only sidebar is working well apart from a
top-spacing issue. That spacing correction and trace bookmarks pass automated
regressions and await in-game confirmation;
see the current validation status.

## Development

Requires a JDK capable of compiling Java 11 code. The build follows RuneLite's
`latest.release`; use `-PruneLiteVersion=1.13.1` to reproduce the publication audit
against the Plugin Hub version checked on 2026-10-05.

```sh
./gradlew build
./gradlew run
```

On Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat run
```

For development-client login, follow RuneLite's
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
guide. Only the user performs in-game checks; do not automate gameplay input.

### Reporting a movement issue

Enable **Debugging → Record movement trace**, reproduce the issue, add a bookmark
if useful, then turn recording off to flush the pending samples. Report what happened, renderer, settings, and
local timestamp with UTC offset. Logs stay on your computer in
`%USERPROFILE%\.runelite\plugin-data\responsive-movement\`
(or `~/.runelite/plugin-data/responsive-movement/`). RuneLite migrates the former
`.runelite/responsive-movement` folder on first recorded write when the managed
folder does not yet exist. Directory resolution and migration run on the
asynchronous writer, not the client thread.
The writer retains about 64 MiB across eight rotating files. Logs contain
positions, world/collision context, clicked NPC information, and movement
decisions; nothing is uploaded automatically.

## Maintainer documentation

- [Architecture and code map](docs/ARCHITECTURE.md)
- [Publication review and remaining steps](docs/PUBLISHING.md)
- [In-game validation and confirmation history](docs/VALIDATION.md)
- [Native model lifetime](docs/RUNELITE-1.13.md)
- [Trace format](docs/TRACE-FORMAT.md)
- [Archived development README](docs/DEVELOPMENT-HISTORY.md)

## License and attribution

BSD-2-Clause. Derived in part from the True Tile Movement Animations project by
Jacob Richard Nelson / Posiedien, whose contributors include MK677 and JarateKing.
The original copyright attribution and PHYSIQUE-sys's attribution (formerly GimmitheBong) are retained in
[LICENSE](LICENSE), also bundled as `META-INF/LICENSE` in the plugin JAR.
