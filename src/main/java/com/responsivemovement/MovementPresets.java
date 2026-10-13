package com.responsivemovement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bundled settings only; listing these presets never applies or persists them. */
final class MovementPresets
{
    static final String DEFAULT = "Default";
    static final String TRUE_TILE = "True Tile";
    static final Map<String, Map<String, String>> PROFILES = create();

    private static Map<String, Map<String, String>> create()
    {
        Map<String, String> defaults = new MovementSettings(new ResponsiveMovementConfig() {}).capture(false);
        Map<String, String> trueTile = new LinkedHashMap<>(defaults);
        // The user's saved True Tile snapshot, including its exact decimal values.
        // Future settings start from plugin defaults rather than the live profile.
        trueTile.putAll(Map.ofEntries(
            Map.entry("responsiveStarts", "true"),
            Map.entry("movementSpeed", "5.8999999999999995"),
            Map.entry("turnSpeed", "30"),
            Map.entry("snapDistance", "5"),
            Map.entry("originalWhenAligned", "false"),
            Map.entry("clickSmoothingMs", "0"),
            Map.entry("faceInteractionsOnArrival", "true"),
            Map.entry("catchUp", "true"),
            Map.entry("catchUpPercent", "15"),
            Map.entry("slowAhead", "true"),
            Map.entry("slowAheadPercent", "13"),
            Map.entry("slowArrival", "true"),
            Map.entry("slowArrivalPercent", "1"),
            Map.entry("arrivalFirstTiles", "0.19999999999999998"),
            Map.entry("arrivalFirstSpeed", "50.0"),
            Map.entry("arrivalFinalTiles", "1.0"),
            Map.entry("arrivalFinalSpeed", "49.800000000000004"),
            Map.entry("showTrueTile", "true"),
            Map.entry("trueTileMode", "SERVER_TRUE_TILE"),
            Map.entry("trueTileFill", "8487040"),
            Map.entry("trueTileBorder", "-939472641"),
            Map.entry("trueTileBorderWidth", "1"),
            Map.entry("trueTileFeather", "1"),
            Map.entry("adaptiveCamera", "true"),
            Map.entry("followDistance", "64"),
            Map.entry("cameraVelocity", "4.0"),
            Map.entry("cameraSnap", "5.0"),
            Map.entry("cameraMarker", "false"),
            Map.entry("markerSpeed", "150.0"),
            Map.entry("markerStrength", "15.0"),
            Map.entry("markerHeight", "0"),
            Map.entry("markerTurnSpeed", "20"),
            Map.entry("overheads", "true"),
            Map.entry("overheadHeight", "7"),
            Map.entry("textOffset", "0"),
            Map.entry("healthOffset", "11"),
            Map.entry("hitsplatSpacing", "25")));
        return Map.of(DEFAULT, Collections.unmodifiableMap(defaults), TRUE_TILE, Collections.unmodifiableMap(trueTile));
    }

    private MovementPresets() { }
}
