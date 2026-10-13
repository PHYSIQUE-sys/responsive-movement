package com.responsivemovement;

import java.util.List;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Parameter sweeps for finite pacing, short-trip isolation and one-clock integration. */
public class ArrivalSettingsSweepTest
{
    private static final LocalPoint START = tile(10, 10);
    private static final int[] CADENCES = {8, 20, 33, 100};
    private static final ResponsiveMovementConfig DEFAULTS = new ResponsiveMovementConfig() {};

    @Test
    public void cleanDefaultsAndInvalidValueFallbacksMatchTheUserConfirmedSettings()
    {
        assertEquals(0.2, DEFAULTS.arrivalFirstTiles(), 0);
        assertEquals(50, DEFAULTS.arrivalFirstSpeed(), 0);
        assertEquals(1, DEFAULTS.arrivalFinalTiles(), 0);
        assertEquals(49.7, DEFAULTS.arrivalFinalSpeed(), 0);
        assertFalse(DEFAULTS.slowArrival());
        ArrivalPacing fallback = new ArrivalPacing(Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, Double.NEGATIVE_INFINITY);
        assertEquals(0.2 * 128, fallback.firstLength, 0);
        assertEquals(128, fallback.finalLength, 0);
        assertEquals(0.5, fallback.firstSpeed, 0);
        assertEquals(0.497, fallback.finalSpeed, 1e-12);
        assertEquals("49.7", new MovementSettings(DEFAULTS).capture(false).get("arrivalFinalSpeed"));
    }

