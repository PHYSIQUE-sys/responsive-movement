package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** All 17 newest marked sequences, with sparse native observations and fixed real input/tick times. */
public class LatestBookmarkReplayTest
{
    private static final long[] CADENCES = {8_333,20_000,33_333};
    private static LocalPoint p(int x, int y) { return new LocalPoint(x,y,0); }

    @Test
    public void all17BookmarksRetainTheirLatestGoalAndOneMovementAndFacingClock() throws Exception
    {
        List<List<String[]>> captures = captures(); Set<String> ids = new HashSet<>(); int runs = 0;
        for (List<String[]> capture : captures)
        {
            ids.addAll(Arrays.asList(capture.get(0)[1].split(",")));
            int toggles = Boolean.parseBoolean(capture.get(0)[6]) ? 7 : 3;
            for (long cadence : CADENCES)
            {
                for (boolean minimap : new boolean[] {false,true})
                {
                    replay(capture,5.9,toggles,cadence,minimap,0,false,true); ++runs;
                }
            }
        }
        assertEquals(17,ids.size()); assertEquals(84,runs);
    }

    @Test
    public void allCapturedSequencesConvergeAcrossSpeedAndIndependentPacingToggles() throws Exception
    {
        List<List<String[]>> captures = captures(); int runs = 0;
        for (double speed : new double[] {0.5,1,2.5,4.9,5,5.1,5.3,5.4,5.9,6,7.5,10})
        {
            for (int toggles = 0; toggles < 8; ++toggles)
            {
                for (List<String[]> capture : captures)
                {
                    for (long cadence : CADENCES)
                    {
                        for (boolean minimap : new boolean[] {false,true})
                        {
                            replay(capture,speed,toggles,cadence,minimap,0,false,false); ++runs;
                        }
                    }
                }
            }
        }
        assertEquals(8064,runs);
    }

    @Test
    public void liveSpeedPacingAndSmoothingChangesDoNotResetPositionOrSpendExtraTime() throws Exception
    {
        List<List<String[]>> captures = captures(); int runs = 0;
        for (int toggles = 0; toggles < 8; ++toggles)
        {
            for (List<String[]> capture : captures)
            {
                for (boolean minimap : new boolean[] {false,true})
                {
                    for (int smoothing : new int[] {0,50,150,300})
                    {
                        replay(capture,5.9,toggles,20_000,minimap,smoothing,true,false); ++runs;
                    }
                }
            }
        }
        assertEquals(896,runs);
    }

