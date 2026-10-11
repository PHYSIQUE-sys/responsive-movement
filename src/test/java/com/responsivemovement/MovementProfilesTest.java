package com.responsivemovement;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;
import static org.junit.Assert.*;

public class MovementProfilesTest
{
    @Test
    public void namedSavesRoundTripAllPresentationSettingsAndKeepDebuggingIndependent()
    {
        Fixture f = new Fixture();
        f.values.put("recordTrace", "true"); f.values.put("explainStarts", "true");
        f.profiles.save("  Running  ");
        assertEquals("Running", f.profiles.selected());
        assertEquals(java.util.List.of("Running"), f.profiles.names());
        assertFalse(f.values.get(MovementProfiles.SAVES_KEY).contains("recordTrace"));
        assertFalse(f.values.get(MovementProfiles.SAVES_KEY).contains("traceBookmarkKey"));
        f.speed = 8; f.values.put("movementSpeed", "8.0");
        MovementProfiles restarted = new MovementProfiles(f.store, new MovementSettings(f.config));
        restarted.load("Running");
        assertEquals("5.4", f.values.get("movementSpeed"));
        assertEquals(Integer.toString(new Color(12, 34, 56, 78).getRGB()), f.values.get("trueTileFill"));
        assertEquals("NATIVE_MOVEMENT_TILES", f.values.get("trueTileMode"));
        assertEquals("true", f.values.get("recordTrace"));
        assertEquals("true", f.values.get("explainStarts"));
        assertEquals(31, new MovementSettings(f.config).capture(false).size());
    }

    @Test
    public void savingLoadedNameUpdatesItsSnapshotWithoutCreatingADuplicate()
    {
        Fixture f = new Fixture();
        f.profiles.save("My profile");
        f.speed = 6.1; f.profiles.save("my PROFILE");
        assertEquals(java.util.List.of("My profile"), f.profiles.names());
        assertEquals("My profile", f.profiles.selected());
        f.profiles.load("My profile");
        assertEquals("6.1", f.values.get("movementSpeed"));
        f.profiles.save("Another");
        assertEquals(java.util.List.of("Another", "My profile"), f.profiles.names());
        f.profiles.selectCurrentSettings();
        assertEquals("", f.profiles.selected());
        assertEquals(2, f.profiles.names().size());
    }

    @Test
    public void malformedOrFutureSavesArePreservedAndBadLoadsMakeNoPartialWrites()
    {
        for (String saved : new String[] {"not-json", "{\"schema\":2,\"profiles\":[]}",
            "{\"schema\":1,\"profiles\":[{\"name\":\"Bad\",\"settings\":{\"movementSpeed\":\"6.0\",\"turnSpeed\":\"bad\"}}]}"})
        {
            Fixture f = new Fixture();
            f.values.put(MovementProfiles.SAVES_KEY, saved);
            Map<String, String> before = new HashMap<>(f.values);
            assertThrows(IllegalArgumentException.class, () -> f.profiles.load("Bad"));
            assertEquals(before, f.values);
            if (!saved.contains("Bad"))
            {
                assertThrows(IllegalArgumentException.class, () -> f.profiles.save("New"));
                assertEquals(before, f.values);
            }
        }
    }

