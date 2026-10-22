package com.lootfilters;

import com.lootfilters.model.BufferedImageProvider;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import net.runelite.api.Tile;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/** Read-only, client-thread access to the icons already resolved by Loot Filters. */
class MenuIconService {
    @Inject private LootFiltersPlugin plugin;
    @Inject private EventBus eventBus;

    void onPluginMessage(PluginMessage message) {
        if (!"loot-filters".equals(message.getNamespace())
                || !"menu-icon-request-v1".equals(message.getName())
                || !plugin.getClient().isClientThread()) return;
        Map<String, Object> data = message.getData();
        if (!(data.get("requestId") instanceof String)
                || !(data.get("tile") instanceof Tile)
                || !(data.get("itemId") instanceof Integer)) return;
        Tile tile = (Tile) data.get("tile");
        int itemId = (Integer) data.get("itemId");
        Map<String, Object> reply = resolve(tile, itemId);
        reply.put("requestId", data.get("requestId"));
        eventBus.post(new PluginMessage("loot-filters", "menu-icon-response-v1", reply));
    }

    Map<String, Object> resolve(Tile tile, int itemId) {
        Map<String, Object> reply = new HashMap<>();
        reply.put("status", "no-icon");
        var items = plugin.getTileItemIndex().findItem(tile, itemId);
        if (items == null || items.isEmpty()) return reply;
        BufferedImageProvider.CacheKey chosen = null;
        boolean first = true;
        for (var item : items) {
            var display = plugin.getDisplayIndex().get(item);
            // Only consume existing evaluated state; never read files or re-evaluate rules here.
            if (display == null) { reply.put("status", "not-ready"); return reply; }
            var provider = display.getIcon();
            var key = provider == null ? null : provider.getCacheKey(item,
                    display.isCompact() ? plugin.getConfig().compactRenderSize() : 16);
            // A menu row cannot identify which same-ID stack it refers to.
            if (!first && !Objects.equals(chosen, key)) {
                reply.put("status", "ambiguous"); return reply;
            }
            chosen = key;
            first = false;
        }
        if (chosen == null) return reply;
        BufferedImage icon = plugin.getIconIndex().get(chosen);
        if (icon == null) { reply.put("status", "not-ready"); return reply; }
        reply.put("status", "ok");
        reply.put("icon", icon);
        reply.put("itemIcon", (chosen.getType() == 1 || chosen.getType() == 3)
                && chosen.getParam0() == itemId);
        return reply;
    }
}
