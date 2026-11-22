package com.responsivemovement;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.client.events.PluginMessage;

/** Synchronous, optional resize handoff; the normal painter remains the fallback. */
final class OverheadRenderer
{
    private final Consumer<PluginMessage> messages;

    OverheadRenderer(Consumer<PluginMessage> messages) { this.messages = messages; }

    void draw(Graphics2D graphics, String kind, int slot, Rectangle bounds, Consumer<Graphics2D> painter)
    {
        draw(graphics, kind, slot, bounds, null, painter);
    }

    BufferedImage artwork(int archive, int file)
    {
        BufferedImage[] image = {null};
        messages.accept(new PluginMessage("hd-ui", "request-overhead-artwork", Map.of("archive", archive, "file", file,
            "receiver", (Consumer<BufferedImage>) supplied -> image[0] = supplied)));
        return image[0];
    }

    void draw(Graphics2D graphics, String kind, int slot, Rectangle bounds, BufferedImage artwork, Consumer<Graphics2D> painter)
    {
        boolean[] handled = {false};
        Map<String, Object> data = new java.util.HashMap<>(Map.of(
            "kind", kind, "slot", slot, "bounds", new Rectangle(bounds), "graphics", graphics,
            "renderer", painter, "handled", (Runnable) () -> handled[0] = true));
        if (artwork != null) { data.put("artwork", artwork); }
        messages.accept(new PluginMessage("responsive-movement", "render-overhead", data));
        if (!handled[0]) { painter.accept(graphics); }
    }
}
