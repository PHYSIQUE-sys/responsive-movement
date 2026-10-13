package com.responsivemovement;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.KeyCode;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Click-time copies only. The asynchronous writer never receives a client, actor or live collision array. */
final class MovementTraceContext extends MovementTrace.Entry
{
    private static final int MAX_SIDE = 128;
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final int[][] collision;

    MovementTraceContext(long session, long sequence, long micros, Client client,
        ResponsiveMovementConfig config, int scene, String action, MenuOptionClicked event,
        LocalPoint visible, LocalPoint previousDestination)
    {
        super(session, sequence, micros);
        assert client.isClientThread();
        Player player = client.getLocalPlayer();
        WorldView view = player == null ? null : player.getWorldView();
        fields.put("schema", 2);
        fields.put("cycle", client.getGameCycle());
        fields.put("scene", scene);
        fields.put("clickAction", action);
        fields.put("inputBoundary", "MINIMAP".equals(action) ? "post-interface" : "menu-event");
        fields.put("option", event == null ? "Walk" : event.getMenuOption());
        fields.put("clickTarget", event == null ? -1 : event.getId());
        fields.put("param0", event == null ? -1 : event.getParam0());
        fields.put("param1", event == null ? -1 : event.getParam1());
        fields.put("menuWorldView", event == null ? -1 : event.getMenuEntry().getWorldViewId());
        fields.put("world", client.getWorld());
        fields.put("worldView", view == null ? -1 : view.getId());
        fields.put("plane", view == null ? -1 : view.getPlane());
        fields.put("baseX", view == null ? -1 : view.getBaseX());
        fields.put("baseY", view == null ? -1 : view.getBaseY());
        fields.put("instanced", view != null && view.isInstance());
        point("draw", visible);
        point("native", player == null ? null : player.getLocalLocation());
        WorldPoint authority = player == null ? null : player.getWorldLocation();
        fields.put("worldX", authority == null ? -1 : authority.getX());
        fields.put("worldY", authority == null ? -1 : authority.getY());
        fields.put("playerSize", player == null ? -1 : player.getFootprintSize());
        fields.put("animation", player == null ? -1 : player.getAnimation());
        fields.put("actionFrame", player == null ? -1 : player.getAnimationFrame());
        CombatEffectCarry.TraceSnapshot effects = CombatEffectCarry.snapshot(player);
        fields.put("spotEffects", effects.values); fields.put("spotEffectsComplete", effects.complete);
        point("previousDestination", previousDestination);
        point("destination", client.getLocalDestinationLocation());
        fields.put("runVarp", client.getVarpValue(VarPlayerID.OPTION_RUN));
        fields.put("energy", client.getEnergy());
        fields.put("control", client.isKeyPressed(KeyCode.KC_CONTROL));
        fields.put("combatStyle", client.getVarpValue(VarPlayerID.COM_MODE));
        fields.put("combatCategory", client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY));
        fields.put("autoRetaliate", client.getVarpValue(VarPlayerID.OPTION_NODEF) == 0);
        fields.put("responsiveStarts", config.responsiveStarts());
        fields.put("configuredSpeed", config.movementSpeed());
        fields.put("effectiveSpeed", MovementPath.configuredSpeed(config.movementSpeed()));
        fields.put("catchUp", config.catchUp());
        fields.put("catchUpPercent", config.catchUpPercent());
        fields.put("slowAhead", config.slowAhead());
        fields.put("slowAheadPercent", config.slowAheadPercent());
        fields.put("turnSpeed", config.turnSpeed());
        fields.put("faceInteractionsOnArrival", config.faceInteractionsOnArrival());
        fields.put("smoothingMs", config.clickSmoothingMs());
        fields.put("snapDistance", config.snapDistance());
        fields.put("originalWhenAligned", config.originalWhenAligned());
        fields.put("adaptiveCamera", config.adaptiveCamera());