    @Test
    public void stageRangeSweepHasFinitePositiveDurationsAndReversibleDistanceIntegration()
    {
        for (double firstLength : new double[] {0, 0.000001, 0.2, 1, 10})
        {
            for (double finalLength : new double[] {0, 0.000001, 0.2, 1, 10})
            {
                for (double firstSpeed : new double[] {1, 49.7, 50, 99.9, 100})
                {
                    for (double finalSpeed : new double[] {1, 49.7, 50, 99.9, 100})
                    {
                        for (double rate : new double[] {0.02, 0.2, 0.4, 0.8})
                        {
                            ArrivalPacing pacing = new ArrivalPacing(firstLength, finalLength, firstSpeed, finalSpeed).fit(3 * 128);
                            double remaining = pacing.length();
                            String context = "first=" + firstLength + "/" + firstSpeed + " final=" + finalLength + "/" + finalSpeed + " rate=" + rate;
                            for (double fraction : new double[] {0, 0.125, 0.5, 0.99999, 1})
                            {
                                double distance = remaining * fraction;
                                double duration = pacing.millis(distance, rate, remaining);
                                assertTrue(context, Double.isFinite(duration) && duration >= 0);
                                if (distance > 0) { assertTrue(context, duration > 0); }
                                assertEquals(context, distance, pacing.travel(duration, rate, remaining), 1e-6);
                                double split = distance * 0.37;
                                assertEquals(context, duration, pacing.millis(split, rate, remaining) +
                                    pacing.millis(distance - split, rate, remaining - split), Math.max(1e-8, duration * 1e-9));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void newDefaultsAcrossMovementSpeedCatchUpAheadAndCadenceKeepRoutesAndArrivalsConsistent()
    {
        ArrivalPacing pacing = defaults();
        for (double configuredSpeed : new double[] {0.5, 5, 10})
        {
            double speed = MovementPath.configuredSpeed(configuredSpeed);
            for (boolean run : new boolean[] {false, true})
            {
                for (LocalPoint goal : new LocalPoint[] {tile(11, 10), tile(12, 12), tile(12, 11), tile(13, 13), tile(15, 10), tile(14, 12)})
                {
                    List<LocalPoint> route = MovementPath.checkedRoute(START, goal, (a, b) -> true, 64);
                    for (int catchUp : new int[] {0, 15, 50})
                    {
                        for (int ahead : new int[] {0, 13, 50})
                        {
                            LocalPoint[] reference = null;
                            for (int cadence : CADENCES)
                            {
                                MovementPath path = confirmed(route, run, speed);
                                MovementPath off = route.size() < 3 ? confirmed(route, run, speed) : null;
                                String context = "speed=" + configuredSpeed + " run=" + run + " goal=" + goal + " catchUp=" + catchUp + " ahead=" + ahead + " cadence=" + cadence;
                                int from = 0;
                                LocalPoint[] samples = new LocalPoint[3];
                                for (int sample = 0; sample < samples.length; ++sample)
                                {
                                    int to = (int) Math.ceil(new int[] {600, 1300, 1600}[sample] * (run ? 1 : 2) / speed);
                                    advance(path, from, to, cadence, goal, catchUp, ahead, pacing);
                                    samples[sample] = path.position();
                                    assertTrue(context, path.clear());
                                    assertEquals(context, goal, path.confirmed());
                                    if (off != null)
                                    {
                                        advance(off, from, to, cadence, goal, catchUp, ahead, ArrivalPacing.OFF);
                                        assertEquals(context, off.position(), path.position());
                                        assertEquals(context, off.presentationRunning(), path.presentationRunning());
                                        assertTrue(context, off.traceSnapshot().same(path.traceSnapshot()));
                                    }
                                    from = to;
                                }
                                if (reference == null) { reference = samples; }
                                for (int i = 0; i < samples.length; ++i) { near(context, reference[i], samples[i]); }
                                int end = (int) Math.ceil(route.size() * 128 / (0.2 * speed * 0.497)) + 100;
                                advance(path, from, Math.max(from, end), cadence, null, catchUp, ahead, pacing);
                                assertEquals(context, goal, path.position());
                                assertTrue(context, path.finished());
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void previewConfirmationSweepExercisesAheadPacingWithoutRenewingPredictionOnFrames()
    {
        LocalPoint goal = tile(15, 10);
        for (double configuredSpeed : new double[] {0.5, 5, 10})
        {
            double speed = MovementPath.configuredSpeed(configuredSpeed);
            for (int catchUp : new int[] {0, 15, 50})
            {
                for (int ahead : new int[] {0, 13, 50})
                {
                    LocalPoint reference = null;
                    for (int cadence : CADENCES)
                    {
                        MovementPath path = MovementPath.anticipate(START, START, goal, true, 0, speed,
                            MovementPath.freshDeadline(0), (a, b) -> true);
                        assertNotNull(path);
                        String context = "preview speed=" + configuredSpeed + " catchUp=" + catchUp + " ahead=" + ahead + " cadence=" + cadence;
                        int from = 0;
                        for (int x : new int[] {12, 14, 15})
                        {
                            int to = from + 600;
                            long deadline = path.predictionDeadlineNanos();
                            advance(path, from, to, cadence, goal, catchUp, ahead, defaults());
                            assertEquals(context, deadline, path.predictionDeadlineNanos());
                            assertTrue(context, path.accept(tile(x, 10), true));
                            assertTrue(context, path.clear());
                            from = to;
                        }
                        int sample = Math.max(1800, (int) Math.ceil(1700 / speed));
                        advance(path, from, sample, cadence, null, catchUp, ahead, defaults());
                        if (reference == null) { reference = path.position(); }
                        near(context, reference, path.position());
                        advance(path, sample, Math.max(sample, (int) Math.ceil(640 / (0.2 * speed * 0.497)) + 100),
                            cadence, null, catchUp, ahead, defaults());
                        assertEquals(context, goal, path.position());
                        assertTrue(context, path.finished());
                    }
                }
            }
        }
    }

    private static ArrivalPacing defaults()
    {
        return new ArrivalPacing(DEFAULTS.arrivalFirstTiles(), DEFAULTS.arrivalFinalTiles(),
            DEFAULTS.arrivalFirstSpeed(), DEFAULTS.arrivalFinalSpeed());
    }

    private static MovementPath confirmed(List<LocalPoint> route, boolean run, double speed)
    {
        MovementPath path = MovementPath.idle(START, 0, speed, (a, b) -> true);
        for (int i = 0; i < route.size(); i += run ? 2 : 1)
        {
            assertTrue(path.accept(route.get(Math.min(route.size() - 1, i + (run ? 1 : 0))), run));
        }
        return path;
    }

    private static void advance(MovementPath path, int from, int to, int cadence, LocalPoint goal,
        int catchUp, int ahead, ArrivalPacing pacing)
    {
        for (int ms = from + cadence; ms < to; ms += cadence)
        {
            path.advance(ms * 1_000_000L, false, catchUp, ahead, goal, pacing);
        }
        path.advance(to * 1_000_000L, false, catchUp, ahead, goal, pacing);
    }

    private static void near(String context, LocalPoint expected, LocalPoint actual)
    {
        assertTrue(context + " x", Math.abs(expected.getX() - actual.getX()) <= 1);
        assertTrue(context + " y", Math.abs(expected.getY() - actual.getY()) <= 1);
    }

    private static LocalPoint tile(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }
}
