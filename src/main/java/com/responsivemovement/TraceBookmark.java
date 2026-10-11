package com.responsivemovement;

import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/** Value-only manual evidence; never prepares a model, replans or advances movement. */
final class TraceBookmark extends MovementTrace.Entry
{
    private final int cycle, scene, view, plane, baseX, baseY, world;
    private final String source;
    private final LocalPoint visible, nativePoint, authority, destination;
    private final Map<String, String> settings;
    private final MovementPath.TraceSnapshot route;
    private final long started, clickSequence, routeSequence;

    TraceBookmark(long session, long sequence, long micros, long started, long clickSequence, long routeSequence,
        Client client, ResponsiveMovementConfig config, int scene, String source, LocalPoint visible, MovementPath path)
    {
        super(session, sequence, micros);
        this.started = started; this.clickSequence = clickSequence; this.routeSequence = routeSequence;
        cycle = client.getGameCycle(); world = client.getWorld(); this.scene = scene;
        this.source = "hotkey".equals(source) ? "hotkey" : "button";
        Player player = client.getLocalPlayer();
        WorldView current = player == null ? null : player.getWorldView();
        view = current == null ? -1 : current.getId(); plane = current == null ? -1 : current.getPlane();
        baseX = current == null ? -1 : current.getBaseX(); baseY = current == null ? -1 : current.getBaseY();
        nativePoint = player == null ? null : player.getLocalLocation();
        WorldPoint point = player == null ? null : player.getWorldLocation();
        authority = current == null || point == null ? null : LocalPoint.fromWorld(current, point);
        this.visible = visible;
        destination = client.getLocalDestinationLocation();
        route = path == null ? null : path.traceSnapshot();
        settings = Map.copyOf(new MovementSettings(config).capture(true));
    }

    @Override String line()
    {
        StringBuilder text = new StringBuilder("[RESPONSIVE-MOVEMENT-BOOKMARK]").append(identity())
            .append(" schema=2 source=").append(source).append(" cycle=").append(cycle)
            .append(" scene=").append(scene).append(" world=").append(world).append(" worldView=").append(view)
            .append(" plane=").append(plane).append(" baseX=").append(baseX).append(" baseY=").append(baseY)
            .append(" clickSeq=").append(clickSequence).append(" routeSeq=").append(routeSequence)
            .append(" draw=").append(point(visible)).append(" true=").append(point(authority))
            .append(" native=").append(point(nativePoint)).append(" destination=").append(point(destination));
        settings.forEach((key, value) -> text.append(' ').append(key).append('=').append(value));
        if (route != null) { text.append(route.fields(started)); }
        return text.append(timestamp()).toString();
    }

    private static String point(LocalPoint point) { return point == null ? "-" : point.getX() + "," + point.getY(); }
}
