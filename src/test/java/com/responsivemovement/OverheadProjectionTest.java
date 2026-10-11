package com.responsivemovement;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Player;
import net.runelite.api.Projection;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Execute supported Perspective projection rather than substituting top-level screen coordinates. */
public class OverheadProjectionTest
{
    @Test
    public void overheadsProjectThroughThePlayersSubWorldView()
    {
        Fixture f = new Fixture();
        assertTrue(f.renderHasPixels());
        assertArrayEquals(new float[] {1344, -65, 1344}, f.projected, 0);
    }

    @Test
    public void missingProjectionAndMismatchedViewSuppressOverheads()
    {
        Fixture f = new Fixture();
        f.projectionAvailable = false;
        assertFalse(f.renderHasPixels());
        assertNull(f.projected);
        f.projectionAvailable = true;
        f.point = new LocalPoint(1344, 1344, 8);
        assertFalse(f.renderHasPixels());
        assertNull(f.projected);
    }

    private interface Call { Object invoke(String name, Object[] args); }
    private static <T> T proxy(Class<T> type, Call call)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (object, method, args) -> call.invoke(method.getName(), args)));
    }

    private static final class Fixture
    {
        boolean projectionAvailable = true;
        float[] projected;
        LocalPoint point = new LocalPoint(1344, 1344, 7);
        final Projection projection = proxy(Projection.class, (name, args) -> {
            if (!name.equals("project")) { throw new AssertionError(name); }
            projected = new float[] {((Number) args[0]).floatValue(), ((Number) args[1]).floatValue(), ((Number) args[2]).floatValue()};
            return new float[] {0, 0, 100};
        });
        final WorldView view = proxy(WorldView.class, (name, args) -> {
            switch (name)
            {
                case "getId": return 7;
                case "getPlane": return 0;
                case "getSizeX": case "getSizeY": return 104;
                case "getTileSettings": return new byte[4][104][104];
                case "getTileHeight": return 42;
                case "getCanvasProjection": return projectionAvailable ? projection : null;
                default: throw new AssertionError(name);
            }
        });
        final WorldEntity entity = proxy(WorldEntity.class, (name, args) -> {
            if (name.equals("getWorldView")) { return view; }
            throw new AssertionError(name);
        });
        final WorldView top = proxy(WorldView.class, (name, args) -> {
            if (name.equals("worldEntities"))
            {
                return proxy(IndexedObjectSet.class, (method, values) -> {
                    assertEquals("byIndex", method);
                    assertEquals(7, values[0]);
                    return entity;
                });
            }
            throw new AssertionError(name);
        });
        final Player player = proxy(Player.class, (name, args) -> {
            switch (name)
            {
                case "getWorldView": return view;
                case "getFootprintSize": return 0;
                case "getLogicalHeight": return 100;
                case "getOverheadText": return "Test";
                case "getSkullIcon": return -1;
                case "getOverheadIcon": return HeadIcon.MAGIC;
                default: throw new AssertionError(name);
            }
        });
        final Client client = proxy(Client.class, (name, args) -> {
            switch (name)
            {
                case "getLocalPlayer": return player;
                case "getGameCycle": return 100;
                case "getWorldView": assertEquals(7, args[0]); return view;
                case "getTopLevelWorldView": return top;
                case "getViewportWidth": case "getViewportHeight": return 200;
                case "getViewportXOffset": case "getViewportYOffset": return 0;
                case "getScale": return 100;
                default: throw new AssertionError("top-level projection must not be used: " + name);
            }
        });

        boolean renderHasPixels()
        {
            ResponsiveMovementPlugin plugin = new ResponsiveMovementPlugin()
            {
                @Override boolean supported() { return true; }
                @Override boolean customUi() { return true; }
                @Override LocalPoint position() { return point; }
            };
            MovementOverheads overheads = new MovementOverheads(client, plugin, new ResponsiveMovementConfig() {}, null);
            BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try { overheads.render(graphics); }
            finally { graphics.dispose(); }
            for (int x = 0; x < 200; ++x)
            {
                for (int y = 0; y < 200; ++y) { if (image.getRGB(x, y) != 0) { return true; } }
            }
            return false;
        }
    }
}