        // Only the clicked NPC, including unsupported/provisional-start-ineligible options.
        NPC npc = event == null ? null : event.getMenuEntry().getNpc();
        fields.put("npcId", npc == null ? -1 : npc.getId());
        fields.put("npcIndex", npc == null ? -1 : npc.getIndex());
        fields.put("npcName", npc == null ? null : npc.getName());
        fields.put("npcDead", npc != null && npc.isDead());
        WorldView npcView = npc == null ? null : npc.getWorldView();
        fields.put("npcWorldView", npcView == null ? -1 : npcView.getId());
        WorldArea area = npc == null ? null : npc.getWorldArea();
        fields.put("npcWorldX", area == null ? -1 : area.getX());
        fields.put("npcWorldY", area == null ? -1 : area.getY());
        fields.put("npcPlane", area == null ? -1 : area.getPlane());
        fields.put("npcWidth", area == null ? -1 : area.getWidth());
        fields.put("npcHeight", area == null ? -1 : area.getHeight());

        int width = view == null ? 0 : Math.max(0, view.getSizeX());
        int height = view == null ? 0 : Math.max(0, view.getSizeY());
        CollisionData[] maps = view == null ? null : view.getCollisionMaps();
        int plane = view == null ? -1 : view.getPlane();
        int[][] flags = maps == null || plane < 0 || plane >= maps.length || maps[plane] == null
            ? null : maps[plane].getFlags();
        int copiedWidth = flags == null ? 0 : Math.min(MAX_SIDE, Math.min(width, flags.length));
        int copiedHeight = Math.min(MAX_SIDE, height);
        collision = new int[copiedWidth][];
        boolean complete = copiedWidth == width && width > 0 && copiedHeight == height && height > 0;
        for (int x = 0; x < copiedWidth; ++x)
        {
            if (flags[x] != null) { collision[x] = Arrays.copyOf(flags[x], Math.min(copiedHeight, flags[x].length)); }
            complete &= collision[x] != null && collision[x].length == height;
        }
        fields.put("sceneWidth", width);
        fields.put("sceneHeight", height);
        fields.put("collisionWidth", copiedWidth);
        fields.put("collisionHeightLimit", copiedHeight);
        fields.put("collisionComplete", complete);
    }

    private void point(String prefix, LocalPoint point)
    {
        fields.put(prefix + "X", point == null ? -1 : point.getX());
        fields.put(prefix + "Y", point == null ? -1 : point.getY());
        fields.put(prefix + "View", point == null ? -1 : point.getWorldView());
    }

    @Override
    String line()
    {
        StringBuilder text = new StringBuilder("[RESPONSIVE-MOVEMENT-CLICK]").append(identity());
        fields.forEach((key, value) -> text.append(' ').append(key).append('=').append(value instanceof String ? quote((String) value) :
            value instanceof long[] ? Arrays.toString((long[]) value).replace(" ", "") : value));
        text.append(timestamp());
        for (int x = 0; x < collision.length; ++x)
        {
            text.append("[RESPONSIVE-MOVEMENT-COLLISION] session=").append(session).append(" clickSeq=").append(sequence)
                .append(" x=").append(x).append(" yStart=0 flagsRle=");
            int[] column = collision[x];
            if (column == null) { text.append("missing"); }
            else
            {
                // Each token is count:hexFlags; tokens expand along increasing scene Y.
                for (int y = 0; y < column.length;)
                {
                    int end = y + 1;
                    while (end < column.length && column[end] == column[y]) { ++end; }
                    if (y > 0) { text.append(','); }
                    text.append(end - y).append(':').append(Integer.toHexString(column[y]));
                    y = end;
                }
            }
            text.append(System.lineSeparator());
        }
        return text.append("[RESPONSIVE-MOVEMENT-COLLISION-END] session=").append(session)
            .append(" clickSeq=").append(sequence).append(System.lineSeparator()).toString();
    }

    private static String quote(String value)
    {
        String bounded = value.substring(0, Math.min(160, value.length()));
        return '"' + bounded.replace("\\", "\\\\").replace("\"", "\\\"")
            .replaceAll("[\\p{Cntrl}]", " ") + '"';
    }
}
