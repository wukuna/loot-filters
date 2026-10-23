package com.lootfilters;

import com.lootfilters.model.*;
import java.awt.Color;
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

public class ResolvedDisplayServiceTest {
    private final LootFiltersPlugin plugin=mock(LootFiltersPlugin.class);
    private final EventBus bus=mock(EventBus.class);
    private final Client client=mock(Client.class);
    private final Tile tile=mock(Tile.class);
    private final TileItemIndex items=mock(TileItemIndex.class);
    private final DisplayConfigIndex displays=mock(DisplayConfigIndex.class);
    private final IconIndex icons=mock(IconIndex.class);
    private final LootFiltersConfig config=mock(LootFiltersConfig.class);
    private final ResolvedDisplayService service=new ResolvedDisplayService();
    public ResolvedDisplayServiceTest() throws Exception {
        for(String name:new String[]{"plugin","eventBus"}) {
            Field f=ResolvedDisplayService.class.getDeclaredField(name); f.setAccessible(true);
            f.set(service,name.equals("plugin") ? plugin : bus);
        }
        when(plugin.getClient()).thenReturn(client); when(client.isClientThread()).thenReturn(true);
        when(plugin.getTileItemIndex()).thenReturn(items); when(plugin.getDisplayIndex()).thenReturn(displays);
        when(plugin.getIconIndex()).thenReturn(icons); when(plugin.getConfig()).thenReturn(config);
        when(config.compactRenderSize()).thenReturn(24);
    }
    private PluginTileItem item(DisplayConfig display) {
        PluginTileItem item=mock(PluginTileItem.class);
        when(item.getId()).thenReturn(4151); when(item.getQuantity()).thenReturn(1);
        when(displays.get(item)).thenReturn(display); return item;
    }
    private Map<String,Object> resolve(DisplayConfig display) {
        var item=item(display);
        when(items.findItem(tile,4151)).thenReturn(List.of(item));
        return service.resolve(tile,4151);
    }
    private static Map<?,?> display(Map<String,Object> reply) { return (Map<?,?>)reply.get("display"); }

