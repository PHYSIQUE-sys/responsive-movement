package com.responsivemovement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Named movement-setting saves in RuneLite configuration; no filesystem capability. */
final class MovementProfiles
{
    static final String SAVES_KEY = "savedMovementProfiles";
    static final String SELECTED_KEY = "selectedMovementProfile";
    private static final int MAX_PROFILES = 64;
    private final Store store;
    private final MovementSettings settings;

    interface Store
    {
        String get(String key);
        void set(String key, String value);
    }

    MovementProfiles(Store store, MovementSettings settings) { this.store = store; this.settings = settings; }

    List<String> names()
    {
        List<String> names = new ArrayList<>(read().keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    String selected()
    {
        String selected = store.get(SELECTED_KEY);
        return selected != null && read().containsKey(selected) ? selected : "";
    }

    void selectCurrentSettings() { store.set(SELECTED_KEY, ""); }

    void save(String requestedName)
    {
        String name = name(requestedName);
        Map<String, Map<String, String>> profiles = read();
        String existing = profiles.keySet().stream().filter(key -> key.equalsIgnoreCase(name)).findFirst().orElse(name);
        if (!profiles.containsKey(existing) && profiles.size() >= MAX_PROFILES)
        {
            throw new IllegalArgumentException("Up to " + MAX_PROFILES + " profiles can be saved.");
        }
        Map<String, String> values = settings.validatedProfile(settings.capture(false));
        profiles.put(existing, values);
        store.set(SAVES_KEY, encode(profiles));
        store.set(SELECTED_KEY, existing);
    }

    void load(String name)
    {
        Map<String, String> stored = read().get(name);
        if (stored == null) { throw new IllegalArgumentException("That profile is no longer available."); }
        // Validate everything before the first config write. The sidebar applies
        // this batch on the client thread without resetting movement state.
        Map<String, String> values = settings.validatedProfile(stored);
        values.forEach(store::set);
        store.set(SELECTED_KEY, name);
    }

    void delete(String name)
    {
        Map<String, Map<String, String>> profiles = read();
        if (profiles.remove(name) == null) { throw new IllegalArgumentException("That profile is no longer available."); }
        store.set(SAVES_KEY, encode(profiles));
        if (name.equals(store.get(SELECTED_KEY))) { store.set(SELECTED_KEY, ""); }
        // Delete changes only saved metadata; the applied settings stay as they are.
    }

    private Map<String, Map<String, String>> read()
    {
        Map<String, Map<String, String>> profiles = new LinkedHashMap<>();
        String saved = store.get(SAVES_KEY);
        if (saved == null || saved.isEmpty()) { return profiles; }
        try
        {
            if (saved.length() > 131072) { throw new IllegalArgumentException(); }
            JsonObject root = new JsonParser().parse(saved).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) { throw new IllegalArgumentException(); }
            JsonArray entries = root.getAsJsonArray("profiles");
            if (entries.size() > MAX_PROFILES) { throw new IllegalArgumentException(); }
            for (JsonElement entry : entries)
            {
                JsonObject profile = entry.getAsJsonObject();
                String name = name(profile.get("name").getAsString());
                if (profiles.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name))) { throw new IllegalArgumentException(); }
                JsonObject data = profile.getAsJsonObject("settings");
                if (data.size() > 64) { throw new IllegalArgumentException(); }
                Map<String, String> values = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> value : data.entrySet())
                {
                    if (!value.getValue().isJsonPrimitive() || !value.getValue().getAsJsonPrimitive().isString() ||
                        value.getKey().length() > 64 || value.getValue().getAsString().length() > 128) { throw new IllegalArgumentException(); }
                    values.put(value.getKey(), value.getValue().getAsString());
                }
                profiles.put(name, values);
            }
            return profiles;
        }
        catch (RuntimeException error)
        {
            // Preserve malformed/future saves instead of overwriting user data.
            throw new IllegalArgumentException("Saved profiles could not be read; existing saves have been preserved.", error);
        }
    }

    private static String encode(Map<String, Map<String, String>> profiles)
    {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        JsonArray entries = new JsonArray();
        profiles.forEach((name, values) -> {
            JsonObject profile = new JsonObject(), data = new JsonObject();
            profile.addProperty("name", name);
            values.forEach(data::addProperty);
            profile.add("settings", data); entries.add(profile);
        });
        root.add("profiles", entries);
        return root.toString();
    }

    private static String name(String value)
    {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 48 || name.chars().anyMatch(Character::isISOControl))
        {
            throw new IllegalArgumentException("Enter a profile name of 1–48 characters.");
        }
        return name;
    }
}
