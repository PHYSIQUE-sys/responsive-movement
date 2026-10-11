package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** 2026-10-12 bookmarked Walk departures; only captured geometry/timing is replayed. */
public class BookmarkWalkJoinTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }

    @Test
    public void nearKnightArrivalsKeepTheNewAuthorityRouteInsteadOfAnOldSideAnchor()
    {
        // Bookmarks 1/4/7/9/11: origin, preceding endpoint, new goal, approximate
        // captured fraction age at unadjusted pace. Collision is explicit open geometry.
        int[][] cases = {
            {6336,7104,6592,6976,6592,7360,486},
            {6464,7104,6720,7232,6720,6976,539},
            {6080,7104,6336,6976,6464,7488,520},
            {6720,7616,6976,7744,6976,7360,565},
            {6976,7488,6720,7616,6720,7232,503},
            {6976,7232,7104,7488,6848,7488,587}}; // Bookmark 8, preceding Walk
        for (int[] c : cases)
        {
            MovementPath path = confirmed(p(c[0],c[1]), p(c[2],c[3]), c[6], OPEN);
            LocalPoint before = path.position(), goal = p(c[4],c[5]);
            long hard = path.predictionDeadlineNanos();
            MovementPath next = path.retargetWalk(goal, true, c[6]*1_000_000L);
            assertNotNull(next); assertEquals(before, next.position());
            assertEquals("a fresh connector grants no extra chain", hard, next.predictionDeadlineNanos());
            String route = next.traceSnapshot().fields(0);
            assertTrue("the new logical route starts at real authority: " + route,
                route.contains("logical=" + c[2] + "," + c[3] + ";"));
            LocalPoint previous = before;
            for (int ms = c[6]+10; ms <= c[6]+350; ms += 10)
            {
                next.advance(ms*1_000_000L);
                LocalPoint at = next.position();
                assertTrue("no side excursion behind the visible/new goal interval", inInterval(at.getX(), before.getX(), goal.getX()));
                assertTrue("no overshoot toward the preceding endpoint", inInterval(at.getY(), before.getY(), goal.getY()));
                assertTrue("one movement budget", MovementPath.distance(previous, at) <= 6);
                assertTrue(next.clear()); previous = at;
            }
        }
    }

    @Test
    public void perpendicularCardinalDepartureDoesNotRetraceBeforeTurning()
    {
        // Bookmark 2: northbound display ~6080,7017, authority 6080,6976,
        // latest native destination 6336,6976. Both diagonals/perimeter are proven.
        MovementPath path = MovementPath.anticipate(p(6080,6976), p(6080,6976), p(6080,7232),
            true, 0, 1.06, MovementPath.freshDeadline(0), OPEN);
        path.advance(97_000_000L);
        LocalPoint before = path.position();
        path = path.retargetWalk(p(6336,6976), true, 97_000_000L);
        assertNotNull(path); path.advance(117_000_000L);
        assertTrue("start the eastward departure immediately", path.position().getX() > before.getX());
        assertTrue("rejoin the native row under the same clock", path.position().getY() < before.getY());
        assertTrue(path.clear());
    }

    @Test
    public void reversingFromTheAdjacentSquareCanCrossItsSharedCheckedBoundary()
    {
        // Bookmark 6: display ~6171,7269, authority 6208,7232; new Walk is
        // 6080,6848. A straight join can cross the two separately proven squares.
        MovementPath path = MovementPath.anticipate(p(6208,7232), p(6208,7232), p(6080,7360),
            false, 0, 1.06, MovementPath.freshDeadline(0), OPEN);
        path.advance(100_000_000L); path.advance(175_000_000L);
        LocalPoint before = path.position();
        path = path.retargetWalk(p(6080,6848), true, 175_000_000L);
        assertNotNull(path); path.advance(195_000_000L);
        assertTrue("the fresh reversal must not borrow an away-moving tangent", path.position().getY() < before.getY());
        path.advance(255_000_000L);
        assertTrue("cross the boundary without returning all the way to its old corner", path.position().getX() < 6195);
        assertTrue(path.clear());
    }

    @Test
    public void everyNewSquareDiagonalAndPerimeterEdgeMustBeClearInBothDirections()
    {
        LocalPoint a = p(6592,7232), b = p(6720,7104), c = p(6592,7104), d = p(6720,7232);
        for (LocalPoint[] edge : new LocalPoint[][] {{a,b},{c,d},{a,c},{a,d},{b,c},{b,d}})
        {
            for (boolean reverse : new boolean[] {false,true})
            {
                LocalPoint from = reverse ? edge[1] : edge[0], to = reverse ? edge[0] : edge[1];
                BiPredicate<LocalPoint,LocalPoint> wall = (x,y) -> !x.equals(from) || !y.equals(to);
                MovementPath path = confirmed(p(6464,7104),d,539,wall);
                MovementPath next = path.retargetWalk(p(6720,6976),true,539_000_000L);
                if (next != null)
                {
                    assertFalse("a partly checked square cannot supply a fractional departure",
                        next.traceSnapshot().fields(0).contains("6592,7232,6720,7104,6720,7104"));
                    assertTrue(next.clear());
                }
            }
        }
    }

    @Test
    public void closingTheIncomingSharedSquareInvalidatesItBeforeMoreTravel()
    {
        boolean[] closed = {false};
        BiPredicate<LocalPoint,LocalPoint> collision = (a,b) -> !closed[0] ||
            !a.equals(p(6208,7232)) || !b.equals(p(6080,7360));
        MovementPath path = MovementPath.anticipate(p(6208,7232),p(6208,7232),p(6080,7360),
            false,0,1.06,MovementPath.freshDeadline(0),collision);
        path.advance(100_000_000L); path.advance(175_000_000L);
        path = path.retargetWalk(p(6080,6848),true,175_000_000L);
        assertNotNull(path); path.advance(195_000_000L); assertTrue(path.clear());
        LocalPoint before = path.position(); closed[0] = true;
        assertFalse(path.clear()); assertNull(path.retargetWalk(p(6336,7104),true,215_000_000L));
        assertEquals(before,path.position());
    }

    @Test
    public void newDepartureRetainsItsDeadlineThroughRepeatsAndRecoversOnTimeout()
    {
        MovementPath path = confirmed(p(6464,7104),p(6720,7232),539,OPEN);
        long hard = path.predictionDeadlineNanos();
        path = path.retargetWalk(p(6720,6976),true,539_000_000L);
        assertNotNull(path);
        for (int ms = 559; ms < 800; ms += 20)
        {
            path.advance(ms*1_000_000L);
            MovementPath next = path.retargetWalk(p(6720,6976),true,ms*1_000_000L);
            if (next != null) { path = next; }
            assertEquals("same-target observations cannot extend the hard budget",hard,path.predictionDeadlineNanos());
        }
        for (int ms = 800; ms <= 3000; ms += 20) { path.advance(ms*1_000_000L); }
        assertEquals(p(6720,7232),path.position()); assertTrue(path.clear());
    }

    @Test
    public void departureTranslationKeepsItsFractionCollisionProofAndMatchingAuthority()
    {
        for (long cadence : new long[] {8_333_333,20_000_000,33_333_333})
        {
            MovementPath path = confirmed(p(6464,7104),p(6720,7232),539,OPEN);
            path = path.retargetWalk(p(6720,6976),true,539_000_000L);
            assertNotNull(path); LocalPoint before = path.position();
            long hard = path.predictionDeadlineNanos();
            assertTrue(path.resumeScene(-128,-256,p(6592,6976),p(6592,6720),true,539_000_000L));
            assertEquals(p(before.getX()-128,before.getY()-256),path.position());
            assertEquals(hard,path.predictionDeadlineNanos());
            LocalPoint previous = path.position();
            for (long t = 539_000_000L+cadence; t < 1_500_000_000L; t += cadence)
            {
                if (t >= 800_000_000L) { assertTrue(path.accept(p(6592,6720),true)); }
                path.advance(t,false,15,13);
                assertTrue(MovementPath.distance(previous,path.position()) <= Math.ceil(cadence*0.00000049)+1);
                assertTrue(path.clear()); previous = path.position();
            }
            assertEquals(p(6592,6720),path.position());
        }
    }

    @Test
    public void blockedBookmarkFiveGoalStillStopsAtTheActualNativeEndpoint()
    {
        // 01:26:57.073: Walk names 6080,7488 (scene 47,58, flags 0x20100).
        // The native client actually stops at 6080,7360; never invent that last tile.
        Fixture f = new Fixture(6080,6976,8000,8000);
        f.speed = 1.08; f.smoothing = 0; f.catchUp = true; f.catchUpPercent = 15;
        f.slowAhead = true; f.slowAheadPercent = 13; f.originalWhenAligned = false;
        f.flags[47][58] = 0x20100;
        f.authority = p(6080,7232);
        for (int ms = 20; ms <= 380; ms += 20) { f.frame(ms); }
        LocalPoint before = f.controller.position();
        f.controller.walkClick(); f.destination = p(6080,7488); f.frame(400);
        assertTrue(f.controller.position().getY() >= before.getY());
        for (int ms = 420; ms <= 2000; ms += 20)
        {
            if (ms == 720) { f.authority = p(6080,7360); f.destination = null; }
            f.frame(ms);
            assertTrue("collision-blocked goal grants no extra preview", f.controller.position().getY() <= 7360);
        }
        assertEquals(p(6080,7360),f.controller.position()); f.controller.close();
    }

    @Test
    public void bookmarkTenAlreadyTrueReturnCannotRenewItsNearlyExhaustedChain()
    {
        // 01:28:25.589 publication: only ~16 ms remain in the original hard chain.
        // The old-direction endpoint arrives ~21 ms later, then real forward
        // authority arrives 600 ms after that. These are not extra click credits.
        LocalPoint from = p(6848,7232), goal = p(6976,7488);
        MovementPath path = MovementPath.anticipate(from,goal,goal,true,0,1.06,16_000_000L,OPEN);
        assertNotNull(path); path.pendingWalkConfirmation();
        assertTrue(path.accept(from,true)); path.advance(21_000_000L);
        assertEquals(16_000_000L,path.predictionDeadlineNanos());
        for (int ms = 41; ms <= 601; ms += 20)
        {
            path.advance(ms*1_000_000L);
            assertEquals("expired input must wait for genuine onward authority",from,path.position());
        }
        assertTrue(path.accept(goal,true));
        for (int ms = 621; ms <= 1501; ms += 20) { path.advance(ms*1_000_000L); }
        assertEquals(goal,path.position()); assertTrue(path.finished());
    }

    @Test
    public void checkedAuthorityDeparturesRotateAndReflectWithoutCoordinatePolicies()
    {
        for (int rotation = 0; rotation < 4; ++rotation)
        {
            for (boolean reflection : new boolean[] {false,true})
            {
                LocalPoint from = transformed(-256,-128,rotation,reflection), authority = p(6720,7232);
                LocalPoint goal = transformed(0,-256,rotation,reflection);
                MovementPath path = confirmed(from,authority,539,OPEN);
                LocalPoint before = path.position();
                path = path.retargetWalk(goal,true,539_000_000L); assertNotNull(path);
                LocalPoint previous = before;
                for (int ms = 559; ms <= 959; ms += 20)
                {
                    if (ms >= 799) { assertTrue(path.accept(goal,true)); }
                    path.advance(ms*1_000_000L);
                    assertTrue(inInterval(path.position().getX(),before.getX(),goal.getX()));
                    assertTrue(inInterval(path.position().getY(),before.getY(),goal.getY()));
                    assertTrue(MovementPath.distance(previous,path.position()) <= 10);
                    assertTrue(path.clear()); previous = path.position();
                }
            }
        }
    }

    private static LocalPoint transformed(int dx, int dy, int rotation, boolean reflection)
    {
        if (reflection) { dx = -dx; }
        for (int i = 0; i < rotation; ++i) { int old = dx; dx = -dy; dy = old; }
        return p(6720+dx,7232+dy);
    }

    @Test
    public void recordedCardinalAndOffsetDeparturesStayWithinTheirLatestTravelCorridor() throws Exception
    {
        List<String[]> rows = capturedRows();
        for (int bookmark : new int[] {4,7,9,11})
        {
            for (long cadence : new long[] {8_333,20_000,33_333})
            {
                replay(rows, bookmark, cadence, bookmark == 4 ? 1.08 : 1.06, 15, 13, true, false, null);
            }
        }
    }

    static List<String[]> capturedRows() throws Exception
    {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            BookmarkWalkJoinTest.class.getResourceAsStream("bookmark-departures-20261012.txt"), StandardCharsets.UTF_8)))
        {
            reader.lines().filter(s -> !s.startsWith("#")).forEach(s -> rows.add(s.split(" ")));
        }
        return rows;
    }

    static void replay(List<String[]> rows, int bookmark, long cadence, double speed,
        int catchUp, int slowAhead, boolean strictDeparture, boolean minimap, double[] liveSettings)
    {
        int index = 0;
        while (!rows.get(index)[0].equals("C") || Integer.parseInt(rows.get(index)[1]) != bookmark) { ++index; }
        String[] initial = rows.get(index++);
        long start = Long.parseLong(initial[2]), end = Long.parseLong(initial[5]);
        Fixture f = new Fixture(Integer.parseInt(initial[3]), Integer.parseInt(initial[4]), 8000,8000);
        f.speed = speed; f.smoothing = 0; f.turnSpeed = 32;
        f.originalWhenAligned = false; f.catchUp = catchUp >= 0; f.catchUpPercent = Math.max(0,catchUp);
        f.slowAhead = slowAhead >= 0; f.slowAheadPercent = Math.max(0,slowAhead);
        for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
        // This crop agrees across all 190 clicks through the last bookmark.
        for (int x = 45; x <= 59; ++x) { for (int y = 52; y <= 61; ++y) { f.flags[x][y] = 0; } }
        int[][] crop = {
            {47,58,0x20100},{47,59,0x2010},{48,58,0x20301},{48,59,0x20100},{48,60,0x20100},
            {49,59,0x20502},{49,60,0x24120},{50,59,0x402},{50,60,0x4020},
            {55,59,0x402},{55,60,0x4020},{56,59,0x20502},{56,60,0x24120},
            {57,58,0x804},{57,59,0x20100},{57,60,0x20100},{58,58,0x20100},{58,59,0x8040}};
        for (int[] cell : crop) { f.flags[cell[0]][cell[1]] = cell[2]; }
        List<String[]> events = new ArrayList<>();
        while (index < rows.size() && !rows.get(index)[0].equals("C")) { events.add(rows.get(index++)); }
        TreeSet<Long> times = new TreeSet<>();
        for (long t = start; t <= end; t += cadence) { times.add(t); }
        for (String[] event : events) { times.add(Long.parseLong(event[1])); }
        LocalPoint before = f.start, latestGoal = null;
        long previousTime = start;
        int next = 0;
        boolean checked = false;
        boolean nativeBefore = f.controller.nativeVisible(), changeSettings = false;
        String context = "bookmark=" + bookmark + " speed=" + speed*5 + " catchUp=" + catchUp +
            " slowAhead=" + slowAhead + " cadence=" + cadence;
        for (long t : times)
        {
            f.now = 1_000_000_000L+(t-start)*1000; f.cycle = 100+(int)((t-start)/20_000);
            while (next < events.size() && Long.parseLong(events.get(next)[1]) <= t)
            {
                String[] event = events.get(next++);
                if (event[0].equals("K"))
                {
                    if (minimap) { f.controller.walkClick(f.destination); } else { f.controller.walkClick(); }
                }
                else if (event[0].equals("G"))
                {
                    latestGoal = p(Integer.parseInt(event[2]),Integer.parseInt(event[3])); checked = true;
                    changeSettings = liveSettings != null;
                }
                else
                {
                    f.authority = p(Integer.parseInt(event[2]),Integer.parseInt(event[3]));
                    f.destination = Integer.parseInt(event[4]) < 0 ? null : p(Integer.parseInt(event[4]),Integer.parseInt(event[5]));
                    f.nativePoint = p(Integer.parseInt(event[6]),Integer.parseInt(event[7]));
                }
            }
            int angle = f.controller.orientation(); f.controller.update(); f.controller.presented();
            LocalPoint at = f.controller.position();
            if (!nativeBefore && !f.controller.nativeVisible())
            {
                assertTrue("single movement budget " + context + " time=" + t,
                    MovementPath.distance(before,at) <= Math.ceil((t-previousTime)*0.0004*f.speed*
                        (1+(f.catchUp ? f.catchUpPercent/100.0 : 0)))+1);
                assertTrue("single capped turn budget " + context, Math.abs(MotionMath.difference(angle,f.controller.orientation())) <=
                    Math.ceil(32*(t-previousTime)/16_667.0)+1);
            }
            if (checked && strictDeparture)
            {
                assertTrue("no lateral detour at bookmark " + bookmark + " time " + t,
                    inInterval(at.getX(),before.getX(),latestGoal.getX()));
            }
            if (changeSettings)
            {
                f.speed = liveSettings[0]/5;
                f.catchUp = liveSettings[1] >= 0; f.catchUpPercent = (int) Math.max(0,liveSettings[1]);
                f.slowAhead = liveSettings[2] >= 0; f.slowAheadPercent = (int) Math.max(0,liveSettings[2]);
                f.controller.update();
                assertEquals("live settings keep the exact displayed point " + context,at,f.controller.position());
                f.controller.update();
                assertEquals("repeated preparation spends no extra time " + context,at,f.controller.position());
                changeSettings = false;
            }
            nativeBefore = f.controller.nativeVisible();
            before = at; previousTime = t;
        }
        // Fixed native click/tick times put a slow display much farther behind.
        // Let its checked confirmed queue drain; do not require the recorded
        // near-arrival fraction or remove legitimate debt at a different speed.
        long limit = end + (long) Math.ceil(2048/(0.2*f.speed))*1000 + 2_000_000;
        long t = previousTime;
        while (!latestGoal.equals(f.controller.position()) && t < limit)
        {
            t += 100_000; f.now = 1_000_000_000L+(t-start)*1000; f.cycle = 100+(int)((t-start)/20_000);
            before = f.controller.position(); nativeBefore = f.controller.nativeVisible();
            f.controller.update();
            if (!nativeBefore && !f.controller.nativeVisible())
            {
                assertTrue("confirmed catch-up keeps its budget " + context,MovementPath.distance(before,f.controller.position()) <=
                    Math.ceil(40*f.speed*(1+(f.catchUp ? f.catchUpPercent/100.0 : 0)))+1);
            }
        }
        assertEquals("settle at actual authority " + context,latestGoal,f.controller.position());
        for (int i = 0; i < 5; ++i)
        {
            t += 100_000; f.now = 1_000_000_000L+(t-start)*1000; f.cycle = 100+(int)((t-start)/20_000);
            f.controller.update(); assertEquals("no extra itinerary after final arrival " + context,latestGoal,f.controller.position());
        }
        f.controller.close();
    }

    private static boolean inInterval(int at, int from, int to) { return at >= Math.min(from,to)-1 && at <= Math.max(from,to)+1; }

    private static MovementPath confirmed(LocalPoint from, LocalPoint to, int millis, BiPredicate<LocalPoint,LocalPoint> collision)
    {
        MovementPath path = MovementPath.idle(from, 0, 1.06, collision);
        assertTrue(path.accept(to,true));
        for (int ms = 10; ms < millis; ms += 10) { path.advance(ms*1_000_000L); }
        path.advance(millis*1_000_000L); return path;
    }
}