    private static List<List<String[]>> captures() throws Exception
    {
        List<List<String[]>> captures = new ArrayList<>(); List<String[]> current = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            LatestBookmarkReplayTest.class.getResourceAsStream("bookmark-authority-20261014.txt"),StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.startsWith("#") || line.isEmpty()) { continue; }
                String[] fields = line.split(" ");
                if (fields[0].equals("C")) { current = new ArrayList<>(); captures.add(current); }
                current.add(fields);
            }
        }
        assertEquals(14,captures.size());
        return captures;
    }

    private static void replay(List<String[]> rows, double speed, int toggles, long cadence,
        boolean minimap, int smoothing, boolean live, boolean strict)
    {
        String[] c = rows.get(0); long start = Long.parseLong(c[2]), end = Long.parseLong(c[5]);
        Fixture f = new Fixture(Integer.parseInt(c[3]),Integer.parseInt(c[4]),8000,8000);
        configure(f,speed,toggles,Integer.parseInt(c[7]));
        f.smoothing = smoothing; f.originalWhenAligned = false; f.arrivalFinalSpeed = 49.8;
        // Shared 27x14 crop retained from 424 later clicks in scene 1. For the
        // early windows this is an explicit collision double, not a claim that
        // all their individual collision publications survived rotation.
        for (int[] column : f.flags) { Arrays.fill(column,CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
        for (int x = 36; x <= 62; ++x) { for (int y = 43; y <= 56; ++y) { f.flags[x][y] = 0; } }
        int[][] nonzero = {{39,43,0x2010},{40,43,0x20100},{40,44,0x20100},{41,43,0x20502},{41,44,0x24120},
            {42,43,0x402},{42,44,0x4020},{44,54,0x20100},{44,55,0x20100},{45,54,0x20100},{45,55,0x20100},
            {47,43,0x402},{47,44,0x4020},{48,43,0x20502},{48,44,0x24120},{49,43,0x20100},{49,44,0x20100},
            {50,43,0x8040},{62,53,0x100},{62,54,0x100}};
        for (int[] cell : nonzero) { f.flags[cell[0]][cell[1]] = cell[2]; }
        TreeSet<Long> times = new TreeSet<>();
        for (long t = start; t <= end; t += cadence) { times.add(t); }
        for (int i = 1; i < rows.size(); ++i) { times.add(Long.parseLong(rows.get(i)[1])); }
        times.add(end);
        int next = 1, clicks = 0; long previousTime = start;
        LocalPoint previous = f.start, monitorFrom = null, monitorGoal = null;
        boolean nativeBefore = f.controller.nativeVisible();
        String context = "bookmarks="+c[1]+" speed="+speed+" toggles="+toggles+" cadence="+cadence+
            " minimap="+minimap+" smoothing="+smoothing+" live="+live;
        for (long t : times)
        {
            double intervalSpeed = f.speed, intervalBoost = f.catchUp ? f.catchUpPercent/100.0 : 0;
            f.now = 1_000_000_000L+(t-start)*1000; f.cycle = 100+(int)((t-start)/20_000);
            while (next < rows.size() && Long.parseLong(rows.get(next)[1]) <= t)
            {
                String[] event = rows.get(next++);
                if (event[0].equals("K"))
                {
                    monitorFrom = monitorGoal = null;
                    if (live)
                    {
                        f.controller.update(); LocalPoint beforeChange = f.controller.position();
                        ++clicks;
                        configure(f,clicks%2 == 0 ? 0.5 : 10,toggles ^ (clicks%8),clicks%2 == 0 ? 0 : 50);
                        f.catchUpPercent = clicks%2 == 0 ? 0 : 50;
                        f.originalWhenAligned = clicks%2 == 0;
                        f.controller.update();
                        assertEquals("live settings keep fraction "+context,beforeChange,f.controller.position());
                        f.controller.update();
                        assertEquals("repeat preparation spends zero extra time "+context,beforeChange,f.controller.position());
                    }
                    if (minimap) { f.controller.walkClick(f.destination); } else { f.controller.walkClick(); }
                }
                else
                {
                    LocalPoint authority = p(Integer.parseInt(event[2]),Integer.parseInt(event[3]));
                    boolean tick = !authority.equals(f.authority);
                    f.authority = authority;
                    f.destination = Integer.parseInt(event[4]) < 0 ? null : p(Integer.parseInt(event[4]),Integer.parseInt(event[5]));
                    f.nativePoint = p(Integer.parseInt(event[6]),Integer.parseInt(event[7]));
                    if (tick) { f.controller.gameTick(); }
                    if (strict && (t == 137783904L || t == 195403917L || t == 199004104L || t == 221202187L || t == 224202788L))
                    {
                        monitorFrom = f.controller.position();
                        monitorGoal = t == 137783904L ? p(6592,6592) : t == 195403917L ? p(4928,6720) :
                            t == 199004104L ? p(5184,6720) : t == 221202187L ? p(6464,6848) : p(6464,6720);
                    }
                }
            }
            int angle = f.controller.orientation(); f.controller.update(); f.controller.presented();
            LocalPoint at = f.controller.position();
            if (!nativeBefore && !f.controller.nativeVisible())
            {
                assertTrue("one movement clock "+context+" t="+t,MovementPath.distance(previous,at) <=
                    Math.ceil((t-previousTime)*0.0004*Math.max(intervalSpeed,f.speed)*
                        (1+Math.max(intervalBoost,f.catchUp ? f.catchUpPercent/100.0 : 0)))+1);
                if (!live)
                {
                    assertTrue("one capped facing clock "+context,Math.abs(MotionMath.difference(angle,f.controller.orientation())) <=
                        Math.ceil(30*(t-previousTime)/16_667.0)+1);
                }
            }
            if (monitorFrom != null)
            {
                assertTrue("authority cannot add a lateral detour "+context+" t="+t,between(at.getX(),monitorFrom.getX(),monitorGoal.getX()));
                assertTrue("authority cannot revisit an obsolete corner "+context+" t="+t,between(at.getY(),monitorFrom.getY(),monitorGoal.getY()));
            }
            previous = at; previousTime = t; nativeBefore = f.controller.nativeVisible();
        }
        // Explicit synthetic completion of native interpolation after the last
        // recorded endpoint; no missing server route is invented.
        f.destination = null; f.nativePoint = f.authority;
        long limit = end+(long)Math.ceil(8192/(0.2*f.speed*0.497))*1000+2_000_000;
        for (long t = end+100_000; !f.controller.position().equals(f.authority) && t <= limit; t += 100_000)
        {
            f.now = 1_000_000_000L+(t-start)*1000; f.cycle++;
            previous = f.controller.position(); f.controller.update();
            assertTrue("confirmed queue drains on the same clock "+context,MovementPath.distance(previous,f.controller.position()) <=
                Math.ceil(40*f.speed*(1+(f.catchUp ? f.catchUpPercent/100.0 : 0)))+1);
        }
        assertEquals("exact authoritative arrival "+context,f.authority,f.controller.position());
        for (int i = 0; i < 5; ++i)
        {
            f.now += 100_000_000L; f.cycle++; f.controller.update();
            assertEquals("no second itinerary after arrival "+context,f.authority,f.controller.position());
        }
        f.controller.close(); assertTrue("model cleanup",f.objects.isEmpty());
    }

    private static void configure(Fixture f, double speed, int toggles, int ahead)
    {
        f.speed = MovementPath.configuredSpeed(speed);
        f.catchUp = (toggles & 1) != 0; f.catchUpPercent = 15;
        f.slowAhead = (toggles & 2) != 0; f.slowAheadPercent = ahead;
        f.slowArrival = (toggles & 4) != 0;
    }

    private static boolean between(int value, int from, int to)
    {
        return value >= Math.min(from,to)-1 && value <= Math.max(from,to)+1;
    }
}
