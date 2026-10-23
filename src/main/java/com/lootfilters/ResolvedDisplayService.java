package com.lootfilters;

import com.lootfilters.model.BufferedImageProvider;
import com.lootfilters.model.FontType;
import com.lootfilters.model.SoundProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Tile;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/** On-demand snapshots of evaluated state. Never evaluates rules, loads images or plays sounds. */
class ResolvedDisplayService {
    @Inject private LootFiltersPlugin plugin;
    @Inject private EventBus eventBus;

    void onPluginMessage(PluginMessage message) {
        if (!"loot-filters".equals(message.getNamespace())
                || !"resolved-display-request-v1".equals(message.getName())
                || !plugin.getClient().isClientThread()) return;
        Map<String, Object> data = message.getData();
        if (!(data.get("requestId") instanceof String)
                || !(data.get("tile") instanceof Tile)
                || !(data.get("itemId") instanceof Integer)) return;
        Map<String, Object> reply = new HashMap<>(resolve((Tile) data.get("tile"), (Integer) data.get("itemId")));
        reply.put("requestId", data.get("requestId"));
        eventBus.post(new PluginMessage("loot-filters", "resolved-display-response-v1", Collections.unmodifiableMap(reply)));
    }

    Map<String, Object> resolve(Tile tile, int itemId) {
        var items = plugin.getTileItemIndex().findItem(tile, itemId);
        if (items == null || items.isEmpty()) return Map.of("status", "not-found");
        Map<String, Object> chosen = null;
        BufferedImageProvider.CacheKey chosenIcon = null;
        DisplayConfig chosenDisplay = null;
        for (var item : items) {
            var display = plugin.getDisplayIndex().get(item);
            if (display == null) return Map.of("status", "not-ready");
            var provider = display.getIcon();
            var key = provider == null ? null : provider.getCacheKey(item,
                    display.isCompact() ? plugin.getConfig().compactRenderSize() : 16);
            var snapshot = snapshot(display, key);
            // A menu row cannot identify an individual same-ID stack. Compare all exported settings.
            if (chosen != null && !chosen.equals(snapshot)) return Map.of("status", "ambiguous");
            chosen = snapshot;
            chosenIcon = key;
            chosenDisplay = display;
        }

        Map<String, Object> reply = new HashMap<>();
        reply.put("status", "ok");
        reply.put("display", chosen);
        reply.put("menu", Map.of(
                "sort", chosenDisplay.getMenuSort(),
                "deprioritized", plugin.getConfig().deprioritizeHidden() && chosenDisplay.isHidden(),
                "textColor", chosenDisplay.isHidden() && plugin.getConfig().recolorHidden()
                        ? plugin.getConfig().hiddenColor() : chosenDisplay.getMenuTextColor()));
        reply.put("itemIcon", chosenIcon != null && (chosenIcon.getType() == 1 || chosenIcon.getType() == 3)
                && chosenIcon.getParam0() == itemId);
        if (chosenDisplay.isHideOverlay()) {
            reply.put("iconStatus", "hidden");
        } else if (chosenIcon == null) {
            reply.put("iconStatus", "none");
        } else {
            var image = plugin.getIconIndex().get(chosenIcon);
            reply.put("iconStatus", image == null ? "not-ready" : "ready");
            if (image != null) reply.put("icon", image);
        }
        return Collections.unmodifiableMap(reply);
    }

    private static Map<String, Object> snapshot(DisplayConfig display, BufferedImageProvider.CacheKey icon) {
        Map<String, Object> result = new HashMap<>();
        result.put("textColor", display.getTextColor());
        result.put("backgroundColor", display.getBackgroundColor());
        result.put("borderColor", display.getBorderColor());
        result.put("hidden", display.isHidden());
        result.put("showLootbeam", display.isShowLootbeam());
        result.put("showValue", display.isShowValue());
        result.put("compact", display.isCompact());
        result.put("showDespawn", display.isShowDespawn());
        result.put("notify", display.isNotify());
        result.put("textAccent", display.getTextAccent() == null ? null : display.getTextAccent().name());
        result.put("textAccentColor", display.getTextAccentColor());
        result.put("lootbeamColor", display.getLootbeamColor());
        result.put("fontType", (display.getFontType() == null ? FontType.NORMAL : display.getFontType()).name());
        result.put("menuTextColor", display.getMenuTextColor());
        result.put("hideOverlay", display.isHideOverlay());
        result.put("highlightTile", display.isHighlightTile());
        result.put("tileStrokeColor", display.getTileStrokeColor());
        result.put("tileFillColor", display.getTileFillColor());
        result.put("sound", sound(display.getSound()));
        result.put("menuSort", display.getMenuSort());
        result.put("icon", icon(icon));
        result.put("evalTrace", display.getEvalTrace() == null ? List.of() : List.copyOf(display.getEvalTrace()));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Object> icon(BufferedImageProvider.CacheKey key) {
        if (key == null) return null;
        switch (key.getType()) {
            case 0: return Map.of("type", "sprite", "id", key.getParam0(), "index", key.getParam1());
            case 1: return Map.of("type", "item", "id", key.getParam0(), "height", key.getHeight());
            case 2: return Map.of("type", "file", "filename", key.getParam2());
            case 3: return Map.of("type", "current-item", "id", key.getParam0(), "quantity", key.getParam1(), "height", key.getHeight());
            default: return Map.of("type", "unknown");
        }
    }

    private static Map<String, Object> sound(SoundProvider sound) {
        if (sound instanceof SoundProvider.SoundEffect) return Map.of("type", "sound-effect", "id", ((SoundProvider.SoundEffect) sound).getId());
        if (sound instanceof SoundProvider.File) return Map.of("type", "file", "filename", ((SoundProvider.File) sound).getFilename());
        return sound == null ? null : Map.of("type", "unknown");
    }
}
