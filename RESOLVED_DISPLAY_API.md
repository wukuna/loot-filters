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

An icon descriptor has `type` and its corresponding fields: `sprite` (`id`, `index`), `item` (`id`, `height`), `current-item` (`id`, `quantity`, `height`), or `file` (`filename`). It also includes nullable `image`, a cached BufferedImage. Hidden overlays and uncached images return null. No icon descriptor means no configured icon; consumers derive visibility and duplicate-item handling from the display flags and descriptor. Compact display resolves to `current-item`.

A sound descriptor has `type: sound-effect` with `id`, or `type: file` with `filename`. Unknown provider types use `type: unknown`. Filenames are descriptive, not an instruction to load files.

The display and nested collections are detached, read-only snapshots. Cached images are shared: copy their pixels before editing. Requests do not evaluate filters, load files/images, play sounds, or change rendering or menus. Temporary reveal keys, camera/distance visibility and menu preferences outside DisplayConfig are not represented.

Request only when needed. Results may be reused for identical rows within one menu-opening callback, but must not be retained across openings, filter/profile changes or scene changes.
