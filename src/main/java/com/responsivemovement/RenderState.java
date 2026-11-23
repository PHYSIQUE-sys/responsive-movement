package com.responsivemovement;

import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.TileObject;

/** Immutable publication; render callbacks never inspect live controllers. */
final class RenderState
{
    static final RenderState NATIVE = new RenderState(null, -1, -1, false, false);
    final Player player;
    final int playerId;
    final int worldView;
    final boolean hideBody;
    final boolean hideUi;

    RenderState(Player player, int playerId, int worldView, boolean hideBody, boolean hideUi)
    {
        this.player = player; this.playerId = playerId; this.worldView = worldView;
        this.hideBody = hideBody; this.hideUi = hideUi;
    }

    static boolean isPlayer(long hash) { return ((hash >>> 16) & 7L) == 0; }

    boolean drawObject(long hash, TileObject object)
    {
        if (!hideBody) { return true; }
        // A temporary object can carry the exact actor even when its tag
        // doesn't identify it as the local player.
        if (player != null && object instanceof GameObject && ((GameObject) object).getRenderable() == player) { return false; }
        return !isPlayer(hash) || ((hash >>> 52) & 4095L) != worldView || object.getId() != playerId;
    }
}
