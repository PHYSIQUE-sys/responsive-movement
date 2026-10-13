package com.responsivemovement;

import net.runelite.api.Perspective;

/** Two configurable, continuous distance-based ramps on MovementPath's clock. */
final class ArrivalPacing
{
    static final ArrivalPacing OFF = new ArrivalPacing(0, 0, 100, 100);
    final double firstLength, finalLength, firstSpeed, finalSpeed;

    ArrivalPacing(double firstTiles, double finalTiles, double firstPercent, double finalPercent)
    {
        firstLength = bounded(firstTiles, 0, 10, 0.2) * Perspective.LOCAL_TILE_SIZE;
        finalLength = bounded(finalTiles, 0, 10, 1) * Perspective.LOCAL_TILE_SIZE;
        firstSpeed = bounded(firstPercent, 1, 100, 50) / 100;
        finalSpeed = bounded(finalPercent, 1, 100, 49.7) / 100;
    }

    static ArrivalPacing legacy(boolean run, int percent)
    {
        if (percent <= 0) { return OFF; }
        double first = run ? 50 : 75;
        return new ArrivalPacing(1, 1, first, first * (1 - Math.min(50, percent) / 100.0));
    }

    boolean enabled()
    {
        return length() > 0 && (firstLength > 0 && firstSpeed < 1 || finalLength > 0 && finalSpeed < 1);
    }

    double length() { return firstLength + finalLength; }

    ArrivalPacing fit(double tripLength)
    {
        if (length() <= tripLength) { return this; }
        double scale = tripLength / length();
        return new ArrivalPacing(firstLength * scale / Perspective.LOCAL_TILE_SIZE,
            finalLength * scale / Perspective.LOCAL_TILE_SIZE, firstSpeed * 100, finalSpeed * 100);
    }

    double speed(double remaining)
    {
        if (remaining > length()) { return 1; }
        if (remaining > finalLength)
        {
            return 1 + (firstSpeed - 1) * (length() - remaining) / firstLength;
        }
        double start = firstLength > 0 ? firstSpeed : 1;
        return finalLength > 0 ? start + (finalSpeed - start) * (finalLength - remaining) / finalLength : start;
    }

    double millis(double distance, double rate, double remaining)
    {
        double first = Math.min(distance, Math.max(0, remaining - finalLength));
        double start = firstLength > 0 ? firstSpeed : 1;
        return rampMillis(first, rate, speed(remaining), slope(remaining)) +
            rampMillis(distance - first, rate, first > 0 ? start : speed(remaining),
                finalLength > 0 ? (finalSpeed - start) / finalLength : 0);
    }

    double travel(double millis, double rate, double remaining)
    {
        double first = Math.max(0, remaining - finalLength);
        double firstTime = rampMillis(first, rate, speed(remaining), slope(remaining));
        if (millis < firstTime) { return rampTravel(millis, rate, speed(remaining), slope(remaining)); }
        double start = firstLength > 0 ? firstSpeed : 1;
        return first + rampTravel(millis - firstTime, rate, first > 0 ? start : speed(remaining),
            finalLength > 0 ? (finalSpeed - start) / finalLength : 0);
    }

    private double slope(double remaining)
    {
        if (remaining > finalLength) { return firstLength > 0 ? (firstSpeed - 1) / firstLength : 0; }
        return finalLength > 0 ? (finalSpeed - (firstLength > 0 ? firstSpeed : 1)) / finalLength : 0;
    }

    private static double rampMillis(double distance, double rate, double speed, double slope)
    {
        return Math.abs(slope) < 1e-12 ? distance / (rate * speed) : Math.log1p(slope * distance / speed) / (rate * slope);
    }

    private static double rampTravel(double millis, double rate, double speed, double slope)
    {
        return Math.abs(slope) < 1e-12 ? millis * rate * speed : speed * Math.expm1(rate * slope * millis) / slope;
    }

    private static double bounded(double value, double min, double max, double fallback)
    {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
