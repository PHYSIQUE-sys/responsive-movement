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

/** Route-quality assertions as well as convergence: pacing must not hide a bad connector. */
public class WalkDetourSettingsSweepTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a,b) -> true;
    private static final int[] CADENCES = {8,20,33};
    private static final int[][] CASES = {
        {7232,9792,7232,9536,6848,9920,450},
        {6720,9664,6848,9408,6720,9920,400},
        {7232,9408,6976,9664,7232,9792,400}};
    private static LocalPoint p(int x, int y) { return new LocalPoint(x,y,0); }

    @Test
    public void everySliderSpeedPacingToggleAndStageVariantKeepsDirectDeparturesChecked()
    {
        int runs = 0;
        for (int slider = 5; slider <= 100; ++slider)
        {
            for (int toggles = 0; toggles < 8; ++toggles)
            {
                for (int profile = 0; profile < 3; ++profile)
                {
                    for (int scenario = 0; scenario < CASES.length; ++scenario)
                    {
                        for (int cadence : CADENCES)
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
        assertEquals(41_472,runs);
    }

    private static void departure(double speed, int toggles, int profile, int scenario, int cadence, boolean run)
    {
        int[] c = CASES[scenario];
        MovementPath path = MovementPath.idle(p(c[0],c[1]),0,1.18,OPEN);
        assertTrue(path.accept(p(c[2],c[3]),true));
        for (int ms = 20; ms < c[6]; ms += 20) { path.advance(ms*1_000_000L); }
        path.advance(c[6]*1_000_000L);
        LocalPoint before = path.position(), goal = p(c[4],c[5]);
        long hard = path.predictionDeadlineNanos();
        path.speed(MovementPath.configuredSpeed(speed));
        assertEquals(before,path.position());
        path = path.retargetWalk(goal,run,c[6]*1_000_000L);
        String context = "speed="+speed+" toggles="+toggles+" profile="+profile+" scenario="+scenario+" cadence="+cadence+" run="+run;
        assertNotNull(context,path);
        assertEquals(context,before,path.position());
        assertEquals(context,hard,path.predictionDeadlineNanos());
        int boost = (toggles & 1) == 0 ? 0 : profile == 0 ? 15 : profile == 1 ? 0 : 50;
        int ahead = (toggles & 2) == 0 ? 0 : profile == 0 ? 13 : profile == 1 ? 0 : 50;
        ArrivalPacing arrival = (toggles & 4) == 0 ? ArrivalPacing.OFF : profile == 0 ?
            new ArrivalPacing(0.2,1,50,49.8) : profile == 1 ? new ArrivalPacing(0,0,100,100) :
            new ArrivalPacing(10,10,1,100);
        LocalPoint previous = before;
        for (int elapsed = cadence; elapsed <= 300; elapsed += cadence)
        {
            path.advance((c[6]+elapsed)*1_000_000L,false,boost,ahead,goal,arrival);
            LocalPoint at = path.position();
            assertTrue("one movement clock "+context,MovementPath.distance(previous,at) <=
                Math.ceil(cadence*0.4*(speed/5)*(1+boost/100.0))+1);
            assertTrue("no old side-anchor excursion "+context,between(at.getX(),before.getX(),goal.getX()));
            assertTrue("no old endpoint excursion "+context,between(at.getY(),before.getY(),goal.getY()));
            assertTrue("retained collision proofs "+context,path.clear());
            assertEquals("pacing never renews prediction "+context,hard,path.predictionDeadlineNanos());
            previous = at;
        }
    }

    @Test
    public void bothRecordedOffSettingTimelinesConvergeAtAllSpeedsAndAllPacingToggleCombinations() throws Exception
    {
        List<List<String[]>> captures = captures(); int runs = 0;
        for (int slider = 5; slider <= 100; ++slider)
        {
            for (int toggles = 0; toggles < 8; ++toggles)
            {
                for (List<String[]> capture : captures)
                {
                    for (boolean minimap : new boolean[] {false,true})
                    {
                        replay(capture,slider/10.0,toggles,20_000,minimap,0,true,false); ++runs;
                    }
                }
            }
        }
        assertEquals(3072,runs);
    }

    @Test
    public void liveSettingsChangesAndNativeOnlyStartsKeepTheirClockAcrossInputAndPresentationToggles() throws Exception
    {
        List<List<String[]>> captures = captures(); int runs = 0;
        for (int toggles = 0; toggles < 8; ++toggles)
        {
            for (List<String[]> capture : captures)
            {
                for (long cadence : new long[] {8_333,20_000,33_333})
                {
                    for (boolean minimap : new boolean[] {false,true})
                    {
                        for (int smoothing : new int[] {0,50,150,300})
                        {
                            for (boolean starts : new boolean[] {false,true})
                            {
                                replay(capture,5.9,toggles,cadence,minimap,smoothing,starts,true); ++runs;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(768,runs);
    }

    private static List<List<String[]>> captures() throws Exception
    {
        List<List<String[]>> captures = new ArrayList<>(); List<String[]> current = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            WalkDetourSettingsSweepTest.class.getResourceAsStream("bookmark-walk-detours-20261014.txt"),StandardCharsets.UTF_8)))
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
        return captures;
    }

    private static void replay(List<String[]> rows, double speed, int toggles, long cadence,
        boolean minimap, int smoothing, boolean starts, boolean live)
    {
        String[] c = rows.get(0); long start = Long.parseLong(c[2]), end = Long.parseLong(c[5]);
        Fixture f = new Fixture(Integer.parseInt(c[3]),Integer.parseInt(c[4]),8000,8000);
        configure(f,speed,toggles); f.starts = starts; f.smoothing = smoothing; f.originalWhenAligned = false;
        f.arrivalFinalSpeed = 49.8;
        // The complete 11x11 crop agrees across all 24 retained clicks in these
        // two windows. Cells beyond the copied crop are unavailable, not clear.
        for (int[] column : f.flags) { Arrays.fill(column,CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
        for (int x = 48; x <= 58; ++x) { for (int y = 72; y <= 82; ++y) { f.flags[x][y] = 0; } }
        int[][] nonzero = {{48,81,0x201},{48,82,0x220100},{49,82,0x201},
            {57,81,0x20100},{57,82,0x20100},{58,81,0x20100},{58,82,0x20100}};
        for (int[] cell : nonzero) { f.flags[cell[0]][cell[1]] = cell[2]; }
        TreeSet<Long> times = new TreeSet<>();
        for (long t = start; t <= end; t += cadence) { times.add(t); }
        for (int i = 1; i < rows.size(); ++i) { times.add(Long.parseLong(rows.get(i)[1])); }
        times.add(end);
        int next = 1, clicks = 0; long previousTime = start;
        LocalPoint previous = f.start, strictFrom = null, strictGoal = null;
        boolean nativeBefore = f.controller.nativeVisible();
        String context = "bookmark="+c[1]+" speed="+speed+" toggles="+toggles+" cadence="+cadence+
            " minimap="+minimap+" smoothing="+smoothing+" starts="+starts+" live="+live;
        for (long t : times)
        {
            double intervalSpeed = f.speed;
            double intervalBoost = f.catchUp ? f.catchUpPercent/100.0 : 0;
            f.now = 1_000_000_000L+(t-start)*1000; f.cycle = 100+(int)((t-start)/20_000);
            while (next < rows.size() && Long.parseLong(rows.get(next)[1]) <= t)
            {
                String[] event = rows.get(next++);
                if (event[0].equals("K"))
                {
                    strictFrom = strictGoal = null;
                    if (live)
                    {
                        // Spend elapsed time once before changing settings at
                        // this boundary, then verify zero-time application.
                        f.controller.update();
                        LocalPoint at = f.controller.position();
                        configure(f,++clicks%2 == 0 ? 0.5 : 10,toggles ^ (clicks%8));
                        f.originalWhenAligned = clicks%2 == 0; f.faceInteractionsOnArrival = clicks%2 == 0;
                        f.controller.update();
                        assertEquals("settings apply without positional reset "+context,at,f.controller.position());
                        f.controller.update();
                        assertEquals("repeated preparation spends no additional time "+context,at,f.controller.position());
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
                    if (!live && speed == 5.9 && starts && smoothing == 0 &&
                        (Long.parseLong(event[1]) == 1226752317L || Long.parseLong(event[1]) == 1251794090L))
                    {
                        strictFrom = f.controller.position(); strictGoal = f.destination;
                    }
                }
            }
            f.controller.update(); f.controller.presented();
            LocalPoint at = f.controller.position();
            if (!nativeBefore && !f.controller.nativeVisible())
            {
                assertTrue("one movement budget "+context+" t="+t,MovementPath.distance(previous,at) <=
                    Math.ceil((t-previousTime)*0.0004*Math.max(intervalSpeed,f.speed)*
                        (1+Math.max(intervalBoost,f.catchUp ? f.catchUpPercent/100.0 : 0)))+1);
            }
            if (strictFrom != null)
            {
                assertTrue("marked departure does not detour sideways "+context,between(at.getX(),strictFrom.getX(),strictGoal.getX()));
                assertTrue("marked departure does not revisit previous endpoint "+context,between(at.getY(),strictFrom.getY(),strictGoal.getY()));
            }
            previous = at; previousTime = t; nativeBefore = f.controller.nativeVisible();
        }
        // Native eventually settles; the capture's sparse native samples are not
        // extrapolated into a hidden route. Drain legitimate slow confirmed debt.
        f.destination = null; f.nativePoint = f.authority;
        long limit = end+(long)Math.ceil(2048/(0.2*f.speed*0.497))*1000+2_000_000;
        for (long t = end+100_000; !f.controller.position().equals(f.authority) && t <= limit; t += 100_000)
        {
            f.now = 1_000_000_000L+(t-start)*1000; f.cycle++;
            previous = f.controller.position(); f.controller.update();
            assertTrue("confirmed debt keeps its budget "+context,MovementPath.distance(previous,f.controller.position()) <=
                Math.ceil(40*f.speed*(1+(f.catchUp ? f.catchUpPercent/100.0 : 0)))+1);
        }
        assertEquals("eventual exact authoritative arrival "+context,f.authority,f.controller.position());
        for (int i = 0; i < 5; ++i)
        {
            f.now += 100_000_000L; f.cycle++; f.controller.update();
            assertEquals("no added trip after arrival "+context,f.authority,f.controller.position());
        }
        f.controller.close(); assertTrue("symmetric model cleanup",f.objects.isEmpty());
    }

    private static void configure(Fixture f, double speed, int toggles)
    {
        f.speed = MovementPath.configuredSpeed(speed);
        f.catchUp = (toggles & 1) != 0; f.catchUpPercent = 15;
        f.slowAhead = (toggles & 2) != 0; f.slowAheadPercent = 13;
        f.slowArrival = (toggles & 4) != 0;
    }

    private static boolean between(int value, int from, int to)
    {
        return value >= Math.min(from,to)-1 && value <= Math.max(from,to)+1;
    }
}
