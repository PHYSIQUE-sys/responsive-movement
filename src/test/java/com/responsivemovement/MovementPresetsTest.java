package com.responsivemovement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class MovementPresetsTest
{
    @Test
    public void freshProfileListsBothPresetsWithoutApplyingOrPersistingEither()
    {
        MovementProfilesTest.Fixture f = new MovementProfilesTest.Fixture(true);
        Map<String, String> before = new HashMap<>(f.values);
        assertEquals(List.of("Default", "True Tile"), f.profiles.names());
        assertEquals("", f.profiles.selected());
        assertTrue(f.profiles.savedNames().isEmpty());
        assertEquals(before, f.values);
    }

    @Test
    public void trueTileLoadsTheUsersSavedSnapshotAndKeepsDebuggingIndependent()
    {
        MovementProfilesTest.Fixture f = new MovementProfilesTest.Fixture(true);
        f.values.put("recordTrace", "true");
        f.values.put("explainStarts", "true");
        f.profiles.load("True Tile");
        assertEquals("True Tile", f.profiles.selected());
        Map<String, String> snapshot = MovementPresets.PROFILES.get("True Tile");
        snapshot.forEach((key, value) -> assertEquals(key, value, f.values.get(key)));
        assertEquals(5.9, Double.parseDouble(f.values.get("movementSpeed")), 1e-12);
        assertEquals(0.2, Double.parseDouble(f.values.get("arrivalFirstTiles")), 1e-12);
        assertEquals(49.8, Double.parseDouble(f.values.get("arrivalFinalSpeed")), 1e-12);
        assertEquals("true", f.values.get("slowArrival"));
        assertEquals("true", f.values.get("showTrueTile"));
        assertEquals("false", f.values.get("originalWhenAligned"));
        assertEquals("0", f.values.get("clickSmoothingMs"));
        assertEquals("true", f.values.get("recordTrace"));
        assertEquals("true", f.values.get("explainStarts"));
        assertFalse(f.values.containsKey(MovementProfiles.SAVES_KEY));
    }

    @Test
    public void defaultRestoresAllNonDebuggingDefaultsAfterLoadingTrueTile()
    {
        MovementProfilesTest.Fixture f = new MovementProfilesTest.Fixture(true);
        f.values.put("recordTrace", "true");
        f.profiles.load("True Tile");
        f.profiles.load("Default");
        Map<String, String> defaults = new MovementSettings(new ResponsiveMovementConfig() {}).capture(false);
        defaults.forEach((key, value) -> assertEquals(key, value, f.values.get(key)));
        assertEquals("Default", f.profiles.selected());
        assertEquals("true", f.values.get("recordTrace"));
        assertFalse(f.values.containsKey(MovementProfiles.SAVES_KEY));
    }

    @Test
    public void existingSameNameSaveTakesPrecedenceIgnoringCaseAndDeletionRevealsThePreset()
    {
        MovementProfilesTest.Fixture f = new MovementProfilesTest.Fixture(true);
        String saved = "{\"schema\":1,\"profiles\":[{\"name\":\"true tile\",\"settings\":{\"movementSpeed\":\"6.3\"}}]}";
        f.values.put(MovementProfiles.SAVES_KEY, saved);
        assertEquals(List.of("Default", "true tile"), f.profiles.names());
        assertEquals(saved, f.values.get(MovementProfiles.SAVES_KEY));
        f.profiles.load("true tile");
        assertEquals("6.3", f.values.get("movementSpeed"));
        f.profiles.delete("true tile");
        assertEquals("6.3", f.values.get("movementSpeed"));
        assertEquals("", f.profiles.selected());
        assertEquals(List.of("Default", "True Tile"), f.profiles.names());
        f.profiles.load("True Tile");
        assertEquals(5.9, Double.parseDouble(f.values.get("movementSpeed")), 1e-12);
    }

    @Test
    public void savingABundledNameCreatesOneSavedOverrideAndCannotMutateTheBundle()
    {
        MovementProfilesTest.Fixture f = new MovementProfilesTest.Fixture(true);
        f.profiles.load("True Tile");
        f.speed = 7.2;
        f.profiles.save("TRUE TILE");
        assertEquals(List.of("Default", "True Tile"), f.profiles.names());
        assertEquals(List.of("True Tile"), f.profiles.savedNames());
        f.profiles.load("True Tile");
        assertEquals("7.2", f.values.get("movementSpeed"));
        f.profiles.delete("True Tile");
        Map<String, String> before = new HashMap<>(f.values);
        assertThrows(IllegalArgumentException.class, () -> f.profiles.delete("True Tile"));
        assertEquals(before, f.values);
        f.profiles.load("True Tile");
        assertEquals(5.9, Double.parseDouble(f.values.get("movementSpeed")), 1e-12);
    }

    @Test
    public void bundledProfilesAreCompleteValidatedSnapshotsAndDoNotConsumeSaveSlots()
    {
        MovementSettings settings = new MovementSettings(new ResponsiveMovementConfig() {});
        for (Map<String, String> profile : MovementPresets.PROFILES.values())
        {
            assertEquals(settings.capture(false).keySet(), profile.keySet());
            assertEquals(profile, settings.validatedProfile(profile));
            assertFalse(profile.containsKey("recordTrace"));
            assertFalse(profile.containsKey("traceBookmarkKey"));
        }
        MovementProfilesTest.Fixture f = new MovementProfilesTest.Fixture(true);
        for (int i = 0; i < 64; ++i) { f.profiles.save("Saved " + i); }
        assertEquals(66, f.profiles.names().size());
        assertEquals(64, f.profiles.savedNames().size());
        Map<String, String> before = new HashMap<>(f.values);
        assertThrows(IllegalArgumentException.class, () -> f.profiles.save("One more"));
        assertEquals(before, f.values);
    }
}
