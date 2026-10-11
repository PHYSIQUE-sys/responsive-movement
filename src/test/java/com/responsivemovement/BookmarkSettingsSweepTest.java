package com.responsivemovement;

import java.util.List;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Settings variants of the bookmark evidence; native timing remains an API double. */
public class BookmarkSettingsSweepTest
{
    private static final int[] BOOSTS = {-1,0,1,10,15,25,50}; // -1 is toggle off.
    private static final int[] SLOWDOWNS = {-1,13,50};
    private static final long[] CADENCES = {8_333_333,20_000_000,33_333_333};
    private static final double[] REPLAY_SPEEDS = {0.5,1,2.5,4,4.9,5,5.1,5.3,5.4,6,7.5,10};
    private static final BiPredicate<LocalPoint,LocalPoint> OPEN = (a,b) -> true;
    private static LocalPoint p(int x, int y) { return new LocalPoint(x,y,0); }

    // Warm the old path at the captured baseline, then vary speed/pacing at the
    // same visible fraction. This separates the geometric fix from changing
    // where the real 600-ms publications meet a very slow/fast display.
    private static final int[][] DEPARTURES = {
        {6336,7104,6592,6976,6592,7360,486},
        {6464,7104,6720,7232,6720,6976,539},
        {6080,7104,6336,6976,6464,7488,520},
        {6720,7616,6976,7744,6976,7360,565},
        {6976,7488,6720,7616,6720,7232,503},
        {6976,7232,7104,7488,6848,7488,587},
        {6080,6976,6080,7232,6336,6976,97},
        {6208,7232,6080,7360,6080,6848,175}};

