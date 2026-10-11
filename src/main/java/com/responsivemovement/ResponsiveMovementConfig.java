package com.responsivemovement;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(ResponsiveMovementConfig.GROUP)
public interface ResponsiveMovementConfig extends Config
{
    String GROUP = "responsive-movement";

    @ConfigSection(name = "Movement", description = "One renderer for all movement", position = 0)
    String MOVEMENT = "movement";
    @ConfigSection(name = "Movement pacing", description = "Adjust displayed speed relative to the true tile", position = 1)
    String PACING = "pacing";
    @ConfigSection(name = "True tile", description = "Highlight the server tile or observed native movement tiles", position = 2)
    String TRUE_TILE = "trueTile";
    @ConfigSection(name = "Camera", description = "Presentation camera and native zoom", position = 3)
    String CAMERA = "camera";
    @ConfigSection(name = "Overheads", description = "Overheads at the visible player", position = 4)
    String OVERHEADS = "overheads";
    @ConfigSection(name = "Debugging", description = "Local diagnostic recording and movement-start messages", position = 5,
        closedByDefault = true)
    String DEBUGGING = "debugging";

    @ConfigItem(keyName = "responsiveStarts", name = "Responsive starts", position = 0,
        description = "Preview walk and interaction destinations around obstacles, from idle or after confirmed movement", section = MOVEMENT)
    default boolean responsiveStarts() { return true; }

    @ConfigItem(keyName = "movementSpeed", name = "Movement speed multiplier", position = 1,
        description = "Displayed movement only; 5.0 is normal (0.5–10.0). Does not change server movement speed", section = MOVEMENT)
    default double movementSpeed() { return 5.0; }

    @ConfigItem(keyName = "turnSpeed", name = "Turning speed", position = 2,
        description = "Maximum orientation units per 60 FPS frame for custom movement and facing; short moves finish turning smoothly", section = MOVEMENT)
    @Range(min = 1, max = 100)
    default int turnSpeed() { return 30; }

    @ConfigItem(keyName = "snapDistance", name = "Discontinuity snap distance", position = 3,
        description = "Maximum tile difference per axis for continuous scene recovery", section = MOVEMENT)
    @Range(min = 1, max = 10)
    default int snapDistance() { return 5; }

    @ConfigItem(keyName = "originalWhenAligned", name = "Original player when aligned", position = 4,
        description = "Use the native player pass when stationary position, pose, and facing agree", section = MOVEMENT)
    default boolean originalWhenAligned() { return true; }

    @ConfigItem(keyName = "explainStarts", name = "Explain movement starts", position = 0,
        description = "Print a local chat diagnostic when a responsive idle start is accepted", section = DEBUGGING)
    default boolean explainStarts() { return false; }

    @ConfigItem(keyName = "recordTrace", name = "Record movement trace", position = 1,
        description = "Record until switched off; retains about 64 MiB of rotating logs in .runelite/plugin-data/responsive-movement", section = DEBUGGING)
    default boolean recordTrace() { return false; }

    @ConfigItem(keyName = "traceBookmarkKey", name = "Trace bookmark hotkey", position = 2,
        description = "Mark a moment in an active movement trace. Recording must be enabled; the key is observed without consuming game input", section = DEBUGGING)
    default Keybind traceBookmarkKey() { return Keybind.NOT_SET; }

    @ConfigItem(keyName = "clickSmoothingMs", name = "Walk-click smoothing (ms)", position = 7,
        description = "Wait up to 0–300 ms after a scene or minimap Walk click. When above 0, the next game tick ends the wait early. 0 disables the wait; native movement continues normally", section = MOVEMENT)
    @Range(min = 0, max = 300)
    default int clickSmoothingMs() { return 50; }

    @ConfigItem(keyName = "faceInteractionsOnArrival", name = "Face interactions on arrival", position = 8,
        description = "Instantly turn to NPCs/objects the moment you arrive at them", section = MOVEMENT)
    default boolean faceInteractionsOnArrival() { return false; }

    @ConfigItem(keyName = "catchUp", name = "Catch up to true tile", position = 0,
        description = "Gently increase displayed speed while behind confirmed true-tile movement", section = PACING)
    default boolean catchUp() { return true; }

    @ConfigItem(keyName = "catchUpPercent", name = "Catch-up speed boost (%)", position = 1,
        description = "Maximum extra displayed speed when catching up (0–50%). Tapers near the true tile; 0 disables the boost", section = PACING)
    @Range(min = 0, max = 50)
    default int catchUpPercent() { return 10; }

    @ConfigItem(keyName = "slowAhead", name = "Slow down ahead of true tile", position = 2,
        description = "Gently reduce displayed speed when a checked preview leads the true tile", section = PACING)
    default boolean slowAhead() { return true; }