    @Test public void exportsWholeEvaluatedDisplayAndResolvedMenuWithoutProviderObjects() {
        var trace=new ArrayList<>(List.of(3,9));
        var display=DisplayConfig.builder().textColor(Color.RED).backgroundColor(Color.BLACK).borderColor(Color.BLUE)
            .hidden(false).showLootbeam(true).showValue(true).compact(false).showDespawn(true).notify(true)
            .textAccent(TextAccent.OUTLINE).textAccentColor(Color.GREEN).lootbeamColor(Color.YELLOW).fontType(FontType.BOLD)
            .menuTextColor(Color.CYAN).hideOverlay(false).highlightTile(true).tileStrokeColor(Color.MAGENTA).tileFillColor(Color.GRAY)
            .sound(new SoundProvider.File("drop.wav")).menuSort(42).icon(new BufferedImageProvider.File("custom.png")).evalTrace(trace).build();
        var item=item(display); when(items.findItem(tile,4151)).thenReturn(List.of(item));
        var image=new BufferedImage(16,16,2); when(icons.get(display.getIcon().getCacheKey(item,16))).thenReturn(image);
        var reply=service.resolve(tile,4151); Map<?,?> snapshot=display(reply);
        assertEquals("ok",reply.get("status")); assertSame(image,reply.get("icon")); assertEquals("ready",reply.get("iconStatus"));
        assertEquals(Set.of("textColor","backgroundColor","borderColor","hidden","showLootbeam","showValue","compact","showDespawn","notify",
            "textAccent","textAccentColor","lootbeamColor","fontType","menuTextColor","hideOverlay","highlightTile","tileStrokeColor","tileFillColor","sound","menuSort","icon","evalTrace"),snapshot.keySet());
        assertEquals(Color.RED,snapshot.get("textColor")); assertEquals(Color.BLACK,snapshot.get("backgroundColor"));
        assertEquals(Color.BLUE,snapshot.get("borderColor")); assertEquals("OUTLINE",snapshot.get("textAccent")); assertEquals("BOLD",snapshot.get("fontType"));
        assertEquals(Color.GREEN,snapshot.get("textAccentColor")); assertEquals(Color.YELLOW,snapshot.get("lootbeamColor"));
        assertEquals(Color.MAGENTA,snapshot.get("tileStrokeColor")); assertEquals(Color.GRAY,snapshot.get("tileFillColor"));
        for(String field:List.of("showLootbeam","showValue","showDespawn","notify","highlightTile")) assertEquals(true,snapshot.get(field));
        for(String field:List.of("hidden","compact","hideOverlay")) assertEquals(false,snapshot.get(field));
        assertEquals(Map.of("type","file","filename","custom.png"),snapshot.get("icon"));
        assertEquals(Map.of("type","file","filename","drop.wav"),snapshot.get("sound"));
        assertEquals(Map.of("sort",42,"deprioritized",false,"textColor",Color.CYAN),reply.get("menu"));
        trace.add(100); assertEquals(List.of(3,9),snapshot.get("evalTrace"));
        try { snapshot.clear(); fail("snapshot must be read-only"); } catch(UnsupportedOperationException expected) { }
        try { ((Map<?,?>)snapshot.get("icon")).clear(); fail("descriptor must be read-only"); } catch(UnsupportedOperationException expected) { }
        verify(plugin,never()).getActiveFilter(); verify(plugin,never()).getAudioPlayer();
    }
    @Test public void hiddenAndOverlayOnlyAndNoIconAndNotReadyRemainDistinct() {
        var base=DisplayConfig.builder().icon(new BufferedImageProvider.File("same.png")).sound(new SoundProvider.SoundEffect(10))
            .notify(true).showLootbeam(true).highlightTile(true).build();
        when(config.deprioritizeHidden()).thenReturn(true); when(config.recolorHidden()).thenReturn(true); when(config.hiddenColor()).thenReturn(Color.GRAY);
        var hidden=resolve(base.toBuilder().hidden(true).build());
        assertEquals("ok",hidden.get("status")); assertEquals("hidden",hidden.get("iconStatus")); assertFalse(hidden.containsKey("icon"));
        assertEquals(true,display(hidden).get("hidden")); assertEquals(true,display(hidden).get("hideOverlay"));
        assertEquals(false,display(hidden).get("notify")); assertNull(display(hidden).get("sound"));
        assertEquals(Map.of("sort",0,"deprioritized",true,"textColor",Color.GRAY),hidden.get("menu"));
        var overlay=resolve(base.toBuilder().hideOverlay(true).build());
        assertEquals(false,display(overlay).get("hidden")); assertEquals("hidden",overlay.get("iconStatus"));
        assertEquals(false,((Map<?,?>)overlay.get("menu")).get("deprioritized"));
        assertEquals(Map.of("type","sound-effect","id",10),display(overlay).get("sound"));
        var none=resolve(DisplayConfig.builder().build()); assertEquals("none",none.get("iconStatus"));
        assertEquals(Color.WHITE,display(none).get("textColor")); assertEquals("NORMAL",display(none).get("fontType"));
        verifyNoInteractions(icons);
        var loading=resolve(base); assertEquals("ok",loading.get("status")); assertEquals("not-ready",loading.get("iconStatus"));
        assertNotNull(display(loading)); verify(plugin,never()).getActiveFilter();
    }
    @Test public void conflictingDisplaySettingsAreAmbiguousEvenWithSameIconInEitherOrder() {
        var base=DisplayConfig.builder().icon(new BufferedImageProvider.File("same.png")).build();
        for(DisplayConfig other:List.of(base.toBuilder().menuSort(1).build(),base.toBuilder().hidden(true).build(),
                base.toBuilder().textColor(Color.PINK).build(),base.toBuilder().sound(new SoundProvider.File("sound.wav")).build())) {
            var first=item(base); var second=item(other);
            when(items.findItem(tile,4151)).thenReturn(List.of(first,second)); assertEquals("ambiguous",service.resolve(tile,4151).get("status"));
            when(items.findItem(tile,4151)).thenReturn(List.of(second,first)); assertEquals("ambiguous",service.resolve(tile,4151).get("status"));
        }
        verifyNoInteractions(icons);
    }
    @Test public void equivalentStacksCanShareDisplayButMissingEvaluationCannot() {
        var base=DisplayConfig.builder().menuSort(2).build();
        var first=item(base); var second=item(base.toBuilder().build()); var missing=item(null);
        when(items.findItem(tile,4151)).thenReturn(List.of(first,second));
        assertEquals("ok",service.resolve(tile,4151).get("status"));
        when(items.findItem(tile,4151)).thenReturn(List.of(first,missing));
        assertEquals(Map.of("status","not-ready"),service.resolve(tile,4151));
        when(items.findItem(tile,4151)).thenReturn(List.of()); assertEquals(Map.of("status","not-found"),service.resolve(tile,4151));
    }
    @Test public void compactUsesExistingSizedCacheAndNativeDeduplication() {
        var display=DisplayConfig.builder().compact(true).build(); var item=item(display);
        when(items.findItem(tile,4151)).thenReturn(List.of(item));
        var image=new BufferedImage(24,24,2); when(icons.get(display.getIcon().getCacheKey(item,24))).thenReturn(image);
        var result=service.resolve(tile,4151); assertSame(image,result.get("icon")); assertEquals(true,result.get("itemIcon"));
        assertEquals(Map.of("type","current-item","id",4151,"quantity",1,"height",24),display(result).get("icon"));
    }
    @Test public void exportsSpriteAndFixedItemDescriptorsWithoutLoading() {
        assertEquals(Map.of("type","sprite","id",12,"index",2),display(resolve(DisplayConfig.builder().icon(new BufferedImageProvider.Sprite(12,2)).build())).get("icon"));
        var item=resolve(DisplayConfig.builder().icon(new BufferedImageProvider.Item(4151)).build());
        assertEquals(Map.of("type","item","id",4151,"height",16),display(item).get("icon")); assertEquals(true,item.get("itemIcon"));
        verify(plugin,never()).getSpriteManager(); verify(plugin,never()).getItemManager();
    }
    @Test public void validatesRequestsAndRepliesOnlyOnClientThread() {
        service.onPluginMessage(new PluginMessage("loot-filters","resolved-display-request-v1")); verifyNoInteractions(bus);
        var request=new PluginMessage("loot-filters","resolved-display-request-v1",Map.of("requestId","consumer:1","tile",tile,"itemId",4151));
        when(client.isClientThread()).thenReturn(false); service.onPluginMessage(request); verifyNoInteractions(bus);
        when(client.isClientThread()).thenReturn(true); service.onPluginMessage(request);
        var captured=org.mockito.ArgumentCaptor.forClass(PluginMessage.class); verify(bus).post(captured.capture());
        assertEquals("resolved-display-response-v1",captured.getValue().getName()); assertEquals("consumer:1",captured.getValue().getData().get("requestId"));
        assertEquals("not-found",captured.getValue().getData().get("status"));
    }
}