    @Test
    public void all96SliderSpeedsAndPacingStrengthsKeepTheBookmarkedDepartureGeometry()
    {
        int runs = 0;
        for (int slider = 5; slider <= 100; ++slider)
        {
            for (int boost : BOOSTS)
            {
                for (int slowdown : SLOWDOWNS)
                {
                    for (int scenario = 0; scenario < DEPARTURES.length; ++scenario)
                    {
                        for (long cadence : CADENCES)
                        {
                            for (boolean run : new boolean[] {false,true})
                            {
                                departure(slider/10.0,boost,slowdown,scenario,cadence,run); ++runs;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(96_768,runs);
    }

    private static void departure(double speed, int boost, int slowdown, int scenario, long cadence, boolean run)
    {
        int[] c = DEPARTURES[scenario];
        LocalPoint from = p(c[0],c[1]), authority = p(c[2],c[3]), goal = p(c[4],c[5]);
        MovementPath path;
        if (scenario >= 6)
        {
            path = MovementPath.anticipate(from,from,authority,scenario == 6,0,1.06,MovementPath.freshDeadline(0),OPEN);
            authority = from;
        }
        else
        {
            path = MovementPath.idle(from,0,1.06,OPEN); assertTrue(path.accept(authority,true));
        }
        long start = c[6]*1_000_000L;
        for (long t = 10_000_000L; t < start; t += 10_000_000L) { path.advance(t); }
        path.advance(start);
        LocalPoint fraction = path.position(); long hard = path.predictionDeadlineNanos();
        path.speed(speed/5);
        assertEquals("changing speed keeps the captured fraction",fraction,path.position());
        path = path.retargetWalk(goal,run,start);
        String context = "scenario=" + scenario + " speed=" + speed + " boost=" + boost +
            " slowdown=" + slowdown + " cadence=" + cadence + " run=" + run;
        assertNotNull(context,path); assertEquals(context,fraction,path.position()); assertEquals(context,hard,path.predictionDeadlineNanos());
        assertTrue("retain authority-anchored logical travel " + context,
            path.traceSnapshot().fields(0).contains("logical=" + authority.getX() + "," + authority.getY() + ";"));
        int steps = Math.max(Math.abs(goal.getX()-authority.getX()),Math.abs(goal.getY()-authority.getY()))/128;
        int stepSize = run ? 2 : 1;
        long lastTick = start+((steps+stepSize-1)/stepSize)*600_000_000L;
        long end = lastTick+(long) Math.ceil(1024/(0.2*(speed/5))*1_000_000L)+200_000_000L;
        TreeSet<Long> times = new TreeSet<>();
        // Sample geometry at each cadence until the native endpoints are
        // confirmed, then use 100-ms frames for the potentially long slow queue.
        for (long t = start+cadence; t < lastTick; t += cadence) { times.add(t); }
        for (long t = start+600_000_000L; t <= lastTick; t += 600_000_000L) { times.add(t); }
        for (long t = lastTick+100_000_000L; t <= end; t += 100_000_000L) { times.add(t); }
        LocalPoint previous = fraction; long previousTime = start;
        boolean arrived = false;
        for (long t : times)
        {
            if (t <= lastTick && (t-start)%600_000_000L == 0)
            {
                assertTrue(context,path.accept(nativeEndpoint(authority,goal,
                    (int)((t-start)/600_000_000L)*stepSize),run));
            }
            path.advance(t,false,Math.max(0,boost),Math.max(0,slowdown));
            LocalPoint at = path.position();
            assertTrue("one movement budget " + context,MovementPath.distance(previous,at) <=
                Math.ceil((t-previousTime)*(run ? 0.0000004 : 0.0000002)*(speed/5)*(1+Math.max(0,boost)/100.0))+1);
            assertTrue("collision-valid departure " + context,path.clear());
            if (scenario < 7)
            {
                assertTrue("no old side-anchor excursion " + context,between(at.getX(),fraction.getX(),goal.getX()));
                assertTrue("no prior-endpoint overshoot " + context,between(at.getY(),fraction.getY(),goal.getY()));
            }
            else if (at.getY() >= 7104)
            {
                assertTrue("shared-square departure does not return to the old corner " + context,
                    offLine(fraction,p(6208,7104),at) <= 1.6);
            }
            if (arrived) { assertEquals("no extra trip after arrival " + context,goal,at); }
            arrived |= at.equals(goal) && t >= lastTick;
            previous = at; previousTime = t;
            if (arrived && t > lastTick+500_000_000L) { break; }
        }
        assertTrue("confirmed movement eventually arrives " + context,arrived);
    }

    private static LocalPoint nativeEndpoint(LocalPoint from, LocalPoint goal, int index)
    {
        int dx = goal.getX()-from.getX(), dy = goal.getY()-from.getY();
        int xs = Math.abs(dx)/128, ys = Math.abs(dy)/128, steps = Math.max(xs,ys);
        index = Math.min(index,steps);
        return p(from.getX()+Integer.signum(dx)*Math.max(0,index-(steps-xs))*128,
            from.getY()+Integer.signum(dy)*Math.max(0,index-(steps-ys))*128);
    }

    @Test
    public void capturedNativeTimelinesConvergeAcrossSpeedCatchUpAndAheadSettings() throws Exception
    {
        List<String[]> rows = BookmarkWalkJoinTest.capturedRows(); int runs = 0;
        for (double speed : REPLAY_SPEEDS)
        {
            for (int boost : BOOSTS)
            {
                for (int slowdown : SLOWDOWNS)
                {
                    for (int bookmark : new int[] {4,7,9,11})
                    {
                        for (long cadence : CADENCES)
                        {
                            BookmarkWalkJoinTest.replay(rows,bookmark,cadence/1000,speed/5,boost,slowdown,false,false,null); ++runs;
                        }
                    }
                }
            }
        }
        assertEquals(3024,runs);
    }

    @Test
    public void liveSpeedAndPacingChangesKeepPositionForSceneAndMinimapWalks() throws Exception
    {
        List<String[]> rows = BookmarkWalkJoinTest.capturedRows(); int runs = 0;
        double[][] changes = {{0.5,-1,-1,10,50,50},{10,50,50,0.5,-1,-1},{5.3,15,13,5.4,10,-1}};
        for (double[] change : changes)
        {
            for (int bookmark : new int[] {4,7,9,11})
            {
                for (long cadence : CADENCES)
                {
                    for (boolean minimap : new boolean[] {false,true})
                    {
                        BookmarkWalkJoinTest.replay(rows,bookmark,cadence/1000,change[0]/5,(int) change[1],(int) change[2],
                            false,minimap,new double[] {change[3],change[4],change[5]}); ++runs;
                    }
                }
            }
        }
        assertEquals(72,runs);
    }

    @Test
    public void blockedGoalsAndNearlyExpiredReturnsKeepTheirBoundsAtEverySliderSpeed()
    {
        int runs = 0;
        for (int slider = 5; slider <= 100; ++slider)
        {
            for (int boost : BOOSTS)
            {
                for (int slowdown : SLOWDOWNS)
                {
                    double speed = slider/50.0;
                    LocalPoint from = p(6080,6976), blocked = p(6080,7488), stop = p(6080,7360);
                    BiPredicate<LocalPoint,LocalPoint> wall = (a,b) -> !a.equals(blocked) && !b.equals(blocked);
                    assertNull("blocked native goal is never a new forecast",MovementPath.anticipate(from,from,blocked,true,
                        0,speed,MovementPath.freshDeadline(0),wall));
                    MovementPath path = MovementPath.idle(from,0,speed,wall); assertTrue(path.accept(p(6080,7232),true));
                    for (long t = 20_000_000L; (t <= 600_000_000L || !path.finished()) && t <= 20_000_000_000L; t += 20_000_000L)
                    {
                        if (t == 600_000_000L) { assertTrue(path.accept(stop,true)); }
                        path.advance(t,false,Math.max(0,boost),Math.max(0,slowdown));
                        assertTrue(path.position().getY() <= stop.getY()); assertTrue(path.clear());
                    }
                    assertEquals(stop,path.position());
                    from = p(6848,7232); LocalPoint goal = p(6976,7488);
                    path = MovementPath.anticipate(from,goal,goal,true,0,speed,16_000_000L,OPEN);
                    assertNotNull(path); path.pendingWalkConfirmation();
                    assertTrue(path.accept(from,true)); path.advance(21_000_000L,false,Math.max(0,boost),Math.max(0,slowdown));
                    for (long t = 41_000_000L; t < 621_000_000L; t += 20_000_000L)
                    {
                        path.speed(speed); path.advance(t,false,Math.max(0,boost),Math.max(0,slowdown));
                        assertEquals("settings cannot renew a depleted chain",16_000_000L,path.predictionDeadlineNanos());
                        assertEquals("wait for real onward authority",from,path.position());
                    }
                    assertTrue(path.accept(goal,true));
                    for (long t = 621_000_000L; !path.finished() && t <= 20_000_000_000L; t += 20_000_000L)
                    {
                        path.advance(t,false,Math.max(0,boost),Math.max(0,slowdown)); assertTrue(path.clear());
                    }
                    assertEquals(goal,path.position()); ++runs;
                }
            }
        }
        assertEquals(2016,runs);
    }

    private static boolean between(int at, int from, int to) { return at >= Math.min(from,to)-1 && at <= Math.max(from,to)+1; }
    private static double offLine(LocalPoint a, LocalPoint b, LocalPoint at)
    {
        double dx = b.getX()-a.getX(), dy = b.getY()-a.getY();
        return Math.abs(dx*(at.getY()-a.getY())-dy*(at.getX()-a.getX()))/Math.hypot(dx,dy);
    }
}