    @ConfigItem(keyName = "slowAheadPercent", name = "Ahead slowdown (%)", position = 3,
        description = "Maximum displayed speed reduction (0–50%). Builds over the first tile ahead; 0 disables slowdown", section = PACING)
    @Range(min = 0, max = 50)
    default int slowAheadPercent() { return 10; }

    @ConfigItem(keyName = "showTrueTile", name = "Show true tile", position = 0,
        description = "Highlight the real server tile independently of the plugin's displayed player", section = TRUE_TILE)
    default boolean showTrueTile() { return false; }

    @ConfigItem(keyName = "trueTileMode", name = "Highlight tracking", position = 1,
        description = "Server true tile is tick-authoritative. Native movement tiles follows the hidden native player between ticks, including intermediate running tiles", section = TRUE_TILE)
    default TrueTileMode trueTileMode() { return TrueTileMode.SERVER_TRUE_TILE; }

    @Alpha
    @ConfigItem(keyName = "trueTileFill", name = "Fill colour", position = 2,
        description = "True-tile fill colour and opacity", section = TRUE_TILE)
    default Color trueTileFill() { return new Color(0, 200, 255, 40); }

    @Alpha
    @ConfigItem(keyName = "trueTileBorder", name = "Border colour", position = 3,
        description = "True-tile border colour and opacity", section = TRUE_TILE)
    default Color trueTileBorder() { return new Color(0, 200, 255, 200); }

    @ConfigItem(keyName = "trueTileBorderWidth", name = "Border thickness", position = 4,
        description = "Border width in screen pixels; 0 hides the border", section = TRUE_TILE)
    @Range(min = 0, max = 10)
    default int trueTileBorderWidth() { return 2; }

    @ConfigItem(keyName = "trueTileFeather", name = "Edge feather (px)", position = 5,
        description = "Soften the fill edge and border in screen pixels; 0 keeps a crisp highlight", section = TRUE_TILE)
    @Range(min = 0, max = 20)
    default int trueTileFeather() { return 0; }

    @ConfigItem(keyName = "adaptiveCamera", name = "Adaptive camera", position = 0,
        description = "Follow the visible player during rendering; native mode is used for menus and input", section = CAMERA)
    default boolean adaptiveCamera() { return true; }

    @ConfigItem(keyName = "followDistance", name = "Following distance", position = 1,
        description = "Camera following distance in local units (128 = one tile)", section = CAMERA)
    @Range(min = 1, max = 512)
    default int followDistance() { return 64; }

    @ConfigItem(keyName = "cameraVelocity", name = "Following velocity", position = 2,
        description = "Frame-rate-independent camera approach speed", section = CAMERA)
    default double cameraVelocity() { return 4.0; }

    @ConfigItem(keyName = "cameraSnap", name = "Camera snap distance", position = 3,
        description = "Distance in tiles at which the camera snaps to the visible player", section = CAMERA)
    default double cameraSnap() { return 5.0; }

    @ConfigItem(keyName = "cameraMarker", name = "Show native-position camera marker", position = 4,
        description = "Optional orb marking the hidden native player's position", section = CAMERA)
    default boolean cameraMarker() { return false; }

    @ConfigItem(keyName = "markerSpeed", name = "Marker animation speed", position = 5,
        description = "Speed of the optional marker's oscillation", section = CAMERA)
    default double markerSpeed() { return 150.0; }

    @ConfigItem(keyName = "markerStrength", name = "Marker animation distance", position = 6,
        description = "Marker oscillation distance in local units", section = CAMERA)
    default double markerStrength() { return 15.0; }

    @ConfigItem(keyName = "markerHeight", name = "Marker height offset", position = 7,
        description = "Marker height above the terrain in local units", section = CAMERA)
    default int markerHeight() { return 0; }

    @ConfigItem(keyName = "markerTurnSpeed", name = "Marker turning speed", position = 8,
        description = "Turning speed of the optional camera-position marker", section = CAMERA)
    default int markerTurnSpeed() { return 20; }

    @ConfigItem(keyName = "overheads", name = "Custom overhead rendering", position = 0,
        description = "Move prayers, skulls, overhead text, health bars, and hitsplats with the visible player", section = OVERHEADS)
    default boolean overheads() { return true; }

    @ConfigItem(keyName = "overheadHeight", name = "Overhead height offset", position = 1,
        description = "Additional height of custom overhead objects", section = OVERHEADS)
    default int overheadHeight() { return 7; }

    @ConfigItem(keyName = "textOffset", name = "Overhead text offset", position = 2,
        description = "Vertical text adjustment", section = OVERHEADS)
    default int textOffset() { return 9; }

    @ConfigItem(keyName = "healthOffset", name = "Health bar offset", position = 3,
        description = "Vertical health bar adjustment", section = OVERHEADS)
    default int healthOffset() { return 10; }

    @ConfigItem(keyName = "hitsplatSpacing", name = "Hitsplat spacing", position = 4,
        description = "Spacing between up to four active hitsplats", section = OVERHEADS)
    default int hitsplatSpacing() { return 25; }
}
