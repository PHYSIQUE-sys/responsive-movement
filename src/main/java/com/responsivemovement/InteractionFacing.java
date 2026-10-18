package com.responsivemovement;

import net.runelite.api.MenuAction;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.MenuOptionClicked;

/** Click-scoped arrival-facing evidence; no route, positional advance or animation ownership. */
final class InteractionFacing
{
    private final WorldView view;
    private final int plane;
    private final NpcApproach npc;
    private final NativeApproach nativeTarget;
    private final LocalPoint min, max;
    private long lastApproachNanos;
    private boolean arrived;
    private long arrivalNanos;
    boolean retired;

    private InteractionFacing(WorldView view, NpcApproach npc, LocalPoint min, LocalPoint max, long now)
    {
        this(view,npc,null,min,max,now);
    }

    private InteractionFacing(WorldView view, NpcApproach npc, NativeApproach nativeTarget, LocalPoint min, LocalPoint max, long now)
    {
        this.view = view; plane = view.getPlane(); this.npc = npc;
        this.nativeTarget = nativeTarget;
        this.min = min; this.max = max; lastApproachNanos = now;
    }

    static InteractionFacing capture(WorldView view, MenuOptionClicked event, ObjectApproach object,
        NpcApproach npc, long now)
    {
        return capture(view,event,object,npc,null,now);
    }

    static InteractionFacing capture(WorldView view, MenuOptionClicked event, ObjectApproach object,
        NpcApproach npc, NativeApproach nativeTarget, long now)
    {
        if (view == null || event.getMenuEntry().getWorldViewId() != view.getId()) { return null; }
        if (npc != null)
        {
            return npc.combat == null ? new InteractionFacing(view, npc, npc.min, npc.max, now) : null;
        }
        if (nativeTarget != null && nativeTarget.hasIntent() && nativeTarget.adjacentIntent())
        {
            return new InteractionFacing(view,null,nativeTarget,nativeTarget.min,nativeTarget.max,now);
        }
        MenuAction action = event.getMenuAction();
        switch (action)
        {
            case GAME_OBJECT_FIRST_OPTION: case GAME_OBJECT_SECOND_OPTION: case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION: case GAME_OBJECT_FIFTH_OPTION:
            case ITEM_USE_ON_GAME_OBJECT: case WIDGET_TARGET_ON_GAME_OBJECT: break;
            default: return null;
        }
        int x = event.getParam0(), y = event.getParam1();
        if (x < 0 || y < 0 || x >= view.getSizeX() || y >= view.getSizeY()) { return null; }
        LocalPoint tile = new LocalPoint(x * 128 + 64, y * 128 + 64, view.getId());
        return new InteractionFacing(view, null, object == null ? tile : object.min,
            object == null ? tile : object.max, now);
    }

    LocalPoint target(WorldView current, MovementPath path, boolean pending, long now, int nativeAngle, int displayedAngle)
    {
        if (retired || current != view || view.getPlane() != plane || npc != null && !npc.valid(view) ||
            nativeTarget != null && (!nativeTarget.valid(view) || !nativeTarget.sameFootprint()) ||
            "recovery".equals(path.phase())) { retired = true; return null; }
        if (path.moving()) { lastApproachNanos = now; }
        LocalPoint at = path.position();
        int dx = Math.max(0, Math.max(min.getX() - at.getX(), at.getX() - max.getX()));
        int dy = Math.max(0, Math.max(min.getY() - at.getY(), at.getY() - max.getY()));
        int reserve = nativeTarget != null ? nativeTarget.reserve : npc == null ? 1 : npc.reserveTiles;
        // A stopped bounded forecast farther from the target is not an arrival.
        // Native catch-up need not finish before a checked visible stopping point faces its target.
        boolean boundary = Math.max(dx, dy) <= reserve * 128 && (dx != 0 || dy != 0);
        if (!arrived && !pending && !path.moving() && boundary)
        {
            arrived = true; arrivalNanos = now;
        }
        if (!arrived)
        {
            if (now - lastApproachNanos >= 1_800_000_000L) { retired = true; }
            return null;
        }
        if (path.moving() || !boundary)
        {
            // A later checked authoritative leg can finish closer than the
            // conservative staging stop. Travel regains the same facing budget.
            arrived = false; return null;
        }
        if (now - arrivalNanos >= 1_800_000_000L)
        {
            retired = true; return null;
        }
        LocalPoint point = nativeTarget != null ? nativeTarget.facingPoint() : npc == null ? new LocalPoint((min.getX() + max.getX()) / 2,
            (min.getY() + max.getY()) / 2, view.getId()) : npc.facingPoint();
        int heading = MotionMath.heading(point.getX() - at.getX(), point.getY() - at.getY());
        // Hand back only after both native and displayed facing agree; otherwise
        // an aligned idle handoff could interrupt the turn before the native tick.
        if (Math.abs(MotionMath.difference(heading, nativeAngle)) <= 10 &&
            Math.abs(MotionMath.difference(heading, displayedAngle)) <= 10)
        {
            retired = true; return null;
        }
        return point;
    }
}