    @Test
    public void invalidNamesAndNonfiniteOrOutOfRangeSettingsCannotChangeSaves()
    {
        Fixture f = new Fixture();
        f.profiles.save("Original");
        Map<String, String> before = new HashMap<>(f.values);
        for (String name : new String[] {"", "  ", "bad\nname", "x".repeat(49)})
        {
            assertThrows(IllegalArgumentException.class, () -> f.profiles.save(name));
            assertEquals(before, f.values);
        }
        for (double speed : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1, 11})
        {
            f.speed = speed;
            assertThrows(IllegalArgumentException.class, () -> f.profiles.save("Original"));
            assertEquals(before, f.values);
        }
    }

    @Test
    public void olderPartialSavesLoadOnlyKnownPresentationKeys()
    {
        Fixture f = new Fixture();
        f.values.put(MovementProfiles.SAVES_KEY, "{\"schema\":1,\"profiles\":[{\"name\":\"Older\",\"settings\":" +
            "{\"movementSpeed\":\"5.1\",\"recordTrace\":\"true\",\"otherPluginKey\":\"value\"}}]}");
        f.profiles.load("Older");
        assertEquals("5.1", f.values.get("movementSpeed"));
        assertFalse(f.values.containsKey("recordTrace"));
        assertFalse(f.values.containsKey("otherPluginKey"));
        assertFalse(f.values.containsKey("turnSpeed"));
    }

    @Test
    public void deletingLoadedProfilePreservesLiveSettingsAndOtherSavedProfiles()
    {
        Fixture f = new Fixture();
        f.profiles.save("First"); f.speed = 6.2; f.profiles.save("Second");
        f.profiles.load("Second");
        Map<String, String> before = new HashMap<>(f.values);
        f.profiles.delete("Second");
        assertEquals(java.util.List.of("First"), f.profiles.names());
        assertEquals("", f.profiles.selected());
        before.forEach((key, value) -> {
            if (!key.equals(MovementProfiles.SAVES_KEY) && !key.equals(MovementProfiles.SELECTED_KEY))
            {
                assertEquals(value, f.values.get(key));
            }
        });
        f.profiles.load("First");
        assertEquals("5.4", f.values.get("movementSpeed"));
    }

    @Test
    public void deletingAnotherProfileKeepsLoadedNameAndMissingDeleteMakesNoWrites()
    {
        Fixture f = new Fixture();
        f.profiles.save("First"); f.profiles.save("Second");
        f.profiles.delete("First");
        assertEquals("Second", f.profiles.selected());
        Map<String, String> before = new HashMap<>(f.values);
        assertThrows(IllegalArgumentException.class, () -> f.profiles.delete("First"));
        assertEquals(before, f.values);
        f.profiles.delete("Second");
        assertTrue(f.profiles.names().isEmpty());
        assertEquals("", f.profiles.selected());
    }

    @Test
    public void loadingFinePaceWhileMovingKeepsTheFractionAndExistingMovementClock()
    {
        NpcApproachControllerTest.Fixture f = new NpcApproachControllerTest.Fixture(1344, 1344, 7000, 7000);
        try
        {
            f.starts = false; f.speed = 1.02;
            Fixture stored = new Fixture();
            MovementProfiles.Store store = new MovementProfiles.Store() {
                @Override public String get(String key) { return stored.values.get(key); }
                @Override public void set(String key, String value)
                {
                    stored.values.put(key, value);
                    if (key.equals("movementSpeed")) { f.speed = Double.parseDouble(value) / 5; }
                }
            };
            MovementProfiles profiles = new MovementProfiles(store, new MovementSettings(new ResponsiveMovementConfig() {
                @Override public double movementSpeed() { return f.speed * 5; }
            }));
            profiles.save("Fine pace");
            f.speed = 1;
            f.authority = new net.runelite.api.coords.LocalPoint(1600, 1344, 0);
            f.frame(100);
            net.runelite.api.coords.LocalPoint before = f.controller.position();
            profiles.load("Fine pace");
            assertEquals(before, f.controller.position());
            f.frame(100);
            assertEquals("same-time preparation must not spend another budget", before, f.controller.position());
            f.frame(200);
            assertEquals(before.getX() + 41, f.controller.position().getX());
        }
        finally { f.controller.close(); }
    }

    @Test
    public void settingsRegistryCoversEveryConfigKeyAndDebuggingIsLastWithStableKeys() throws Exception
    {
        MovementSettings settings = new MovementSettings(new ResponsiveMovementConfig() {});
        Set<String> registered = new HashSet<>();
        for (MovementSettings.Option option : settings.options) { assertTrue(registered.add(option.key)); }
        Set<String> annotated = new HashSet<>();
        for (java.lang.reflect.Method method : ResponsiveMovementConfig.class.getMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item != null) { annotated.add(item.keyName()); }
        }
        assertEquals(annotated, registered);
        assertEquals("debugging", ResponsiveMovementConfig.class.getMethod("recordTrace").getAnnotation(ConfigItem.class).section());
        assertEquals("recordTrace", ResponsiveMovementConfig.class.getMethod("recordTrace").getAnnotation(ConfigItem.class).keyName());
        assertEquals("explainStarts", ResponsiveMovementConfig.class.getMethod("explainStarts").getAnnotation(ConfigItem.class).keyName());
        int debugPosition = ResponsiveMovementConfig.class.getField("DEBUGGING").getAnnotation(ConfigSection.class).position();
        for (java.lang.reflect.Field field : ResponsiveMovementConfig.class.getFields())
        {
            ConfigSection section = field.getAnnotation(ConfigSection.class);
            if (section != null && !field.getName().equals("DEBUGGING")) { assertTrue(section.position() < debugPosition); }
        }
    }

    static final class Fixture
    {
        final Map<String, String> values = new HashMap<>();
        double speed = 5.4;
        final ResponsiveMovementConfig config = new ResponsiveMovementConfig() {
            @Override public double movementSpeed() { return speed; }
            @Override public Color trueTileFill() { return new Color(12, 34, 56, 78); }
            @Override public TrueTileMode trueTileMode() { return TrueTileMode.NATIVE_MOVEMENT_TILES; }
        };
        final MovementProfiles.Store store = new MovementProfiles.Store() {
            @Override public String get(String key) { return values.get(key); }
            @Override public void set(String key, String value) { values.put(key, value); }
        };
        final MovementProfiles profiles = new MovementProfiles(store, new MovementSettings(config));
    }
}
