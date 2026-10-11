package com.responsivemovement;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.runelite.api.Client;
import net.runelite.client.config.Keybind;
import org.junit.Test;
import static org.junit.Assert.*;

public class TraceBookmarkTest
{
    @Test
    public void controllerBookmarkCapturesImmutableEvidenceWithoutMovementOrModelPreparation() throws Exception
    {
        List<MovementTrace.Entry> entries = new ArrayList<>();
        NpcApproachControllerTest.Fixture f = new NpcApproachControllerTest.Fixture(5952, 5952, 7000, 7000, entries);
        try
        {
            f.smoothing = 0; f.controller.walkClick(); f.destination = new net.runelite.api.coords.LocalPoint(6464, 5952, 0);
            f.frame(100);
            net.runelite.api.coords.LocalPoint before = f.controller.position();
            int angle = f.controller.orientation(), models = f.modelBuilds;
            long sequence = f.controller.bookmark("hotkey", f.now - 5_000_000L);
            assertTrue(sequence >= 0);
            assertEquals(before, f.controller.position());
            assertEquals(angle, f.controller.orientation());
            assertEquals(models, f.modelBuilds);
            TraceBookmark bookmark = (TraceBookmark) entries.stream().filter(e -> e instanceof TraceBookmark).findFirst().get();
            assertEquals(95000, bookmark.micros);
            f.frame(100); f.controller.bookmark("button", f.now);
            assertEquals("bookmarks cannot rearm the once-per-cycle position sample", 1,
                entries.stream().filter(e -> e instanceof MovementTrace.Sample)
                    .map(e -> (MovementTrace.Sample) e).filter(sample -> sample.cycle == 105).count());
            assertEquals(2, entries.stream().filter(e -> e instanceof TraceBookmark).count());
            f.speed = 2; f.destination = null; f.authority = new net.runelite.api.coords.LocalPoint(6720, 5952, 0);
            String line = CompletableFuture.supplyAsync(bookmark::line).get();
            assertTrue(line.startsWith("[RESPONSIVE-MOVEMENT-BOOKMARK]"));
            assertTrue(line.contains("source=hotkey"));
            assertTrue(line.contains("movementSpeed=5.0"));
            assertTrue(line.contains("destination=6464,5952"));
            assertTrue(line.contains("draw=" + before.getX() + "," + before.getY()));
            assertTrue(line.contains("logical="));
            String time = line.substring(line.indexOf(" time=") + 6).trim();
            assertEquals(bookmark.session + 95, OffsetDateTime.parse(time).toInstant().toEpochMilli());
        }
        finally { f.controller.close(); }
    }

    @Test
    public void disabledBookmarkNeverReadsClientStateOrResolvesStorage()
    {
        Client forbidden = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
            (object, method, args) -> { throw new AssertionError("Unexpected diagnostic read: " + method.getName()); });
        MovementTrace trace = new MovementTrace(() -> 0, () -> 0, batch -> { fail("Unexpected write"); return false; });
        ResponsiveMovementConfig config = new ResponsiveMovementConfig() {};
        assertEquals(-1, trace.bookmark(forbidden, config, 0, "button", 0, null, null));
        MovementController controller = new MovementController(forbidden, config, () -> 0, trace);
        assertEquals(-1, controller.bookmark("button", 0));
    }

    @Test
    public void hotkeyObservesOnePressWithoutConsumingAndReleasesAfterFocusLoss()
    {
        boolean[] enabled = {true}; int[] bookmarks = {0};
        TraceBookmarkKeyListener listener = new TraceBookmarkKeyListener(() -> new Keybind(KeyEvent.VK_F8, 0),
            () -> enabled[0], () -> ++bookmarks[0]);
        KeyEvent press = key(KeyEvent.KEY_PRESSED);
        listener.keyPressed(press); listener.keyPressed(press);
        assertEquals(1, bookmarks[0]); assertFalse(press.isConsumed());
        KeyEvent typed = key(KeyEvent.KEY_TYPED); listener.keyTyped(typed); assertFalse(typed.isConsumed());
        listener.keyReleased(key(KeyEvent.KEY_RELEASED)); listener.keyPressed(press);
        assertEquals(2, bookmarks[0]);
        listener.focusLost(); listener.keyPressed(press);
        assertEquals(3, bookmarks[0]);
        listener.focusLost(); enabled[0] = false; listener.keyPressed(press);
        assertEquals(3, bookmarks[0]);
        enabled[0] = true; press.consume(); listener.keyPressed(press);
        assertEquals(3, bookmarks[0]);
    }

    private static KeyEvent key(int id)
    {
        return new KeyEvent(new Canvas(), id, 0, 0, id == KeyEvent.KEY_TYPED ? KeyEvent.VK_UNDEFINED : KeyEvent.VK_F8,
            id == KeyEvent.KEY_TYPED ? 'x' : KeyEvent.CHAR_UNDEFINED)
        {
            @Override public int getExtendedKeyCode() { return id == KeyEvent.KEY_TYPED ? KeyEvent.VK_UNDEFINED : KeyEvent.VK_F8; }
        };
    }
}
