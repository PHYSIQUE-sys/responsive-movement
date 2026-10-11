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
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class RepeatedWalkContinuityTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }
    @Test public void secondNewestKnightCaptureCannotAppendAPrecedingEndpointBehindTheNewReversal() throws Exception { replay("A"); }
    @Test public void newestRepeatedKnightCaptureCannotInventAnExtraOutAndBackAfterClicksStop() throws Exception { replay("B"); }
    @Test public void markedCircleCaptureKeepsTurningAndTravelBudgetsThroughThePause() throws Exception { replay("C"); }

    @Test
    public void correctedFineSpeedRetainsCapturedRoutesArrivalsAndSingleFrameBudgets() throws Exception
    {
        for (String id : new String[] {"A", "B", "C"}) { replay(id, 1.08); }
    }

    private static void replay(String id) throws Exception
    {
        // These captures were executed at the formerly quantized effective
        // 1.10 pace. Keep their geometry-specific historical assertions intact.
        replay(id, 1.10);
    }

    private static void replay(String id, double speed) throws Exception
    {
        List<String[]> map = new ArrayList<>(), events = new ArrayList<>();
        String[] header = null;
        boolean selected = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            RepeatedWalkContinuityTest.class.getResourceAsStream(id.equals("C") ? "marked-circle-2229.txt" : "repeated-knights-2229-2237.txt"), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.startsWith("#")) { continue; }
                String[] row = line.split(" ");
                if (row[0].equals("M")) { map.add(row); }
                else if (row[0].equals("C")) { selected = row[1].equals(id); if (selected) { header = row; } }
                else if (selected) { events.add(row); }
            }
        }
        assertNotNull(header);
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            Fixture f = new Fixture(Integer.parseInt(header[2]), Integer.parseInt(header[3]), 8000, 8000);
            f.speed = speed; f.turnSpeed = 30; f.smoothing = 0; f.originalWhenAligned = false;
            f.nativeOrientation = Integer.parseInt(header[4]);
            f.controller.close(); f.controller.update(); f.controller.presented();
            for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
            for (String[] column : map)
            {
                String[] flags = column[2].split(",");
                for (int y = 0; y < flags.length; ++y) { f.flags[Integer.parseInt(column[1])][38 + y] = Integer.parseUnsignedInt(flags[y], 16); }
            }
            TreeSet<Long> times = new TreeSet<>();
            for (long t = 0; t <= Long.parseLong(header[5]); t += cadence) { times.add(t); }
            for (String[] event : events) { times.add(Long.parseLong(event[1])); }
            long previousTime = 0;
            int next = 0;
            LocalPoint previous = f.start, held = null;
            LocalPoint reversalGoal = id.equals("A") ? p(5568,5568) : p(5312,5568);
            long begin = id.equals("C") ? Long.MAX_VALUE : id.equals("A") ? 5_878_025 : 3_914_645;
            long end = id.equals("A") ? 6_408_463 : 8_384_239;
            for (long t : times)
            {
                f.now = 1_000_000_000L + t * 1000; f.cycle = 100 + (int) (t / 20_000);
                while (next < events.size() && Long.parseLong(events.get(next)[1]) <= t)
                {
                    String[] e = events.get(next++);
                    if (e[0].equals("K")) { f.controller.walkClick(); }
                    else
                    {
                        f.authority = p(Integer.parseInt(e[2]),Integer.parseInt(e[3]));
                        f.destination = Integer.parseInt(e[4]) < 0 ? null : p(Integer.parseInt(e[4]),Integer.parseInt(e[5]));
                        f.nativePoint = p(Integer.parseInt(e[6]),Integer.parseInt(e[7]));
                    }
                }
                int angle = f.controller.orientation(); f.controller.update();
                LocalPoint actual = f.controller.position();
                // The fixed-time window identifies a particular positive-turn
                // connector at the captured effective pace. A different speed
                // can choose a different checked connector with another heading.
                if (speed == 1.10 && id.equals("C") && t >= 7_740_000 && t <= 7_940_000)
                {
                    assertTrue("no corrective facing sweep in the marked curved-join window at " + t +
                        "us, angle " + angle + " -> " + f.controller.orientation() + " position " + actual,
                        MotionMath.difference(angle,f.controller.orientation()) >= -1);
                }
                assertTrue("one frame movement budget at " + t, MovementPath.distance(previous,actual) <= Math.ceil((t - previousTime) * 0.0004 * speed) + 1);
                assertTrue("one turn budget", Math.abs(MotionMath.difference(angle,f.controller.orientation())) <= Math.ceil(30 * (t - previousTime) / 16_667.0) + 1);
                if (t >= begin && t < end)
                {
                    assertTrue("late preceding-click authority must not add a return trip at " + t,
                        squared(actual,reversalGoal) <= squared(previous,reversalGoal) + 2);
                    if (actual.equals(reversalGoal)) { held = reversalGoal; }
                    if (held != null) { assertEquals("hold the latest goal until another click", held, actual); }
                }
                else { held = null; }
                previous = actual; previousTime = t;
            }
            assertEquals(id.equals("B") ? p(5440,5824) : p(5696,5824), f.controller.position());
            f.controller.close();
        }
    }

    private static long squared(LocalPoint a, LocalPoint b)
    {
        long dx = a.getX() - b.getX(), dy = a.getY() - b.getY(); return dx * dx + dy * dy;
    }

    @Test
    public void aLateKnightEndpointCannotConfirmItsAlreadyTrueReversalGoal()
    {
        for (int[] delta : new int[][] {{128,256},{256,128},{-128,256},{256,-128},{-256,-128},{128,-256}})
        {
            LocalPoint start = p(5312,5568), old = p(start.getX()+delta[0],start.getY()+delta[1]);
            MovementPath path = MovementPath.anticipate(start,start,old,true,0,1.1,MovementPath.freshDeadline(0),(a,b)->true);
            for (int ms = 20; ms <= 400; ms += 20) { path.advance(ms*1_000_000L); }
            path = path.retargetWalk(start,true,400_000_000L);
            assertNotNull(path); long deadline = path.predictionDeadlineNanos();
            assertTrue(path.accept(old,true)); assertEquals(deadline,path.predictionDeadlineNanos());
            LocalPoint before = path.position();
            for (int ms = 420; ms <= 1000; ms += 20)
            {
                if (ms == 800) { assertTrue(path.accept(start,true)); }
                path.advance(ms*1_000_000L);
                assertTrue("no invented trip toward preceding knight", squared(path.position(),start) <= squared(before,start)+2);
                before = path.position();
            }
            assertEquals(start,path.position()); assertTrue(path.finished());
        }
    }

    @Test
    public void aSecondKnightNearNativeOriginRetainsItsWholeCheckedChord()
    {
        LocalPoint start = p(5440,5824), nativeOrigin = p(5568,5568), goal = p(5696,5824);
        MovementPath path = MovementPath.anticipate(start,start,nativeOrigin,true,0,1.1,MovementPath.freshDeadline(0),(a,b)->true);
        assertTrue(path.accept(nativeOrigin,true));
        for (int ms = 20; ms <= 560; ms += 20) { path.advance(ms*1_000_000L); }
        LocalPoint before = path.position(); long deadline = path.predictionDeadlineNanos();
        MovementPath next = path.retargetWalk(goal,true,560_000_000L);
        assertNotNull(next); assertEquals(before,next.position()); assertEquals(deadline,next.predictionDeadlineNanos());
        assertTrue("new logical route begins at actual authority", next.traceSnapshot().fields(0).contains("logical=5568,5568;5568,5696;5696,5824"));
        assertTrue("retain the native whole knight, not a cardinal/diagonal split", next.traceSnapshot().fields(0).contains("5568,5568,5696,5824,5696,5824,1,1"));
    }

    @Test
    public void aCurvedConnectorCannotMakeFacingOvershootAndThenLookBack()
    {
        checkCurveFacing(1.1);
        checkCurveFacing(1.08);
    }

    private static void checkCurveFacing(double speed)
    {
        MovementPath path = MovementPath.anticipate(p(6848,8000),p(6848,8000),p(7104,8128),
            true,0,speed,MovementPath.freshDeadline(0),(a,b)->true);
        for (int ms = 20; ms <= 200; ms += 20) { path.advance(ms*1_000_000L); }
        int incoming = 0, change = 0, previous = 0;
        path = path.retargetWalk(p(7104,7872),true,200_000_000L);
        int samples = 0;
        for (int ms = 202; ms <= 400; ms += 2)
        {
            path.advance(ms*1_000_000L);
            String snapshot = path.traceSnapshot().fields(0);
            if (!snapshot.matches(".* joins=\\[[0-9].*")) { break; }
            if (samples == 0)
            {
                // Pace can change the chosen checked connector. Derive its
                // intended endpoint tangents from published cubic geometry,
                // rather than assuming the old 1.10 connector's anchor.
                String[] values = snapshot.split(" joins=\\[")[1].split("\\]")[0].split(",");
                double[] controls = Arrays.stream(values).limit(8).mapToDouble(Double::parseDouble).toArray();
                incoming = MotionMath.heading(controls[2] - controls[0], controls[3] - controls[1]);
                int terminal = MotionMath.heading(controls[6] - controls[4], controls[7] - controls[5]);
                change = MotionMath.difference(incoming, terminal); previous = incoming;
            }
            ++samples;
            int heading = MotionMath.heading(path.turnX(),path.turnY());
            int progress = Integer.signum(change)*MotionMath.difference(incoming,heading);
            assertTrue("curve facing must stay between incoming and intended terminal headings", progress >= -1 && progress <= Math.abs(change)+1);
            assertTrue("no corrective left/right target sweep during one blend", Integer.signum(change)*MotionMath.difference(previous,heading) >= -1);
            previous = heading;
        }
        assertTrue(samples > 20);
    }

    @Test
    public void staleNonCardinalUpdatesKeepTheirOriginalBoundsAndRecoverWhenUnconfirmed()
    {
        LocalPoint start = p(5312,5568), preceding = p(5440,5824);
        MovementPath path = MovementPath.anticipate(start,start,preceding,true,0,1.1,MovementPath.freshDeadline(0),(a,b)->true);
        for (int ms = 20; ms <= 400; ms += 20) { path.advance(ms*1_000_000L); }
        path = path.retargetWalk(start,true,400_000_000L);
        long hard = path.predictionDeadlineNanos();
        assertTrue(path.accept(preceding,true));
        String snapshot = path.traceSnapshot().fields(0);
        assertTrue(snapshot.contains("deadlineUs=1300000")); assertEquals(hard,path.predictionDeadlineNanos());
        for (int ms = 420; ms <= 3000; ms += 20)
        {
            LocalPoint before = path.position(); path.advance(ms*1_000_000L);
            assertTrue(MovementPath.distance(before,path.position()) <= 10); assertTrue(path.clear());
        }
        assertEquals("missing confirmation still recovers to actual authority", preceding,path.position());
        assertTrue(path.finished());
    }

    @Test
    public void aNewForwardKnightStepStillConfirmsAndDoesNotGetMistakenForStaleAuthority()
    {
        LocalPoint start = p(5312,5568), old = p(5440,5824);
        MovementPath path = MovementPath.anticipate(start,start,old,true,0,1.1,MovementPath.freshDeadline(0),(a,b)->true);
        assertTrue(path.accept(old,true));
        for (int ms = 20; ms <= 400; ms += 20) { path.advance(ms*1_000_000L); }
        path = path.retargetWalk(start,true,400_000_000L);
        assertTrue(path.accept(start,true));
        assertEquals(start,path.confirmed()); assertEquals("confirmed",path.phase());
        for (int ms = 420; ms <= 1000; ms += 20) { path.advance(ms*1_000_000L); }
        assertEquals(start,path.position()); assertTrue(path.finished());
    }

    @Test
    public void pendingKnightReversalTranslatesAndCannotBeRetargetedAcrossNewCollision()
    {
        boolean[] open = {true};
        MovementPath path = MovementPath.anticipate(p(5312,5568),p(5312,5568),p(5440,5824),true,
            0,1.1,MovementPath.freshDeadline(0),(a,b)->open[0]);
        for (int ms = 20; ms <= 400; ms += 20) { path.advance(ms*1_000_000L); }
        path = path.retargetWalk(p(5312,5568),true,400_000_000L);
        long hard = path.predictionDeadlineNanos();
        assertTrue(path.accept(p(5440,5824),true));
        LocalPoint before = path.position();
        assertTrue(path.resumeScene(-128,-256,p(5312,5568),p(5184,5312),true,400_000_000L));
        assertEquals(p(before.getX()-128,before.getY()-256),path.position());
        assertEquals(hard,path.predictionDeadlineNanos());
        open[0] = false;
        assertFalse(path.clear()); assertNull(path.retargetWalk(p(5184,5312),true,420_000_000L));
    }

    @Test
    public void wholeKnightHandoffRequiresBothCheckedOrderingsAndActiveRun()
    {
        for (boolean running : new boolean[] {false,true})
        {
            boolean[] blocked = {false};
            LocalPoint start = p(5440,5824), origin = p(5568,5568), goal = p(5696,5824);
            MovementPath path = MovementPath.anticipate(start,start,origin,true,0,1.1,MovementPath.freshDeadline(0),
                (a,b)->!blocked[0] || !(a.equals(origin) && b.equals(p(5696,5696))));
            assertTrue(path.accept(origin,true));
            for (int ms = 20; ms <= 560; ms += 20) { path.advance(ms*1_000_000L); }
            blocked[0] = running;
            LocalPoint before = path.position();
            MovementPath next = path.retargetWalk(goal,running,560_000_000L);
            assertNotNull(next); assertEquals(before,next.position()); assertTrue(next.clear());
            assertFalse("no unproved/unsupported whole chord", next.traceSnapshot().fields(0).contains("5568,5568,5696,5824,5696,5824,1,1"));
        }
    }

    @Test
    public void genuineDetourProgressAwayFromTheGeometricGoalStillConfirmsNormally()
    {
        LocalPoint start = p(5312,5568), corner = p(5440,5568), goal = p(5312,5824);
        java.util.function.BiPredicate<LocalPoint,LocalPoint> collision = (a,b)->
            (!a.equals(start) || b.equals(corner)) && (!b.equals(start) || a.equals(corner));
        MovementPath path = MovementPath.anticipate(start,start,p(5568,5568),true,
            0,1.1,MovementPath.freshDeadline(0),collision);
        for (int ms = 20; ms <= 100; ms += 20) { path.advance(ms*1_000_000L); }
        path = path.retargetWalk(goal,true,100_000_000L);
        assertNotNull(path);
        for (int ms = 120; ms <= 600; ms += 20) { path.advance(ms*1_000_000L); }
        assertTrue(path.accept(corner,true));
        assertTrue("real checked detour progress replenishes the ordinary reserve", path.traceSnapshot().fields(0).contains("deadlineUs=1500000"));
        for (int ms = 620; ms <= 1200; ms += 20)
        {
            path.advance(ms*1_000_000L); assertNotEquals("do not mistake necessary detour progress for a stale tick", "recovery",path.phase());
        }
        assertTrue(path.accept(goal,true));
        for (int ms = 1220; ms <= 1800; ms += 20) { path.advance(ms*1_000_000L); }
        assertEquals(goal,path.position()); assertTrue(path.finished());
    }
}
