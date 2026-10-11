package com.responsivemovement;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
import org.junit.Test;

import static org.junit.Assert.*;

/** Test-only injection/API doubles; no reflective access ships in the plugin JAR. */
public class PluginFallbackTest
{
    @Test
    public void crowdedHitsplatsPublishNativeUiWithoutGivingUpCustomBodyMovement() throws Exception
    {
        NpcApproachControllerTest.Fixture f = new NpcApproachControllerTest.Fixture(6336, 6336, 6848, 6336);
        f.originalWhenAligned = false;
        ResponsiveMovementConfig config = new ResponsiveMovementConfig() {
            @Override public boolean adaptiveCamera() { return false; }
        };
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
            (object, method, args) -> {
                switch (method.getName())
                {
                    case "isGpu": return true;
                    case "getCameraMode": return 0;
                    case "getCameraFocalPointX": case "getCameraFocalPointY": case "getCameraFocalPointZ": return 0f;
                    default: return f.clientCall(method.getName(), args);
                }
            });
        ResponsiveMovementPlugin plugin = new ResponsiveMovementPlugin();
        MovementOverheads overheads = new MovementOverheads(client, plugin, config, null);
        inject(plugin, "client", client);
        inject(plugin, "config", config);
        inject(plugin, "movement", f.controller);
        inject(plugin, "camera", new PresentationCamera(client, config));
        inject(plugin, "overheads", overheads);
        inject(plugin, "active", true);
        try
        {
            for (int amount = 1; amount <= 4; ++amount) { overheads.hit(OverheadHitsplatsTest.hit(amount, 110)); }
            plugin.onBeforeRender(new BeforeRender());
            assertTrue(plugin.customUi());
            overheads.hit(OverheadHitsplatsTest.hit(100, 120));
            plugin.onBeforeRender(new BeforeRender());
            assertFalse(plugin.customUi());
            assertFalse(f.controller.nativeVisible());
            assertFalse(f.objects.isEmpty());
            f.frame(400); // cycle 120: every potentially visible hit has expired.
            overheads.tick();
            plugin.onBeforeRender(new BeforeRender());
            assertTrue(plugin.customUi());
            overheads.clear();
            assertTrue(overheads.ready());
        }
        finally { f.controller.close(); }
        assertTrue(f.objects.isEmpty());
    }

    @Test
    public void gpuLossBetweenRenderAndTickStillCleansUpThePresentation() throws Exception
    {
        NpcApproachControllerTest.Fixture f = new NpcApproachControllerTest.Fixture(6336, 6336, 6848, 6336);
        f.originalWhenAligned = false;
        f.frame(20);
        assertFalse(f.objects.isEmpty());
        ResponsiveMovementPlugin plugin = new ResponsiveMovementPlugin();
        ResponsiveMovementConfig config = new ResponsiveMovementConfig() {};
        int[] cameraRestores = {0};
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
            (object, method, args) -> {
                if (method.getName().equals("isGpu")) { return false; }
                if (method.getName().equals("setCameraMode"))
                {
                    assertEquals(0, args[0]); ++cameraRestores[0]; return null;
                }
                return f.clientCall(method.getName(), args);
            });
        inject(plugin, "client", client);
        inject(plugin, "movement", f.controller);
        inject(plugin, "camera", new PresentationCamera(client, config));
        inject(plugin, "overheads", new MovementOverheads(client, plugin, config, null));
        inject(plugin, "active", true);

        plugin.onBeforeRender(new BeforeRender());
        plugin.onClientTick(new ClientTick());
        assertFalse(plugin.supported());
        assertFalse(plugin.customUi());
        assertNull(plugin.position());
        assertTrue("native fallback must unregister the custom body and effects", f.objects.isEmpty());
        assertTrue("fallback restores native camera input mode", cameraRestores[0] > 0);
    }

    private static void inject(ResponsiveMovementPlugin plugin, String name, Object value) throws Exception
    {
        Field field = ResponsiveMovementPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
