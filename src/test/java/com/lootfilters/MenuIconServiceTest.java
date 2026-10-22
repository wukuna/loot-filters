package com.lootfilters;

import com.lootfilters.model.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.Tile;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MenuIconServiceTest {
    private final LootFiltersPlugin plugin = mock(LootFiltersPlugin.class);
    private final EventBus bus = mock(EventBus.class);
    private final Client client = mock(Client.class);
    private final Tile tile = mock(Tile.class);
    private final TileItemIndex items = mock(TileItemIndex.class);
    private final DisplayConfigIndex displays = mock(DisplayConfigIndex.class);
    private final IconIndex icons = mock(IconIndex.class);
    private final MenuIconService service = new MenuIconService();
    public MenuIconServiceTest() throws Exception {
        for (String name : new String[]{"plugin", "eventBus"}) {
            Field f = MenuIconService.class.getDeclaredField(name); f.setAccessible(true);
            f.set(service, name.equals("plugin") ? plugin : bus);
        }
        when(plugin.getClient()).thenReturn(client); when(client.isClientThread()).thenReturn(true);
        when(plugin.getTileItemIndex()).thenReturn(items); when(plugin.getDisplayIndex()).thenReturn(displays);
        when(plugin.getIconIndex()).thenReturn(icons);
    }
    private PluginTileItem item(BufferedImageProvider provider) {
        PluginTileItem item = mock(PluginTileItem.class);
        when(item.getId()).thenReturn(4151);
        DisplayConfig display = mock(DisplayConfig.class); when(display.getIcon()).thenReturn(provider);
        when(displays.get(item)).thenReturn(display); return item;
    }
    @Test public void servesCachedCustomFileWithoutLoadingIt() {
        BufferedImageProvider provider = new BufferedImageProvider.File("custom.png");
        PluginTileItem item = item(provider);
        when(items.findItem(tile,4151)).thenReturn(List.of(item));
        BufferedImage image = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        when(icons.get(provider.getCacheKey(item,16))).thenReturn(image);
        Map<String,Object> reply=service.resolve(tile,4151);
        assertSame(image,reply.get("icon")); assertEquals("ok",reply.get("status"));
        assertEquals(false,reply.get("itemIcon"));
    }
    @Test public void refusesAmbiguousStacksIncludingMissingIcons() {
        var stacks=List.of(item(new BufferedImageProvider.File("a.png")),item(null));
        when(items.findItem(tile,4151)).thenReturn(stacks);
        assertEquals("ambiguous",service.resolve(tile,4151).get("status"));
        verifyNoInteractions(icons);
    }
    @Test public void nativeItemIsIdentifiedForDeduplication() {
        BufferedImageProvider provider=new BufferedImageProvider.CurrentItem(); PluginTileItem item=item(provider);
        when(items.findItem(tile,4151)).thenReturn(List.of(item));
        when(icons.get(provider.getCacheKey(item,16))).thenReturn(new BufferedImage(16,16,2));
        assertEquals(true,service.resolve(tile,4151).get("itemIcon"));
    }
    @Test public void missingCacheDoesNotLoadImages() {
        var stacks=List.of(item(new BufferedImageProvider.File("missing.png")));
        when(items.findItem(tile,4151)).thenReturn(stacks);
        assertEquals("not-ready",service.resolve(tile,4151).get("status"));
    }
    @Test public void repliesOnlyToValidClientThreadRequests() {
        service.onPluginMessage(new PluginMessage("loot-filters","menu-icon-request-v1"));
        verifyNoInteractions(bus);
        PluginMessage request=new PluginMessage("loot-filters","menu-icon-request-v1",Map.of("requestId","consumer:1","tile",tile,"itemId",4151));
        when(client.isClientThread()).thenReturn(false); service.onPluginMessage(request); verifyNoInteractions(bus);
        when(client.isClientThread()).thenReturn(true); service.onPluginMessage(request);
        org.mockito.ArgumentCaptor<PluginMessage> captured=org.mockito.ArgumentCaptor.forClass(PluginMessage.class);
        verify(bus).post(captured.capture());
        assertEquals("menu-icon-response-v1",captured.getValue().getName());
        assertEquals("consumer:1",captured.getValue().getData().get("requestId"));
        assertEquals("no-icon",captured.getValue().getData().get("status"));
    }
}
