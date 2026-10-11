package com.responsivemovement;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.runelite.client.config.Keybind;

/** Explicit supported settings for profile data and bookmark evidence; no reflection. */
final class MovementSettings
{
    enum Kind { BOOLEAN, INTEGER, DECIMAL, COLOR, TRACKING, KEYBIND }
    static final String[] SECTIONS = {"Movement", "Movement pacing", "True tile", "Camera", "Overheads", "Debugging"};
    final List<Option> options;

    MovementSettings(ResponsiveMovementConfig config)
    {
        List<Option> items = new ArrayList<>();
        items.add(bool("responsiveStarts", "Responsive starts", 0, config::responsiveStarts));
        items.add(decimal("movementSpeed", "Movement speed multiplier", 0, config::movementSpeed, 0.5, 10));
        items.add(integer("turnSpeed", "Turning speed", 0, config::turnSpeed, 1, 100));
        items.add(integer("snapDistance", "Discontinuity snap distance", 0, config::snapDistance, 1, 10));
        items.add(bool("originalWhenAligned", "Original player when aligned", 0, config::originalWhenAligned));
        items.add(integer("clickSmoothingMs", "Walk-click smoothing (ms)", 0, config::clickSmoothingMs, 0, 300));
        items.add(bool("faceInteractionsOnArrival", "Face interactions on arrival", 0, config::faceInteractionsOnArrival));
        items.add(bool("catchUp", "Catch up to true tile", 1, config::catchUp));
        items.add(integer("catchUpPercent", "Catch-up speed boost (%)", 1, config::catchUpPercent, 0, 50));
        items.add(bool("slowAhead", "Slow down ahead of true tile", 1, config::slowAhead));
        items.add(integer("slowAheadPercent", "Ahead slowdown (%)", 1, config::slowAheadPercent, 0, 50));
        items.add(bool("showTrueTile", "Show true tile", 2, config::showTrueTile));
        items.add(new Option("trueTileMode", "Highlight tracking", 2, Kind.TRACKING, config::trueTileMode, 0, 0));
        items.add(new Option("trueTileFill", "Fill colour", 2, Kind.COLOR, config::trueTileFill, 0, 0));
        items.add(new Option("trueTileBorder", "Border colour", 2, Kind.COLOR, config::trueTileBorder, 0, 0));
        items.add(integer("trueTileBorderWidth", "Border thickness", 2, config::trueTileBorderWidth, 0, 10));
        items.add(integer("trueTileFeather", "Edge feather (px)", 2, config::trueTileFeather, 0, 20));
        items.add(bool("adaptiveCamera", "Adaptive camera", 3, config::adaptiveCamera));
        items.add(integer("followDistance", "Following distance", 3, config::followDistance, 1, 512));
        items.add(decimal("cameraVelocity", "Following velocity", 3, config::cameraVelocity, 0, 100));
        items.add(decimal("cameraSnap", "Camera snap distance", 3, config::cameraSnap, 0.1, 100));
        items.add(bool("cameraMarker", "Show native-position camera marker", 3, config::cameraMarker));
        items.add(decimal("markerSpeed", "Marker animation speed", 3, config::markerSpeed, 0, 1000));
        items.add(decimal("markerStrength", "Marker animation distance", 3, config::markerStrength, 0, 128));
        items.add(integer("markerHeight", "Marker height offset", 3, config::markerHeight, 0, Integer.MAX_VALUE));
        items.add(integer("markerTurnSpeed", "Marker turning speed", 3, config::markerTurnSpeed, 0, Integer.MAX_VALUE));
        items.add(bool("overheads", "Custom overhead rendering", 4, config::overheads));
        items.add(integer("overheadHeight", "Overhead height offset", 4, config::overheadHeight, 0, Integer.MAX_VALUE));
        items.add(integer("textOffset", "Overhead text offset", 4, config::textOffset, 0, Integer.MAX_VALUE));
        items.add(integer("healthOffset", "Health bar offset", 4, config::healthOffset, 0, Integer.MAX_VALUE));
        items.add(integer("hitsplatSpacing", "Hitsplat spacing", 4, config::hitsplatSpacing, 0, Integer.MAX_VALUE));
        items.add(bool("explainStarts", "Explain movement starts", 5, config::explainStarts));
        items.add(bool("recordTrace", "Record movement trace", 5, config::recordTrace));
        items.add(new Option("traceBookmarkKey", "Trace bookmark hotkey", 5, Kind.KEYBIND, config::traceBookmarkKey, 0, 0));
        options = Collections.unmodifiableList(items);
    }

