package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native evidence and shared movement continuity, never inferred item/spell reach. */
public class NativeUseContinuityTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }
    private static final MenuAction[] NATIVE_ACTIONS = {
        MenuAction.ITEM_USE_ON_NPC, MenuAction.WIDGET_TARGET_ON_NPC,
        MenuAction.ITEM_USE_ON_PLAYER, MenuAction.WIDGET_TARGET_ON_PLAYER,
        MenuAction.PLAYER_FIRST_OPTION, MenuAction.PLAYER_SECOND_OPTION, MenuAction.PLAYER_THIRD_OPTION,
        MenuAction.PLAYER_FOURTH_OPTION, MenuAction.PLAYER_FIFTH_OPTION, MenuAction.PLAYER_SIXTH_OPTION,
        MenuAction.PLAYER_SEVENTH_OPTION, MenuAction.PLAYER_EIGHTH_OPTION,
        MenuAction.ITEM_USE_ON_GAME_OBJECT, MenuAction.WIDGET_TARGET_ON_GAME_OBJECT,
        MenuAction.ITEM_USE_ON_GROUND_ITEM, MenuAction.WIDGET_TARGET_ON_GROUND_ITEM,
        MenuAction.WORLD_ENTITY_FIRST_OPTION, MenuAction.WORLD_ENTITY_SECOND_OPTION,
        MenuAction.WORLD_ENTITY_THIRD_OPTION, MenuAction.WORLD_ENTITY_FOURTH_OPTION,
        MenuAction.WORLD_ENTITY_FIFTH_OPTION};

    @Test
    public void recordedIdleUseAndObjectPublicationsDoNotStopBetweenServerSteps() throws Exception
    {
        List<String[]> rows = new ArrayList<>(), maps = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("native-use-2158.txt"), StandardCharsets.UTF_8)))
        {
            reader.lines().filter(line -> !line.startsWith("#")).forEach(line -> {
                String[] fields = line.split(" ");
                (fields[0].equals("M") ? maps : rows).add(fields);
            });
        }
        for (int index = 0; index < rows.size();)
        {
            String[] click = rows.get(index++);
            List<String[]> events = new ArrayList<>();
            while (index < rows.size() && rows.get(index)[0].equals("S")) { events.add(rows.get(index++)); }
            for (long cadence : new long[] {8_333, 20_000, 33_333})
            {
                Fixture f = new Fixture(Integer.parseInt(click[3]), Integer.parseInt(click[4]), 6976, 8256);
                try
                {
                    settings(f); f.faceInteractionsOnArrival = true;
                    for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
                    for (String[] map : maps)
                    {
                        String[] values = map[2].split(",");
                        for (int y = 0; y < values.length; ++y)
                        {
                            f.flags[Integer.parseInt(map[1])][50 + y] = Integer.parseUnsignedInt(values[y], 16);
                        }
                    }
                    MenuAction action = MenuAction.valueOf(click[2]);
                    f.controller.worldInteraction(action.name().endsWith("NPC") || action.name().endsWith("PLAYER")
                        ? f.event(action, "Use") : f.sceneEvent(action, "Use", Integer.parseInt(click[7]),
                            Integer.parseInt(click[5]), Integer.parseInt(click[6])));
                    TreeSet<Long> times = new TreeSet<>();
                    for (long t = 0; t <= 4_000_000; t += cadence) { times.add(t); }
                    for (String[] event : events) { times.add(Long.parseLong(event[1])); }
                    int next = 0;
                    LocalPoint previous = f.start;
                    long previousTime = 0, stopped = 0;
                    long firstAuthority = Long.parseLong(events.get(1)[1]);
                    long finalAuthority = Long.parseLong(events.get(events.size() - 1)[1]);
                    for (long t : times)
                    {
                        at(f, t);
                        while (next < events.size() && Long.parseLong(events.get(next)[1]) <= t)
                        {
                            String[] event = events.get(next++);
                            f.authority = point(event, 2);
                            f.destination = Integer.parseInt(event[4]) < 0 ? null : point(event, 4);
                            f.nativePoint = point(event, 6); f.animation = Integer.parseInt(event[8]);
                        }
                        int angle = f.controller.orientation(); f.controller.update();
                        LocalPoint actual = f.controller.position();
                        assertTrue("single clock: " + click[1], MovementPath.distance(previous, actual) <=
                            Math.ceil((t - previousTime) * 0.000472 * 1.15) + 1);
                        assertTrue("single facing cap", Math.abs(MotionMath.difference(angle, f.controller.orientation())) <=
                            Math.ceil((t - previousTime) * 30 / 16_667.0) + 1);
                        if (t > firstAuthority + cadence && t < finalAuthority)
                        {
                            stopped = actual.equals(previous) ? stopped + t - previousTime : 0;
                            assertTrue("click " + click[1] + " intermediate hold=" + stopped + "us at " + actual, stopped < 40_000);
                        }
                        // Use now has a bounded click-time target forecast;
                        // native publications still establish its actual stop.
                        previous = actual; previousTime = t;
                    }
                    assertEquals("final native stop: " + click[1], f.authority, f.controller.position());
                }
                finally { f.controller.close(); assertTrue(f.objects.isEmpty()); }
            }
        }
    }

    @Test
    public void recordedMovingUseClicksRetireOldWalkAndKeepTheNewNativeApproachContinuous() throws Exception
    {
        List<String[]> rows = new ArrayList<>(), maps = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("native-use-moving-2158.txt"), StandardCharsets.UTF_8)))
        {
            reader.lines().filter(line -> !line.startsWith("#")).forEach(line -> rows.add(line.split(" ")));
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("native-use-2158.txt"), StandardCharsets.UTF_8)))
        {
            reader.lines().filter(line -> line.startsWith("M ")).forEach(line -> maps.add(line.split(" ")));
        }
        for (int index = 0; index < rows.size();)
        {
            String marker = rows.get(index++)[1];
            List<String[]> events = new ArrayList<>();
            while (index < rows.size() && !rows.get(index)[0].equals("B")) { events.add(rows.get(index++)); }
            String[] start = events.get(0);
            long finalPublication = Long.parseLong(events.get(events.size() - 1)[1]);
            for (long cadence : new long[] {8_333, 20_000, 33_333})
            {
                Fixture f = new Fixture(Integer.parseInt(start[4]), Integer.parseInt(start[5]), 6848, 6592);
                try
                {
                    settings(f);
                    for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
                    for (String[] map : maps)
                    {
                        String[] values = map[2].split(",");
                        for (int y = 0; y < values.length; ++y) { f.flags[Integer.parseInt(map[1])][50 + y] = Integer.parseUnsignedInt(values[y],16); }
                    }
                    TreeSet<Long> times = new TreeSet<>();
                    for (long t = 0; t <= finalPublication + 2_000_000; t += cadence) { times.add(t); }
                    for (String[] event : events) { times.add(Long.parseLong(event[1])); }
                    int next = 0;
                    long previousTime = 0, firstNewAuthority = -1, stopped = 0;
                    LocalPoint previous = f.start, clickedAt = null, clickedAuthority = null;
                    for (long t : times)
                    {
                        at(f,t);
                        while (next < events.size() && Long.parseLong(events.get(next)[1]) <= t)
                        {
                            String[] event = events.get(next++);
                            if (event[0].equals("C"))
                            {
                                if (event[3].equals("WALK")) { f.controller.walkClick(); }
                                else if (event[3].equals("MINIMAP")) { f.destination = point(event,6); f.controller.walkClick(null); }
                                else
                                {
                                    clickedAt = f.controller.position();
                                    clickedAuthority = f.authority;
                                    assertTrue("recorded warm Walk fraction: " + marker, MovementPath.distance(clickedAt,point(event,4)) <= 20);
                                    click(f,MenuAction.WIDGET_TARGET_ON_NPC);
                                }
                            }
                            else
                            {
                                LocalPoint authority = point(event,2);
                                if (clickedAt != null && firstNewAuthority < 0 && !authority.equals(f.authority)) { firstNewAuthority = t; }
                                f.authority = authority;
                                f.destination = Integer.parseInt(event[4]) < 0 ? null : point(event,4);
                                f.nativePoint = point(event,6); f.animation = Integer.parseInt(event[8]);
                            }
                        }
                        int angle = f.controller.orientation(); f.controller.update();
                        LocalPoint actual = f.controller.position();
                        assertTrue("single movement budget", MovementPath.distance(previous,actual) <= Math.ceil((t-previousTime)*0.000472*1.15)+1);
                        assertTrue("single turn budget", Math.abs(MotionMath.difference(angle,f.controller.orientation())) <= Math.ceil((t-previousTime)*30/16_667.0)+1);
                        if (clickedAt != null && firstNewAuthority < 0)
                        {
                            assertTrue("no onward obsolete Walk before native evidence: " + marker,
                                actual.getX() >= Math.min(clickedAt.getX(),clickedAuthority.getX())-1 &&
                                    actual.getX() <= Math.max(clickedAt.getX(),clickedAuthority.getX())+1);
                        }
                        if (firstNewAuthority >= 0 && t > firstNewAuthority + cadence && t < finalPublication)
                        {
                            stopped = actual.equals(previous) ? stopped+t-previousTime : 0;
                            assertTrue("moving Use intermediate stop: " + marker + " duration=" + stopped, stopped < 40_000);
                        }
                        previous = actual; previousTime = t;
                    }
                    assertEquals(f.authority,f.controller.position());
                }
                finally { f.controller.close(); assertTrue(f.objects.isEmpty()); }
            }
        }
    }

    @Test
    public void everyNativeInputSharesConfirmedContinuityWithAndWithoutFlags()
    {
        for (MenuAction action : MenuAction.values())
        {
            if (!ResponsiveMovementPlugin.worldInteraction(action)) { continue; }
            for (boolean run : new boolean[] {false, true})
            {
                for (boolean flag : new boolean[] {false, true})
                {
                    Fixture f = new Fixture(6848, 7488, 6976, 8256);
                    try
                    {
                        settings(f); f.starts = false; f.run = run;
                        click(f, action);
                        f.frame(120);
                        LocalPoint previous = f.controller.position();
                        for (int ms = 140; ms < 1320; ms += 20)
                        {
                            f.authority = p(6848, 7488 + (run ? 256 : 128) * (ms < 720 ? 1 : 2));
                            f.destination = flag ? p(6976, 8128) : null;
                            f.frame(ms);
                            assertTrue(action + " flag=" + flag + " stopped at " + ms, f.controller.position().getY() > previous.getY());
                            assertTrue(MovementPath.distance(previous, f.controller.position()) <= 11);
                            previous = f.controller.position();
                        }
                        f.destination = null; f.nativePoint = f.authority;
                        for (int ms = 1320; ms <= 2200; ms += 20) { f.frame(ms); }
                        assertEquals(f.authority, f.controller.position());
                    }
                    finally { f.controller.close(); }
                }
            }
        }
    }

    @Test
    public void useRetiresAnOldWalkForecastEvenWhenNoNativeFlagArrives()
    {
        for (MenuAction action : new MenuAction[] {MenuAction.WIDGET_TARGET_ON_NPC, MenuAction.WIDGET_TARGET_ON_PLAYER})
        {
            Fixture f = new Fixture(6848, 7488, 6848, 6592);
            try
            {
                settings(f); f.controller.walkClick(); f.destination = p(8512, 6976);
                for (int ms = 0; ms <= 200; ms += 20) { f.frame(ms); }
                LocalPoint clickedAt = f.controller.position();
                f.controller.worldInteraction(f.event(action, "Use")); f.destination = null;
                for (int ms = 220; ms <= 400; ms += 20)
                {
                    f.frame(ms);
                    assertTrue("obsolete Walk cannot continue after Use", f.controller.position().getX() <= clickedAt.getX());
                }
                f.authority = p(6848, 7232);
                for (int ms = 420; ms <= 2200; ms += 20) { f.frame(ms); }
                assertEquals(f.authority, f.controller.position());
            }
            finally { f.controller.close(); }
        }
    }

    @Test
    public void nativeActorAndWidgetRefinementsShareTheCheckedJoin()
    {
        for (MenuAction action : NATIVE_ACTIONS)
        {
            Fixture f = new Fixture(6848, 7488, 6976, 8256);
            try
            {
                settings(f); click(f, action);
                f.destination = p(6848, 8256); f.frame(0);
                for (int ms = 20; ms <= 400; ms += 20) { f.frame(ms); }
                f.authority = p(6848, 7744); f.destination = p(6976, 8128);
                for (int ms = 420; ms <= 2400; ms += 20)
                {
                    if (ms == 1020) { f.authority = p(6976, 8000); }
                    if (ms == 1620) { f.authority = p(6976, 8128); f.destination = null; }
                    LocalPoint before = f.controller.position(); f.frame(ms);
                    assertTrue("one checked budget", MovementPath.distance(before, f.controller.position()) <= 11);
                    assertTrue("refined native goal, not obsolete tail: " + action, f.controller.position().getY() <= 8128);
                }
                assertEquals(f.authority, f.controller.position());
            }
            finally { f.controller.close(); }
        }
    }

    @Test
    public void castAndUseRespectNativeRangeAndPrimaryActionTimingForBothActorTypes()
    {
        for (MenuAction action : new MenuAction[] {MenuAction.WIDGET_TARGET_ON_NPC,MenuAction.WIDGET_TARGET_ON_PLAYER})
        {
            for (String option : new String[] {"Use","Cast"})
            {
                Fixture f = new Fixture(6848,7488,6976,8256);
                try
                {
                    settings(f); f.controller.worldInteraction(f.event(action,option));
                    for (int ms = 0; ms <= 580; ms += 20)
                    {
                        f.frame(ms);
                        if (option.equals("Cast")) { assertEquals("within the conservative spell reserve",f.start,f.controller.position()); }
                        else { assertTrue("Use is a bounded visual forecast, not proven reach",MovementPath.distance(f.start,f.controller.position())<=512); }
                    }
                    // A ranged interaction can stop after one step, well before
                    // adjacency. Only the native endpoint establishes that stop.
                    f.authority = p(6848,7616); f.destination = p(6848,7616);
                    for (int ms = 600; ms <= 1100; ms += 20)
                    {
                        LocalPoint before=f.controller.position(); f.frame(ms);
                        assertTrue("native range disagreement reconciles without a snap",MovementPath.distance(before,f.controller.position())<=11);
                        if(option.equals("Cast")) { assertTrue("a stationary-range Cast does not overshoot",f.controller.position().getY()<=7616); }
                    }
                    f.animation = 711; f.poses.put("AnimationFrame",7); f.destination = null;
                    for (int ms = 1120; ms <= 2000; ms += 20)
                    {
                        f.frame(ms);
                        assertEquals("native primary ID stays untouched",711,f.animation);
                        assertEquals("native primary frame stays untouched",Integer.valueOf(7),f.poses.get("AnimationFrame"));
                        assertTrue("no approach to a guessed adjacent tile during the native action",MovementPath.distance(f.authority,f.controller.position())<=128);
                    }
                    assertEquals(f.authority,f.controller.position());
                }
                finally { f.controller.close(); }
            }
        }
    }

    @Test
    public void repeatedTargetedInputCannotRenewAnUnconfirmedRouteFromAnUnchangedFlag()
    {
        for (MenuAction action : new MenuAction[] {MenuAction.ITEM_USE_ON_NPC,MenuAction.WIDGET_TARGET_ON_NPC,
            MenuAction.ITEM_USE_ON_PLAYER,MenuAction.WIDGET_TARGET_ON_PLAYER})
        {
            Fixture f = new Fixture(6848,7488,6976,8256);
            try
            {
                settings(f); click(f,action); f.destination = p(6848,8256);
                for (int ms = 0; ms <= 3000; ms += 20)
                {
                    if (ms > 0 && ms <= 800 && ms % 100 == 0) { f.at(ms); click(f,action); }
                    f.frame(ms);
                    assertTrue("unchanged flag cannot grant unchecked new geometry",f.controller.position().getY() <= 8256);
                }
                assertEquals("repeated targeted actions do not renew hard prediction without authority",f.start,f.controller.position());
                f.frame(3020); assertEquals(f.start,f.controller.position());
            }
            finally { f.controller.close(); }
        }
    }

    @Test
    public void nativeEvidenceGuardsMissingFlagsExpiredClicksAndUnchangedAuthority()
    {
        for (int guard = 0; guard < 6; ++guard)
        {
            List<MovementTrace.Entry> entries = new ArrayList<>();
            Fixture f = new Fixture(6848, 7488, 6976, 8256, entries);
            try
            {
                settings(f);
                if (guard == 1) { f.destination = p(6976, 8128); }
                if (guard == 2) { f.control = true; }
                if (guard == 3) { f.starts = false; }
                f.controller.worldInteraction(f.event(MenuAction.WIDGET_TARGET_ON_NPC,"Cast"));
                for (int ms = 0; ms <= 200; ms += 20) { f.frame(ms); }
                assertEquals(f.start, f.controller.position());
                if (guard != 0) { f.destination = p(6976, 8128); }
                for (int ms = 220; ms <= 1980; ms += 20)
                {
                    if (guard == 4 && ms == 600) { f.animation = 422; }
                    f.frame(ms);
                    assertEquals("a late flag without a checked server step is not a new click", f.start, f.controller.position());
                }
                f.animation = -1;
                f.authority = p(6848, 7744);
                for (int ms = 2000; ms <= 3200; ms += 20) { f.frame(ms); }
                assertTrue("expired/ineligible evidence cannot forecast past real authority", f.controller.position().getY() <= 7744);
            }
            finally { f.controller.close(); }
            assertFalse("no late forecast can revive these clicks", entries.stream().anyMatch(entry ->
                entry instanceof MovementTrace.Sample && ((MovementTrace.Sample) entry).startDecision.equals("observed-native-approach-authority")));
        }
    }

    @Test
    public void lateNativeEvidenceIsCheckedOnceAndRepeatedPreparationSpendsNoExtraTime()
    {
        for (MenuAction action : NATIVE_ACTIONS)
        {
            List<MovementTrace.Entry> entries = new ArrayList<>();
            Fixture f = new Fixture(6848, 7488, 6976, 8256, entries);
            try
            {
                settings(f);
                f.controller.worldInteraction(action.name().startsWith("PLAYER_") || action.name().endsWith("_ON_NPC") || action.name().endsWith("_ON_PLAYER")
                    ? f.event(action,"Cast") : f.sceneEvent(action,"Use",233,54,64));
                for (int ms = 0; ms <= 580; ms += 20) { f.frame(ms); }
                f.authority = p(6848, 7744); f.destination = p(6976, 8128); f.frame(600);
                LocalPoint before = f.controller.position();
                int angle = f.controller.orientation();
                f.controller.update(); f.controller.update();
                assertEquals(before, f.controller.position()); assertEquals(angle, f.controller.orientation());
                for (int ms = 620; ms <= 3100; ms += 20)
                {
                    f.frame(ms);
                    assertTrue("unchanged authority cannot renew a late preview", f.controller.position().getY() <= 8128);
                }
                assertEquals("rejected forecast reconciles to real authority", f.authority, f.controller.position());
            }
            finally { f.controller.close(); }
            assertTrue("late native evidence enters the shared route: " + action, entries.stream().anyMatch(entry ->
                entry instanceof MovementTrace.Sample && ((MovementTrace.Sample) entry).startDecision.equals("observed-native-approach-authority")));
        }
    }

    @Test
    public void blockedLateNativeDestinationsAndConfirmedCornersKeepTheirProofs()
    {
        for (boolean blocked : new boolean[] {false, true})
        {
            Fixture f = new Fixture(6848, 7488, 6976, 8256);
            try
            {
                settings(f); f.controller.worldInteraction(f.event(MenuAction.WIDGET_TARGET_ON_NPC,"Cast"));
                f.frame(0); f.frame(120);
                if (blocked)
                {
                    for (int x = 0; x < 104; ++x) { f.flags[x][62] = CollisionDataFlag.BLOCK_MOVEMENT_FULL; }
                }
                f.authority = p(7104, 7488); f.destination = p(7104, 8256);
                for (int ms = 140; ms <= 2800; ms += 20)
                {
                    LocalPoint before = f.controller.position(); f.frame(ms);
                    assertTrue(MovementPath.distance(before, f.controller.position()) <= 11);
                    if (ms < 580) { assertEquals("finish the real eastbound prefix before turning north", 7488, f.controller.position().getY()); }
                    if (blocked) { assertTrue("native flags cannot cross a blocked boundary", f.controller.position().getY() < 62 * 128 + 64); }
                }
                assertEquals("no real onward steps: recover within the retained deadline", f.authority, f.controller.position());
            }
            finally { f.controller.close(); }
        }
    }

    @Test
    public void actorTargetInvalidationAndReplacementClearItsNativePublicationEvidence()
    {
        for (boolean player : new boolean[] {false, true})
        {
            for (int invalidation = 0; invalidation < 8; ++invalidation)
            {
                Fixture f = new Fixture(6848, 7488, 6976, 8256);
                try
                {
                    settings(f); click(f, player ? MenuAction.ITEM_USE_ON_PLAYER : MenuAction.ITEM_USE_ON_NPC);
                    f.destination = p(6848, 8256); f.frame(0); f.frame(100);
                    assertTrue(f.controller.position().getY() > 7488);
                    if (invalidation == 0) { f.present = false; }
                    if (invalidation == 1) { f.npcDead = true; }
                    if (invalidation == 2) { f.animation = 422; }
                    if (invalidation == 3) { f.spot = true; }
                    if (invalidation == 4) { f.controller.sceneChanged(false); }
                    if (invalidation == 5)
                    {
                        f.controller.walkClick(); f.destination = null;
                    }
                    if (invalidation == 6)
                    {
                        f.area = new net.runelite.api.coords.WorldArea(3254,3264,1,1,1);
                    }
                    if (invalidation == 7)
                    {
                        if (!player) { ++f.npcId; }
                        else { f.present = false; }
                    }
                    for (int ms = 120; ms <= 2000; ms += 20) { f.frame(ms); }
                    assertEquals("stale actor input must recover: " + invalidation, f.authority, f.controller.position());
                    f.present = true; f.npcDead = false; f.animation = -1; f.spot = false;
                    f.destination = p(6976, 8128);
                    f.frame(2020); f.frame(2040);
                    assertEquals("restored actor/flag alone cannot rearm the retired click", f.authority, f.controller.position());
                }
                finally { f.controller.close(); assertTrue(f.objects.isEmpty()); }
            }
        }
    }

    @Test
    public void movingTargetUsesOnlyItsChangedNativeDestinationNotItsFootprint()
    {
        for (boolean player : new boolean[] {false, true})
        {
            Fixture f = new Fixture(6848, 7488, 6976, 8256);
            try
            {
                settings(f); click(f, player ? MenuAction.WIDGET_TARGET_ON_PLAYER : MenuAction.WIDGET_TARGET_ON_NPC);
                f.destination = p(6848, 8256); f.frame(0); f.frame(100);
                f.area = new net.runelite.api.coords.WorldArea(3257, 3264, 1, 1, 0);
                LocalPoint before = f.controller.position();
                f.frame(120);
                assertEquals("target motion supplies no new route", before.getX(), f.controller.position().getX());
                f.destination = p(6976, 8128); f.frame(140);
                for (int ms = 160; ms <= 3200; ms += 20)
                {
                    if (ms == 400) { f.authority = p(6848, 7744); }
                    if (ms == 1000) { f.authority = p(6976, 8000); }
                    if (ms == 1600) { f.authority = p(6976, 8128); f.destination = null; }
                    before = f.controller.position(); f.frame(ms);
                    assertTrue(MovementPath.distance(before, f.controller.position()) <= 11);
                    assertTrue(f.controller.position().getY() <= 8128);
                }
                assertEquals(f.authority, f.controller.position());
            }
            finally { f.controller.close(); }
        }
    }

    @Test
    public void continuousNativeDebtCoversEverySpeedWalkRunPacingStrengthAndCadence()
    {
        for (int speed = 5; speed <= 100; ++speed)
        {
            for (boolean run : new boolean[] {false, true})
            {
                for (int strength : new int[] {0, 15, 50})
                {
                    for (int cadence : new int[] {8, 20, 33, 100})
                    {
                        double rate = speed / 50.0 * (run ? 0.4 : 0.2);
                        MovementPath path = MovementPath.idle(p(6848, 7488), 0, speed / 50.0, (a, b) -> true);
                        int authorityY = 7488;
                        long previousTime = 0;
                        LocalPoint before = path.position();
                        for (int tick = 0; tick < 4; ++tick)
                        {
                            authorityY += run ? 256 : 128;
                            assertTrue(path.accept(p(6848, authorityY), run));
                            for (long ms = tick * 600L + cadence; ms < (tick + 1) * 600L; ms += cadence)
                            {
                                path.advance(ms * 1_000_000L, true, strength, strength);
                                assertTrue("no intermediate native stop", path.moving());
                                assertTrue("native-only pacing cannot lead authority", path.position().getY() <= authorityY);
                                assertTrue("same movement budget", MovementPath.distance(before, path.position()) <=
                                    Math.ceil((ms - previousTime) * rate * (1 + strength / 100.0)) + 1);
                                assertTrue(path.clear()); before = path.position(); previousTime = ms;
                            }
                            path.advance((tick + 1) * 600_000_000L, true, strength, strength);
                            before = path.position(); previousTime = (tick + 1) * 600L;
                        }
                        // Very low speed legitimately accumulates confirmed travel.
                        for (long ms = 2500; ms < 70_000 && !path.finished(); ms += 100)
                        {
                            path.advance(ms * 1_000_000L, false, strength, strength);
                        }
                        assertTrue("finite exact arrival after native withdrawal", path.finished());
                        assertEquals(p(6848, authorityY), path.position());
                    }
                }
            }
        }
    }

    @Test
    public void liveNativeInputSettingsChangesKeepPositionBudgetsAndExactArrival()
    {
        for (MenuAction action : NATIVE_ACTIONS)
        {
            for (boolean run : new boolean[] {false,true})
            {
                for (int cadence : new int[] {8,20,33,100})
                {
                    for (boolean faster : new boolean[] {false,true})
                    {
                        Fixture f = new Fixture(6848,7488,6976,8256);
                        try
                        {
                            settings(f); f.run = run; click(f,action);
                            int step = run ? 256 : 128;
                            for (int ms = 0; ms <= 1000; ms += cadence)
                            {
                                if (ms >= 300) { f.authority = p(6848,7488+step); f.destination = p(6848,7488+step*3); }
                                if (ms >= 900) { f.authority = p(6848,7488+step*2); }
                                f.frame(ms);
                            }
                            f.at(1000); f.controller.update();
                            LocalPoint atChange = f.controller.position();
                            f.speed = faster ? 2 : 0.1;
                            f.catchUpPercent = faster ? 50 : 0;
                            f.slowAhead = !faster; f.slowAheadPercent = 50;
                            f.slowArrival = !f.slowArrival; f.arrivalFirstTiles = 2; f.arrivalFinalSpeed = 1;
                            f.controller.update();
                            assertEquals("settings never reset/advance position at same timestamp",atChange,f.controller.position());
                            LocalPoint before = atChange;
                            for (int ms = 1100; ms <= 4000; ms += 100)
                            {
                                if (ms == 1500) { f.authority = p(6848,7488+step*3); f.destination = null; }
                                f.frame(ms);
                                assertTrue("new setting still spends one movement budget",MovementPath.distance(before,f.controller.position()) <= Math.ceil(100*(run?0.4:0.2)*f.speed*1.5)+1);
                                assertTrue("no invented onward travel",f.controller.position().getY() <= 7488+step*3);
                                before = f.controller.position();
                            }
                            // Slow pace can owe real confirmed travel; drain it normally.
                            for (int ms = 4100; ms <= 50_000; ms += 100) { f.frame(ms); }
                            assertEquals("settle and hold exact native arrival",f.authority,f.controller.position());
                        }
                        finally { f.controller.close(); }
                    }
                }
            }
        }
    }

    private static void settings(Fixture f)
    {
        f.speed = 1.18; f.smoothing = 0; f.originalWhenAligned = false;
        f.catchUp = true; f.catchUpPercent = 15; f.slowAhead = true; f.slowAheadPercent = 10;
        f.slowArrival = true; f.arrivalFinalSpeed = 49.8;
    }
    private static void click(Fixture f, MenuAction action)
    {
        String name = action.name();
        f.controller.worldInteraction(name.startsWith("NPC_") || name.startsWith("PLAYER_") || name.endsWith("_ON_PLAYER") || name.endsWith("_ON_NPC")
            ? f.event(action, "Use") : f.sceneEvent(action, "Use", 233, 54, 64));
    }
    private static LocalPoint point(String[] fields, int offset) { return p(Integer.parseInt(fields[offset]), Integer.parseInt(fields[offset + 1])); }
    private static void at(Fixture f, long micros) { f.now = 1_000_000_000L + micros * 1000; f.cycle = 100 + (int) (micros / 20_000); }
}
