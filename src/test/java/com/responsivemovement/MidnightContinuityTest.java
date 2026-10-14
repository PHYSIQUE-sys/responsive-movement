package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.TreeSet;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Recorded 2026-10-04 +10:00 click/authority timings; no game input or native renderer. */
public class MidnightContinuityTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }
    private static void at(Fixture f, long micros)
    {
        f.now = 1_000_000_000L + micros * 1000;
        f.cycle = 100 + (int) (micros / 20_000);
    }

    @Test
    public void recordedTakeRepeatsKeepTheOriginalCheckedForecast() throws Exception
    {
        // Session 1791036237103, 00:04:01.281..03.441. Pickup ID=233,
        // scene tile=(39,31). Native publication and endpoint times are recorded;
        // collision is an explicit open-map double for this identity/timing test.
        long[] clicks = {4_178_412, 4_337_269, 4_537_475, 4_738_888, 4_937_273, 5_115_566, 5_277_799};
        long[] ticks = {4_537_710, 5_154_886, 5_756_452, 6_338_857};
        LocalPoint[] authority = {p(5696, 3904), p(5440, 3904), p(5184, 3904), p(5056, 4032)};
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            Fixture single = new Fixture(5952, 3904, 7000, 7000), repeated = new Fixture(5952, 3904, 7000, 7000);
            single.speed = repeated.speed = 1.2;
            TreeSet<Long> times = timeline(clicks[0] - 20_000, 7_500_000, cadence, clicks, ticks);
            int click = 0, tick = 0;
            LocalPoint previous = repeated.start;
            for (long t : times)
            {
                at(single, t); at(repeated, t);
                if (click < clicks.length && t == clicks[click])
                {
                    if (click == 0) { take(single, 233, 39, 31); }
                    take(repeated, 233, 39, 31);
                    single.destination = repeated.destination = p(5056, 4032);
                    ++click;
                }
                if (tick < ticks.length && t == ticks[tick])
                {
                    single.authority = repeated.authority = authority[tick++];
                    if (tick == ticks.length) { single.destination = repeated.destination = null; }
                }
                single.controller.update(); repeated.controller.update();
                assertEquals("repeat must not retire/restart the original itinerary at " + t,
                    single.controller.position(), repeated.controller.position());
                assertTrue("no pickup backstep", repeated.controller.position().getX() <= previous.getX());
                previous = repeated.controller.position();
            }
            assertEquals(authority[3], repeated.controller.position());
            single.controller.close(); repeated.controller.close();
        }
    }

    @Test
    public void worstYewClickJoinsTheNewApproachInsteadOfFinishingTheOldWalk() throws Exception
    {
        // Session 1791036237103, click 1999 -> 2043; real footprint/map and
        // native endpoint/flag times. Native sub-tile samples are held between
        // the recorded points below, not represented as live native animation.
        long[] changes = {39_297_511, 39_377_226, 39_940_640, 40_157_668, 40_157_910,
            40_555_979, 41_154_836, 41_755_266, 42_656_966};
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            Fixture f = yew(5568, 3904);
            f.destination = p(5440, 3520);
            int event = 0, reverse = 0;
            LocalPoint previous = f.start;
            long previousTime = changes[0] - 20_000;
            for (long t : timeline(previousTime, 43_000_000, cadence, changes))
            {
                at(f, t);
                while (event < changes.length && changes[event] == t)
                {
                    switch (event++)
                    {
                        case 0: f.controller.walkClick(); break;
                        case 1: f.destination = p(5056, 4032); break;
                        case 2: f.authority = p(5312, 3904); f.nativePoint = p(5568, 3904); break;
                        case 3: f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Chop down", 10822, 33, 27)); break;
                        case 4: f.destination = p(4288, 3520); f.nativePoint = p(5524, 3904); break;
                        case 5: f.authority = p(5056, 3904); f.destination = p(4544, 3904); f.nativePoint = p(5380, 3904); break;
                        case 6: f.authority = p(4800, 3904); f.nativePoint = p(5141, 3904); break;
                        case 7: f.authority = p(4544, 3904); f.destination = null; f.nativePoint = p(4901, 3904); break;
                        case 8: f.nativePoint = f.authority; break;
                        default: throw new AssertionError();
                    }
                }
                f.controller.update();
                LocalPoint actual = f.controller.position();
                if (t >= changes[3])
                {
                    reverse += Math.max(0, actual.getX() - previous.getX());
                    assertTrue("no old-row timeout retrace at " + t + ": " + reverse, reverse <= 8);
                    assertTrue("stay in the proven approach corridor", actual.getY() >= 3776 && actual.getY() <= 4032);
                    assertTrue("one movement clock", MovementPath.distance(actual, previous) <= Math.ceil((t - previousTime) * 0.00048) + 1);
                }
                previous = actual; previousTime = t;
            }
            assertEquals(p(4544, 3904), f.controller.position());
            f.controller.close(); assertTrue(f.objects.isEmpty());
        }
    }

    @Test
    public void precedingYewClickLeavesTheNorthboundWalkAndAcceptsTheLateSouthernGoal() throws Exception
    {
        // Same session: tree 1457 (00:04:25.741), Walk 1511, then tree
        // 1537 (00:04:27.298). This older approach stopped at 4800 until 28.059.
        long[] changes = {28_638_067, 28_641_854, 29_154_957, 29_694_767, 29_717_940,
            29_756_460, 30_195_747, 30_195_984, 30_338_763, 30_956_681};
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            Fixture f = yew(5568, 3904);
            int event = 0;
            long stopped = 0, previousTime = changes[0] - 20_000;
            LocalPoint previous = f.start;
            for (long t : timeline(previousTime, 32_000_000, cadence, changes))
            {
                at(f, t);
                while (event < changes.length && changes[event] == t)
                {
                    switch (event++)
                    {
                        case 0: chop(f); break;
                        case 1: f.destination = p(4288, 3520); break;
                        case 2: f.authority = p(5312, 3904); f.destination = p(4544, 3904); break;
                        case 3: f.controller.walkClick(); break;
                        case 4: f.destination = p(4928, 4032); break;
                        case 5: f.authority = p(5056, 3904); break;
                        case 6: chop(f); break;
                        case 7: f.destination = p(4288, 3520); break;
                        case 8: f.authority = p(4800, 3904); f.destination = p(4544, 3904); break;
                        case 9: f.authority = p(4544, 3904); f.destination = null; break;
                        default: throw new AssertionError();
                    }
                }
                f.controller.update();
                LocalPoint actual = f.controller.position();
                if (t > changes[7] && actual.getX() > 4544)
                {
                    stopped = actual.equals(previous) ? stopped + t - previousTime : 0;
                    assertTrue("no intermediate wait on the old Walk endpoint", stopped <= 60_000);
                    assertTrue("no backward construction-anchor join", actual.getX() <= previous.getX() + 1);
                    assertTrue("one movement clock", MovementPath.distance(actual, previous) <= Math.ceil((t - previousTime) * 0.00048) + 1);
                }
                previous = actual; previousTime = t;
            }
            assertEquals(p(4544, 3904), f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void recordedConfirmedRunKeepsPositionalReserveBetweenServerSteps()
    {
        // Session 1791036292200, 00:05:05.881..08.860. The original body
        // finishes each pair ~533 ms after publication, leaving 20..100-ms
        // stationary gaps even though MovementGait retains its running pose.
        long[] ticks = {13_681_663, 14_301_308, 14_860_745, 15_460_576, 16_099_261, 16_660_069};
        LocalPoint[] authority = {p(4800, 3904), p(5056, 3904), p(5312, 3904), p(5568, 3904), p(5824, 3904), p(6080, 3648)};
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            Fixture f = new Fixture(4544, 3904, 7000, 7000);
            f.speed = 1.2;
            f.destination = p(6720, 3136); // Native destination; no synthetic click.
            int tick = 0;
            long stopped = 0, previousTime = ticks[0] - 20_000;
            LocalPoint previous = f.start;
            for (long t : timeline(previousTime, ticks[5] - 1, cadence, ticks))
            {
                at(f, t);
                if (tick < ticks.length && t == ticks[tick])
                {
                    f.nativePoint = tick == 0 ? f.start : authority[tick - 1];
                    f.authority = authority[tick++];
                }
                f.controller.update();
                LocalPoint actual = f.controller.position();
                if (tick > 0 && t > ticks[0])
                {
                    stopped = actual.equals(previous) ? stopped + t - previousTime : 0;
                    assertTrue("no positional stop between run pairs at " + t + ": " + stopped + "us", stopped <= cadence);
                    assertTrue("do not forecast another tile", actual.getX() <= f.authority.getX());
                    assertTrue("keep the actual row", actual.getY() == 3904);
                    assertTrue("one pacing budget", MovementPath.distance(actual, previous) <= Math.ceil((t - previousTime) * 0.00048) + 1);
                }
                previous = actual; previousTime = t;
            }
            f.destination = null; f.nativePoint = f.authority;
            for (long t = ticks[5]; t < ticks[5] + 1_000_000; t += cadence) { at(f, t); f.controller.update(); }
            assertEquals("real arrival completes normally", f.authority, f.controller.position());
            f.controller.close();
        }
    }

    static Fixture yew(int x, int y) throws Exception
    {
        Fixture f = new Fixture(x, y, 7000, 7000);
        // Isolate the recorded geometric handoffs from the now-shared delay.
        f.speed = 1.2; f.smoothing = 0;
        for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            MidnightContinuityTest.class.getResourceAsStream("midnight-yew-map.txt"), StandardCharsets.UTF_8)))
        {
            for (String line; (line = reader.readLine()) != null;)
            {
                if (line.startsWith("#")) { continue; }
                String[] fields = line.split(" ");
                int sx = Integer.parseInt(fields[0]), sy = 0;
                for (String token : fields[1].split(","))
                {
                    String[] rle = token.split(":");
                    int count = Integer.parseInt(rle[0]), flags = Integer.parseUnsignedInt(rle[1], 16);
                    for (int i = 0; i < count; ++i, ++sy) { if (sy >= 24 && sy <= 34) { f.flags[sx][sy] = flags; } }
                }
                assertEquals("complete recorded column", 104, sy);
            }
        }
        f.object(10822, p(4288, 3520), p(4544, 3776));
        return f;
    }

    static void take(Fixture f, int id, int x, int y)
    {
        f.controller.worldInteraction(f.sceneEvent(MenuAction.GROUND_ITEM_THIRD_OPTION, "Take", id, x, y));
    }

    private static void chop(Fixture f)
    {
        f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Chop down", 10822, 33, 27));
    }

    private static TreeSet<Long> timeline(long start, long end, long cadence, long[]... events)
    {
        TreeSet<Long> times = new TreeSet<>();
        for (long t = start; t <= end; t += cadence) { times.add(t); }
        for (long[] group : events) { for (long t : group) { if (t >= start && t <= end) { times.add(t); } } }
        times.add(end);
        return times;
    }
}
