package com.responsivemovement;

import java.util.function.LongSupplier;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarClientID;

/** Render-only camera, preserving native input, zoom and terrain conventions. */
final class PresentationCamera
{
    private static final float REFERENCE_FRAME_MS = 16.667f;
    // Fixed original orb; the marker does not accept arbitrary cache model IDs.
    private static final int MARKER_MODEL = 3351;
    private final Client client;
    private final ResponsiveMovementConfig config;
    private final LongSupplier clock;
    private double worldX = Double.NaN, worldZ = Double.NaN;
    private float height = Float.NaN;
    private long lastNanos = -1;
    private volatile boolean detachedForDraw;
    private RuneLiteObject marker;
    private double markerPhase;
    private double markerOrientation;
    private long markerNanos;

    PresentationCamera(Client client, ResponsiveMovementConfig config)
    {
        this(client, config, System::nanoTime);
    }

    PresentationCamera(Client client, ResponsiveMovementConfig config, LongSupplier clock)
    {
        this.client = client; this.config = config; this.clock = clock;
    }

    void beforeInput()
    {
        if (detachedForDraw || config.adaptiveCamera()) { client.setCameraMode(0); }
        detachedForDraw = false;
    }

    void afterDraw()
    {
        if (detachedForDraw) { client.setCameraMode(0); detachedForDraw = false; }
    }

    void prepare(Player player, LocalPoint destination, boolean nativePresentation)
    {
        if (player == null || destination == null || !config.adaptiveCamera() || nativePresentation ||
            player.getWorldView() != client.getTopLevelWorldView())
        {
            synchronize(player);
            updateMarker(player, destination);
            return;
        }
        WorldPoint world = player.getWorldLocation();
        LocalPoint local = LocalPoint.fromWorld(player.getWorldView(), world);
        if (local == null) { synchronize(player); return; }
        long now = clock.getAsLong();
        float delta = lastNanos < 0 ? REFERENCE_FRAME_MS : Math.min(100, Math.max(0, now - lastNanos) / 1_000_000f);
        lastNanos = now;
        double offsetX = local.getX() - world.getX() * 128.0;
        double offsetZ = local.getY() - world.getY() * 128.0;
        double x = Double.isFinite(worldX) ? worldX + offsetX : client.getCameraFocalPointX();
        double z = Double.isFinite(worldZ) ? worldZ + offsetZ : client.getCameraFocalPointZ();
        if (!Float.isFinite(height)) { height = client.getCameraFocalPointY(); }
        float ground = footprintHeight(player.getWorldView(), destination, player.getWorldView().getPlane(), player.getFootprintSize());
        height = easeHeight(height, ground - player.getAnimationHeightOffset() - followHeight(), delta);
        double dx = destination.getX() - x, dz = destination.getY() - z;
        double distance = Math.hypot(dx, dz);
        double snap = finite(config.cameraSnap(), 5, 0.1, 100) * 128;
        if (distance > snap)
        {
            x = destination.getX(); z = destination.getY();
        }
        else if (distance > 0)
        {
            double fraction = followFraction(config.cameraVelocity(), config.followDistance(), delta);
            x += dx * fraction; z += dz * fraction;
        }
        client.setCameraMode(1);
        client.setFreeCameraSpeed(0);
        client.setCameraFocalPointX((float) x);
        client.setCameraFocalPointY(height);
        client.setCameraFocalPointZ((float) z);
        detachedForDraw = true;
        worldX = x - offsetX; worldZ = z - offsetZ;
        updateMarker(player, destination);
    }

    private void synchronize(Player player)
    {
        lastNanos = -1;
        if (client.getCameraMode() == 0 && player != null && player.getWorldView() != null)
        {
            WorldPoint world = player.getWorldLocation();
            LocalPoint local = LocalPoint.fromWorld(player.getWorldView(), world);
            if (local != null)
            {
                worldX = client.getCameraFocalPointX() - (local.getX() - world.getX() * 128.0);
                worldZ = client.getCameraFocalPointZ() - (local.getY() - world.getY() * 128.0);
                height = client.getCameraFocalPointY();
            }
        }
        beforeInput();
    }

    private int followHeight()
    {
        int small = clamp(client.getVarcIntValue(VarClientID.CAMERA_ZOOM_SMALL),
            client.getVarcIntValue(VarClientID.CAMERA_ZOOM_SMALL_MIN), client.getVarcIntValue(VarClientID.CAMERA_ZOOM_SMALL_MAX));
        int big = clamp(client.getVarcIntValue(VarClientID.CAMERA_ZOOM_BIG),
            client.getVarcIntValue(VarClientID.CAMERA_ZOOM_BIG_MIN), client.getVarcIntValue(VarClientID.CAMERA_ZOOM_BIG_MAX));
        return followHeight(small, big, client.getViewportHeight());
    }

    static int followHeight(int smallZoom, int bigZoom, int viewportHeight)
    {
        int blend = clamp(viewportHeight - 334, 0, 100);
        int zoom = smallZoom + (bigZoom - smallZoom) * blend / 100;
        return 25 + 25 * zoom / 256;
    }

