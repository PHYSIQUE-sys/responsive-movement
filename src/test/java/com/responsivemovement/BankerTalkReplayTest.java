package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static com.responsivemovement.GeApproachReplayTest.tile;
import static org.junit.Assert.*;

/** Twelve banker Talk-to starts, including the slow final updates that exposed overshoot. */
@RunWith(Parameterized.class)
public class BankerTalkReplayTest
{
    @Parameterized.Parameters(name = "banker Talk-to {0}")
    public static Collection<Object[]> captures() throws Exception
    {
        return lines("banker-talk-1945.txt").stream().map(line -> new Object[] {line.split(" ")[0], line}).collect(Collectors.toList());
    }

    private final String capture;
    public BankerTalkReplayTest(String id, String capture) { this.capture = capture; }

    @Test
    public void followsBothRecordedRunPairsWithoutPredictingPastTheCounterStop() throws Exception
    {
        String[] values = capture.split(" ");
        LocalPoint start = tile(Integer.parseInt(values[1]), Integer.parseInt(values[2]));
        LocalPoint first = tile(47, start.getSceneY()), finalPoint = tile(49, 48);
        long published = Long.parseLong(values[3]), withdrawn = Long.parseLong(values[4]);
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            Fixture f = fixture(start);
            f.controller.worldInteraction(f.event(MenuAction.NPC_FIRST_OPTION, "Talk-to"));
            TreeSet<Long> times = new TreeSet<>();
            for (long t = 0; t <= withdrawn + 1_500_000; t += cadence) { times.add(t); }
            times.add(published); times.add(withdrawn);
            LocalPoint before = start;
            for (long t : times)
            {
                f.now = 1_000_000_000L + t * 1000; f.cycle = 100 + (int) (t / 20_000);
                f.authority = t >= withdrawn ? finalPoint : t >= published ? first : start;
                f.destination = t >= published && t < withdrawn ? tile(50, 49) : null;
                f.controller.update();
                LocalPoint actual = f.controller.position();
                assertTrue("never overshoot the actual run-pair stop at " + t, actual.getX() <= finalPoint.getX() && actual.getY() <= finalPoint.getY());
                assertTrue("no backward recovery at " + t, actual.getX() >= before.getX() && actual.getY() >= before.getY());
                // Geometry comes from the two recorded authoritative endpoints.
                // First pair is cardinal; second is cardinal or a proven knight.
                double distance = t * 0.00052;
                LocalPoint expected;
                if (distance <= 256)
                {
                    expected = new LocalPoint((int) Math.round(start.getX() + distance), start.getY(), 0);
                }
                else
                {
                    double fraction = Math.min(1, (distance - 256) / 256);
                    expected = new LocalPoint((int) Math.round(first.getX() + 256 * fraction),
                        (int) Math.round(first.getY() + (finalPoint.getY() - first.getY()) * fraction), 0);
                }
                assertTrue("constant captured run pace at " + t + " expected " + expected + " actual " + actual,
                    MovementPath.distance(expected, actual) <= 1);
                before = actual;
            }
            assertEquals(finalPoint, f.controller.position());
            f.controller.close();
        }
    }

    static Fixture fixture(LocalPoint start) throws Exception
    {
        Fixture f = new Fixture(start.getX(), start.getY(), tile(51, 49).getX(), tile(51, 49).getY());
        // Historical constant-rate geometry replay, with no requested delay.
        // Delay/tick-release behavior is covered separately for all input paths.
        f.speed = 1.3; f.smoothing = 0;
        f.npcActions = new String[] {"Talk-to", null, "Bank"};
        for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
        for (String line : lines("red-click-1820.txt"))
        {
            if (!line.startsWith("M ")) { continue; }
            String[] fields = line.split(" "), values = fields[2].split(",");
            for (int y = 0; y < values.length; ++y) { f.flags[Integer.parseInt(fields[1])][44 + y] = Integer.parseUnsignedInt(values[y], 16); }
        }
        return f;
    }

    private static List<String> lines(String name) throws Exception
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            BankerTalkReplayTest.class.getResourceAsStream(name), StandardCharsets.UTF_8)))
        {
            return reader.lines().filter(line -> !line.startsWith("#") && !line.isEmpty()).collect(Collectors.toList());
        }
    }
}
