# Resolved display settings

Send a RuneLite `PluginMessage` on the client thread with namespace `loot-filters`, name `resolved-display-request-v1`, and a **mutable** data map:

```java
Map<String, Object> data = new HashMap<>();
data.put("x", worldX);
data.put("y", worldY);
data.put("z", plane);
data.put("itemId", itemId);
data.put("worldViewId", worldViewId); // optional; defaults to WorldView.TOPLEVEL
eventBus.post(new PluginMessage("loot-filters", "resolved-display-request-v1", data));
// Read data.get("status") and data.get("display") here, after post() returns.
```

All request values are Integer. Coordinates are the selected world view's base X/Y plus scene-tile X/Y, not instance-template coordinates; `z` is the plane. Include `worldViewId` for items in a nested world view. No Tile instance crosses the interface.

Loot Filters fills the same map during dispatch. There is no second event, reply listener or request ID. Use a fresh map for each request. No `status` means no compatible provider responded; otherwise it is `ok`, `not-found`, `not-ready` (not yet evaluated), or `ambiguous` (same-ID stacks have differing settings). Only `ok` includes `display`.

`display` contains the evaluated `DisplayConfig` fields below. These follow its accessor defaults and hidden-item suppression. There are no additional menu preferences or consumer-specific flags.

| Fields | Values |
| --- | --- |
| textColor, menuTextColor | Color |
| backgroundColor, borderColor, textAccentColor, lootbeamColor, tileStrokeColor, tileFillColor | Nullable Color |
| hidden, hideOverlay, showLootbeam, showValue, compact, showDespawn, notify, highlightTile | Boolean |
| textAccent | Nullable enum-name String |
| fontType | Enum-name String; defaults to NORMAL |
| menuSort | Integer; defaults to 0 |
| evalTrace | Read-only list of matched line/rule integers |
| icon, sound | Nullable descriptors, below |

An icon descriptor has `type` and its corresponding fields: `sprite` (`id`, `index`), `item` (`id`, `height`), `current-item` (`id`, `quantity`, `height`), or `file` (`filename`). It also includes `getImage`, a `Supplier<CompletableFuture<BufferedImage>>`. Calling it schedules loading and returns a future; never block waiting for it on the client thread. The future supplies a fresh independent image, or null if unavailable, stale or timed out. Loading errors may complete exceptionally. File loading runs on the injected executor; sprites and item images load on the client thread, with item images copied only after loading completes. No icon descriptor means no configured icon; consumers derive visibility and duplicate-item handling from the display flags and descriptor. Compact display resolves to `current-item`.

A sound descriptor has `type: sound-effect` with `id`, or `type: file` with `filename`. Unknown provider types use `type: unknown`. Filenames are descriptive, not an instruction to load files.

The display and nested collections are detached, read-only snapshots. Returned images belong to the caller and are not borrowed from the overlay cache. Requests do not evaluate filters, load files/images, play sounds, or change rendering or menus. Temporary reveal keys, camera/distance visibility and menu preferences outside DisplayConfig are not represented.

Request only when needed. Results may be reused for identical rows within one menu-opening callback, but must not be retained across openings, filter/profile changes or scene changes.

After resetting evaluated display state, Loot Filters posts `PluginMessage("loot-filters", "filter-reloaded", Map.of())` on the client thread. This covers reloading the same filename as well as changes that reset display settings. Discard cached images and stale pending results on receipt. Old image-provider handles complete with null after invalidation or shutdown. The notification is separate from the synchronous request/response map; it is not a response event.

Image provider calls do not retain Tile instances or live item objects: their immutable descriptor captures sprite/item IDs, quantity, filename and requested item-image height. Image requests time out after five seconds. Consumers should cache by descriptor, invalidate on changes and handle callbacks without assuming their completion thread.