    Map<String, String> capture(boolean includeDebugging)
    {
        Map<String, String> values = new LinkedHashMap<>();
        for (Option option : options)
        {
            if (includeDebugging || option.section != 5) { values.put(option.key, option.encode(option.read.get())); }
        }
        return values;
    }

    Map<String, String> validatedProfile(Map<String, String> stored)
    {
        Map<String, String> values = new LinkedHashMap<>();
        for (Option option : options)
        {
            // Debug toggles, hotkeys and unknown future keys never acquire a
            // profile's movement/presentation ownership. Missing keys retain current values.
            if (option.section != 5 && stored.containsKey(option.key))
            {
                values.put(option.key, option.encode(option.decode(stored.get(option.key))));
            }
        }
        return values;
    }

    private static Option bool(String key, String name, int section, Supplier<Boolean> read)
    {
        return new Option(key, name, section, Kind.BOOLEAN, read, 0, 0);
    }
    private static Option integer(String key, String name, int section, Supplier<Integer> read, int min, int max)
    {
        return new Option(key, name, section, Kind.INTEGER, read, min, max);
    }
    private static Option decimal(String key, String name, int section, Supplier<Double> read, double min, double max)
    {
        return new Option(key, name, section, Kind.DECIMAL, read, min, max);
    }

    static final class Option
    {
        final String key, name;
        final int section;
        final Kind kind;
        final Supplier<?> read;
        final double min, max;

        Option(String key, String name, int section, Kind kind, Supplier<?> read, double min, double max)
        {
            this.key = key; this.name = name; this.section = section; this.kind = kind;
            this.read = read; this.min = min; this.max = max;
        }

        String encode(Object value)
        {
            if (kind == Kind.COLOR) { return Integer.toString(((Color) value).getRGB()); }
            if (kind == Kind.TRACKING) { return ((TrueTileMode) value).name(); }
            if (kind == Kind.KEYBIND)
            {
                Keybind binding = (Keybind) value;
                return binding.getKeyCode() + ":" + binding.getModifiers();
            }
            return String.valueOf(value);
        }

        Object decode(String value)
        {
            try
            {
                switch (kind)
                {
                    case BOOLEAN:
                        if (!"true".equals(value) && !"false".equals(value)) { throw new IllegalArgumentException(); }
                        return Boolean.parseBoolean(value);
                    case INTEGER:
                        int integer = Integer.parseInt(value);
                        if (integer < min || integer > max) { throw new IllegalArgumentException(); }
                        return integer;
                    case DECIMAL:
                        double decimal = Double.parseDouble(value);
                        if (!Double.isFinite(decimal) || decimal < min || decimal > max) { throw new IllegalArgumentException(); }
                        return decimal;
                    case COLOR: return new Color(Integer.parseInt(value), true);
                    case TRACKING: return TrueTileMode.valueOf(value);
                    case KEYBIND:
                        String[] parts = value.split(":");
                        if (parts.length != 2) { throw new IllegalArgumentException(); }
                        return new Keybind(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                    default: throw new IllegalArgumentException();
                }
            }
            catch (IllegalArgumentException error)
            {
                throw new IllegalArgumentException("Invalid saved value for " + name, error);
            }
        }
    }
}