    static float easeHeight(float current, float target, float millis)
    {
        if (!Float.isFinite(current)) { return target; }
        if (!Float.isFinite(target)) { return current; }
        double blend = 1 - Math.pow(0.5, Math.max(0, Math.min(100, millis)) / 80.0);
        float value = current + (target - current) * (float) blend;
        return Math.abs(target - value) < 0.01 ? target : value;
    }

    static double followFraction(double velocity, int distance, double millis)
    {
        if (millis <= 0) { return 0; }
        double reference = Math.min(1, finite(velocity, 4, 0, 100) / Math.max(1, distance));
        if (reference >= 1) { return 1; }
        // Match the former 60-FPS response, integrating its remaining-distance
        // decay exactly instead of applying a cadence-dependent linear step.
        return -Math.expm1(Math.log1p(-reference) * Math.min(100, millis) / REFERENCE_FRAME_MS);
    }

    static float tileHeight(WorldView view, float x, float y, int plane)
    {
        int tx = (int) (x / 128), ty = (int) (y / 128);
        if (tx < 0 || ty < 0 || tx >= view.getSizeX() || ty >= view.getSizeY()) { return 0; }
        if (plane < 3 && (view.getTileSettings()[1][tx][ty] & 2) != 0) { ++plane; }
        int[][] heights = view.getTileHeights()[plane];
        float ox = x % 128, oy = y % 128;
        float south = ((128 - ox) * heights[tx][ty] + ox * heights[tx + 1][ty]) / 128;
        float north = ((128 - ox) * heights[tx][ty + 1] + ox * heights[tx + 1][ty + 1]) / 128;
        return ((128 - oy) * south + oy * north) / 128;
    }

    private static float footprintHeight(WorldView view, LocalPoint p, int plane, int size)
    {
        float half = size / 2;
        float left = p.getX() - half, right = p.getX() + half, bottom = p.getY() - half, top = p.getY() + half;
        float result = tileHeight(view, p.getX(), p.getY(), plane);
        result = Math.min(result, tileHeight(view, left, bottom, plane));
        result = Math.min(result, tileHeight(view, left, top, plane));
        result = Math.min(result, tileHeight(view, right, bottom, plane));
        result = Math.min(result, tileHeight(view, right, top, plane));
        for (float tx = left / 128 + 1; tx <= right / 128; ++tx)
        {
            for (float ty = bottom / 128 + 1; ty <= top / 128; ++ty)
            {
                result = Math.min(result, tileHeight(view, tx * 128, ty * 128, plane));
            }
        }
        return result;
    }

    private void updateMarker(Player player, LocalPoint visible)
    {
        if (!config.cameraMarker() || player == null || player.getWorldView() == null ||
            player.getLocalLocation() == null || visible == null || visible.equals(player.getLocalLocation()))
        {
            if (marker != null) { marker.setActive(false); marker.setModel(null); }
            markerNanos = 0;
            return;
        }
        long now = clock.getAsLong();
        float delta = markerNanos == 0 ? REFERENCE_FRAME_MS : Math.min(100, Math.max(0, now - markerNanos) / 1_000_000f);
        markerNanos = now;
        if (marker == null) { marker = client.createRuneLiteObject(); }
        if (marker.getBaseModel() == null)
        {
            Model source = client.loadModel(MARKER_MODEL);
            if (source == null) { return; }
            // This cached, unanimated model is never transformed by the plugin.
            marker.setModel(source);
        }
        LocalPoint nativePoint = player.getLocalLocation();
        int angle = (MotionMath.heading(visible.getX() - nativePoint.getX(), visible.getY() - nativePoint.getY()) + 1024) & 2047;
        markerOrientation = MotionMath.turn(markerOrientation, angle, Math.max(0, config.markerTurnSpeed()) * delta / REFERENCE_FRAME_MS);
        markerPhase += delta * finite(config.markerSpeed(), 150, 0, 1000) * 0.0001;
        double offset = Math.sin(markerPhase) * finite(config.markerStrength(), 15, 0, 128);
        double radians = markerOrientation * Math.PI / 1024;
        LocalPoint point = new LocalPoint((int) (nativePoint.getX() - Math.sin(radians) * offset),
            (int) (nativePoint.getY() + Math.cos(radians) * offset), nativePoint.getWorldView());
        marker.setLocation(point, player.getWorldView().getPlane());
        marker.setOrientation((int) markerOrientation);
        marker.setZ(Perspective.getTileHeight(client, point, player.getWorldView().getPlane()) - config.markerHeight());
        if (!marker.isActive()) { marker.setActive(true); }
    }

    void sceneChanged()
    {
        beforeInput(); lastNanos = -1;
        markerNanos = 0;
        if (marker != null) { marker.setActive(false); marker = null; }
    }

    void close()
    {
        sceneChanged(); worldX = worldZ = Double.NaN; height = Float.NaN; markerPhase = markerOrientation = 0;
    }

    private static int clamp(int value, int low, int high) { return Math.max(low, Math.min(high, value)); }
    private static double finite(double value, double fallback, double low, double high)
    {
        return Double.isFinite(value) ? Math.max(low, Math.min(high, value)) : fallback;
    }
}
