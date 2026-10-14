package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Session 1791904169808: publication timing/coordinates, with explicit scene doubles. */
public class ObjectPublicationPacingTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }

    @Test
    public void confirmedPairsSpendTheWholeTickAtSteadyPaceAcrossSettingsAndCadences()
    {
        // #534/#1968: pairs exhausted ~100 ms before the next publication.
        // #17702: after disagreement, the former tail almost stopped instead.
        for (int cadence : new int[] {8, 20, 33, 100})
        {
            for (int boost : new int[] {0, 15, 50})
            {
                MovementPath path = MovementPath.idle(p(4800, 5184), 0, 1.18, (a, b) -> true);
                for (int tick = 0; tick < 4; ++tick)
                {
                    int from = tick * 600;
                    assertTrue(path.accept(p(5056 + tick * 256, 5184), true));
                    LocalPoint before = path.position();
                    int previousTime = from;
                    for (int ms = from + cadence; ms < from + 600; ms += cadence)
                    {
                        path.advance(ms * 1_000_000L, true, boost, 13, p(6208, 5184), ArrivalPacing.OFF);
                        int travel = path.position().getX() - before.getX();
                        assertTrue("no end-of-tick brake", travel >= Math.floor((ms - previousTime) * 0.38));
                        assertTrue("no catch-up burst", travel <= Math.ceil((ms - previousTime) * 0.46));
                        assertTrue(path.position().getX() <= path.confirmed().getX());
                        assertTrue(path.clear());
                        before = path.position(); previousTime = ms;
                    }
                    path.advance((from + 600) * 1_000_000L, true, boost);
                }
                for (int ms = 2420; ms <= 3400; ms += 20) { path.advance(ms * 1_000_000L, true, boost); }
                assertTrue("unchanged destination cannot hold a stale route", path.finished());
            }
        }
    }

    @Test
    public void objectFallbackSharesConfirmedPacingWithoutAUsablePreview()
    {
        List<MovementTrace.Entry> trace = new ArrayList<>();
        Fixture f = new Fixture(4800, 5184, 8000, 8000, trace);
        try
        {
            settings(f);
            f.starts = false;
            f.wall(1804, 43, 42);
            // Explicitly disable prediction to exercise the confirmed fallback
            // independently of which reachable perimeter the planner chooses.
            f.flags[42][42] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
            f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Open", 1804, 43, 42));
            f.destination = p(5568, 5440); f.frame(0);
            LocalPoint before = f.controller.position();
            for (int ms = 20; ms <= 1180; ms += 20)
            {
                f.authority = ms < 620 ? p(5056, 5184) : p(5312, 5184);
                f.destination = p(5568, 5312);
                f.frame(ms);
                int step = f.controller.position().getX() - before.getX();
                assertTrue("no idle between confirmed pairs at " + ms, step >= 7);
                assertTrue(step <= 10);
                before = f.controller.position();
            }
        }
        finally { f.controller.close(); }
    }

    @Test
    public void withdrawnObjectFlagCannotKeepAnUnconfirmedWrongSideForecast()
    {
        // #28364: native reached the east side while a north-side preview
        // continued, waited, expired, and travelled back before walking south.
        Fixture f = new Fixture(10432, 6080, 8000, 8000);
        try
        {
            settings(f); tree(f);
            f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Chop-down", 12165, 78, 41));
            f.destination = p(10048, 5312); f.frame(0); f.frame(20);
            LocalPoint before = f.controller.position();
            f.authority = p(10304, 5824); f.destination = null;
            for (int ms = 40; ms <= 1200; ms += 20)
            {
                f.frame(ms);
                LocalPoint at = f.controller.position();
                assertTrue("withdrawn forecast cannot cross to the north side", at.getX() >= 10304);
                assertTrue("single movement budget", MovementPath.distance(before, at) <= 11);
                before = at;
            }
            assertEquals(f.authority, f.controller.position());
        }
        finally { f.controller.close(); }
    }

    @Test
    public void repeatedSingleTileObjectUpdatesWalkSmoothlyEvenWithRunEnabledAndNoFlag()
    {
        // #24913/#28364: 10304,5696 -> 5568 -> 5440 -> 5312, one
        // tile every ~600 ms, destination absent and run orb still enabled.
        Fixture f = new Fixture(10304, 5952, 8000, 8000);
        try
        {
            settings(f); tree(f); f.starts = false;
            f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Chop-down", 12165, 78, 41));
            f.authority = p(10304, 5696);
            for (int ms = 20; ms <= 600; ms += 20) { f.frame(ms); }
            LocalPoint before = f.controller.position();
            for (int ms = 620; ms <= 2380; ms += 20)
            {
                int step = Math.min(3, 1 + (ms - 620) / 600);
                f.authority = p(10304, 5696 - step * 128);
                f.frame(ms);
                int travel = before.getY() - f.controller.position().getY();
                assertTrue("one-tile publications must not sprint then idle at " + ms, travel >= 3 && travel <= 6);
                before = f.controller.position();
            }
            f.nativePoint = f.authority;
            for (int ms = 2400; ms <= 3000; ms += 20) { f.frame(ms); }
            assertEquals(f.authority, f.controller.position());
        }
        finally { f.controller.close(); }
    }

    private static void settings(Fixture f)
    {
        f.speed = 1.18; f.smoothing = 0; f.catchUp = true; f.catchUpPercent = 15;
        f.slowAhead = true; f.slowAheadPercent = 13; f.slowArrival = true;
        f.arrivalFinalSpeed = 49.8; f.originalWhenAligned = false;
    }

    private static void tree(Fixture f)
    {
        f.object(12165, p(10048, 5312), p(10176, 5824));
        for (int x = 78; x <= 79; ++x)
        {
            for (int y = 41; y <= 45; ++y) { f.flags[x][y] = CollisionDataFlag.BLOCK_MOVEMENT_OBJECT; }
        }
    }
}
