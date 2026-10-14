package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class MovingObjectHandoffTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }

    @Test
    public void failedInitialPlanStillObservesANativeCorrectionWhileIdleOrMoving()
    {
        for (boolean moving : new boolean[] {false,true})
        {
            Fixture f = new Fixture(6464,6720,8000,8000);
            try
            {
                f.speed = 1.18; f.smoothing = 0;
                f.wall(1804,43,50);
                for (int[] side : new int[][] {{42,50},{44,50},{43,49},{43,51}})
                {
                    f.flags[side[0]][side[1]] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
                }
                if (moving) { f.authority = p(6336,6720); f.frame(20); }
                f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION,"Open",1804,43,50));
                f.destination = p(5568,6464); f.frame(40);
                f.destination = p(6080,6720);
                f.frame(60);
                LocalPoint before = f.controller.position();
                for (int ms = 80; ms <= 460; ms += 20)
                {
                    f.frame(ms);
                    assertTrue("a reachable refined goal resumes the existing click", f.controller.position().getX() < before.getX());
                    assertTrue(MovementPath.distance(before,f.controller.position()) <= 11);
                    before = f.controller.position();
                }
            }
            finally { f.controller.close(); }
        }
    }

    @Test
    public void refinementCannotRenewExpiredCreditOrBypassBlockedGeometry()
    {
        MovementPath path = MovementPath.idle(p(6464,6720),0,1.18,(a,b)->true);
        long hard = path.predictionDeadlineNanos();
        MovementPath refined = path.refineConfirmedApproach(p(6080,6720),true,100_000_000L,false);
        assertNotNull(refined);
        assertEquals(hard,refined.predictionDeadlineNanos());
        assertNull(path.refineConfirmedApproach(p(6080,6720),true,hard,false));
        MovementPath blocked = MovementPath.idle(p(6464,6720),0,1.18,(a,b)->false);
        assertNull(blocked.refineConfirmedApproach(p(6080,6720),true,100_000_000L,false));
        assertNull(path.refineConfirmedApproach(p(6080,6720),true,100_000_000L,false)
            .refineConfirmedApproach(p(5952,6720),true,120_000_000L,false));
    }

    @Test
    public void recordedDoorApproachKeepsTravelAcrossIdleMovingAndTurningClicks() throws Exception
    {
        checkStarts(true);
        checkStarts(false);
    }

    private static void checkStarts(boolean run) throws Exception
    {
        for (int click : new int[] {0, 100, 300, 620, 900})
        {
            for (int cadence : new int[] {10, 20, 33})
            {
                Fixture f = fixture();
                f.run = run;
                LocalPoint firstAuthority = run ? p(6208,6592) : p(6336,6592);
                try
                {
                    if (click > 0) { f.controller.walkClick(); f.destination = p(5440,6336); }
                    for (int ms = 0; ms < click; ms += cadence)
                    {
                        if (ms >= 600) { f.authority = firstAuthority; }
                        f.frame(ms);
                    }
                    if (click >= 600) { f.authority = firstAuthority; }
                    f.at(click);
                    f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Open", 1804,43,50));
                    f.destination = p(5568,6464); f.frame(click);
                    LocalPoint before = f.controller.position();
                    for (int ms = click + cadence; ms < click + 420; ms += cadence)
                    {
                        f.frame(ms);
                        int travelled = MovementPath.distance(before, f.controller.position());
                        int alongCorner = Math.abs(before.getX() - f.controller.position().getX()) +
                            Math.abs(before.getY() - f.controller.position().getY());
                        assertTrue("no near-stationary handoff: click=" + click + " ms=" + ms + " before=" + before + " after=" + f.controller.position(),
                            alongCorner >= Math.floor(cadence * (run ? 0.25 : 0.12)));
                        assertTrue("one movement budget", travelled <= Math.ceil(cadence * (run ? 0.55 : 0.28)));
                        before = f.controller.position();
                    }
                }
                finally { f.controller.close(); }
            }
        }
    }

    @Test
    public void capturedMovingClickAndRepeatFollowNativeRefinementToTheReachableSide() throws Exception
    {
        for (boolean repeat : new boolean[] {false,true})
        {
            Fixture f = fixture();
            try
            {
                f.slowArrival = true; f.arrivalFinalSpeed = 49.8;
                f.controller.walkClick(); f.destination = p(5440,6336);
                for (int ms = 0; ms <= 580; ms += 20) { f.frame(ms); }
                f.authority = p(6208,6592); f.frame(600);
                f.at(620);
                f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION,"Open",1804,43,50));
                f.destination = p(5568,6464); f.frame(620);
                LocalPoint before = f.controller.position();
                for (int ms = 640; ms <= 4000; ms += 20)
                {
                    if (repeat && ms == 800)
                    {
                        f.at(ms);
                        f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION,"Open",1804,43,50));
                    }
                    if (ms == 1200) { f.authority = p(6080,6464); f.destination = p(5568,6336); }
                    if (ms == 1800) { f.authority = p(5824,6336); }
                    if (ms == 2400) { f.authority = p(5568,6336); f.destination = null; }
                    f.frame(ms);
                    LocalPoint at = f.controller.position();
                    int travel = Math.abs(at.getX()-before.getX()) + Math.abs(at.getY()-before.getY());
                    if (ms <= 1600) { assertTrue("no handoff brake at " + ms, travel >= 5); }
                    assertTrue("single frame budget", MovementPath.distance(before,at) <= 11);
                    assertNotEquals("the closed hinge is not an approach tile",p(5568,6464),at);
                    before = at;
                }
                assertEquals(p(5568,6336),f.controller.position());
            }
            finally { f.controller.close(); }
        }
    }

    private static Fixture fixture() throws Exception
    {
        Fixture f = new Fixture(6464,6720,8000,8000);
        f.speed = 1.18; f.smoothing = 0; f.catchUp = true; f.catchUpPercent = 15;
        f.slowAhead = true; f.slowAheadPercent = 13; f.originalWhenAligned = false;
        f.wall(1804,43,50);
        for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            MovingObjectHandoffTest.class.getResourceAsStream("door-130416-map.txt"), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.startsWith("#")) { continue; }
                String[] row = line.split(" "); int x = Integer.parseInt(row[0]), y = 0;
                for (String run : row[1].split(","))
                {
                    String[] pair = run.split(":"); int length = Integer.parseInt(pair[0]);
                    Arrays.fill(f.flags[x], y, y + length, Integer.parseUnsignedInt(pair[1],16)); y += length;
                }
                assertEquals(104,y);
            }
        }
        return f;
    }
}
