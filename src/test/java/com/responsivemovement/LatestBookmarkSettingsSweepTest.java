package com.responsivemovement;

import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Fixed bookmark-derived fractions distinguish geometry from a differently paced earlier trip. */
public class LatestBookmarkSettingsSweepTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x,y,0); }

    @Test
    public void all96SpeedsToggleAndStageVariantsKeepAuthorityJoinsAndCurvesInsideTheLatestGoalInterval()
    {
        int runs = 0;
        for (int slider = 5; slider <= 100; ++slider)
        {
            for (int toggles = 0; toggles < 8; ++toggles)
            {
                for (int profile = 0; profile < 3; ++profile)
                {
                    for (int scenario = 0; scenario < 3; ++scenario)
                    {
                        for (int cadence : new int[] {8,20,33,100})
                        {
                            for (boolean run : new boolean[] {false,true})
                            {
                                departure(slider/10.0,toggles,profile,scenario,cadence,run); ++runs;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(55_296,runs);
    }

    private static void departure(double speed, int toggles, int profile, int scenario, int cadence, boolean run)
    {
        MovementPath path; LocalPoint goal; int start;
        if (scenario == 0)
        {
            path = MovementPath.anticipate(p(6720,6592),p(6720,6592),p(6592,6720),false,
                0,1.18,MovementPath.freshDeadline(0),(a,b) -> true);
            advance(path,0,460);
            path = path.retargetWalk(p(6592,6592),false,460_000_000L); assertNotNull(path);
            advance(path,460,500); start = 500; goal = p(6592,6592);
        }
        else if (scenario == 1)
        {
            path = MovementPath.anticipate(p(5056,6720),p(5056,6720),p(5056,6592),false,
                0,1.18,MovementPath.freshDeadline(0),(a,b) -> true);
            advance(path,0,400);
            path = path.retargetWalk(p(4928,6720),false,400_000_000L); assertNotNull(path);
            advance(path,400,450); start = 450; goal = p(4928,6720);
        }
        else
        {
            path = MovementPath.anticipate(p(7488,6336),p(7488,6336),p(7744,6208),true,
                0,1.18,MovementPath.freshDeadline(0),(a,b) -> true);
            advance(path,0,200); start = 200; goal = p(7744,6464);
        }
        LocalPoint before = path.position(); long originalHard = path.predictionDeadlineNanos();
        path.speed(MovementPath.configuredSpeed(speed)); assertEquals(before,path.position());
        if (scenario == 0)
        {
            path = path.alignWalkAuthority(p(6592,6720),run,start*1_000_000L);
        }
        else if (scenario == 1) { assertTrue(path.accept(p(5056,6592),run)); }
        else { path = path.retargetWalk(goal,run,start*1_000_000L); }
        String context = "speed="+speed+" toggles="+toggles+" profile="+profile+" scenario="+scenario+" cadence="+cadence+" run="+run;
        assertNotNull(context,path); assertEquals("same fraction "+context,before,path.position());
        if (scenario != 0) { assertEquals("no old-input credit "+context,originalHard,path.predictionDeadlineNanos()); }
        long hard = path.predictionDeadlineNanos();
        int boost = (toggles & 1) == 0 ? 0 : profile == 0 ? 15 : profile == 1 ? 0 : 50;
        int ahead = (toggles & 2) == 0 ? 0 : profile == 0 ? 13 : profile == 1 ? 0 : 50;
        ArrivalPacing arrival = (toggles & 4) == 0 ? ArrivalPacing.OFF : profile == 0 ?
            new ArrivalPacing(0.2,1,50,49.8) : profile == 1 ? new ArrivalPacing(0,0,100,100) :
            new ArrivalPacing(10,10,1,1);
        LocalPoint previous = before; long previousTime = start*1_000_000L;
        for (int elapsed = cadence; elapsed <= 300; elapsed += cadence)
        {
            long now = (start+elapsed)*1_000_000L;
            path.advance(now,false,boost,ahead,goal,arrival);
            LocalPoint at = path.position();
            assertTrue("one frame-time budget "+context,MovementPath.distance(previous,at) <=
                Math.ceil((now-previousTime)*0.0000004*(speed/5)*(1+boost/100.0))+1);
            assertTrue("no lateral detour "+context,between(at.getX(),before.getX(),goal.getX()));
            assertTrue("no previous-click endpoint excursion "+context,between(at.getY(),before.getY(),goal.getY()));
            assertTrue("collision proof "+context,path.clear());
            assertEquals("settings cannot renew deadlines "+context,hard,path.predictionDeadlineNanos());
            path.advance(now,false,boost,ahead,goal,arrival);
            assertEquals("zero-time preparation "+context,at,path.position());
            previous = at; previousTime = now;
        }
    }

    private static void advance(MovementPath path, int from, int to)
    {
        for (int ms = from+20; ms < to; ms += 20) { path.advance(ms*1_000_000L); }
        path.advance(to*1_000_000L);
    }

    private static boolean between(int value, int from, int to)
    {
        return value >= Math.min(from,to)-1 && value <= Math.max(from,to)+1;
    }
}
