# Resolved display messages (proposal v1)

Consumers can request the complete evaluated display settings for a ground item through RuneLite `PluginMessage`. This is an on-demand, client-thread snapshot of existing caches, not a subscription or another filter evaluation. No dependency on Loot Filters classes, parser, files or settings is required.

## Request and response

Post namespace `loot-filters`, name `resolved-display-request-v1`, with `requestId` (unique String), `tile` (the actual RuneLite Tile in the menu entry's world view), and `itemId` (Integer). Register your listener first. The enabled plugin responds synchronously with `resolved-display-response-v1`, the same `requestId`, and `status`:

- `ok`: evaluated settings are available, even when the item is hidden or has no icon.
- `not-found`: no matching tracked item at that Tile.
- `not-ready`: a matching item has not been evaluated yet.
- `ambiguous`: same-ID stacks have different exported settings or icon descriptors. The menu row cannot identify which stack it represents; no settings are returned.

No response means the provider is disabled or does not implement this proposal. Ignore unrelated or late replies. Do not retain a snapshot across menu openings, world-view changes, or profile/filter changes. Request only when needed, not every rendering frame; consumers can reuse one response for matching rows during a single menu-opening callback.

## Successful response

`display` is an unmodifiable `Map<String,Object>` with all fields below. Colors are `java.awt.Color`, booleans are Boolean, numbers are Integer. Nullable values are included as null, meaning unset in the evaluated config; consumers should apply their own presentation defaults. Booleans and getters follow `DisplayConfig` semantics (including suppression by `hidden`). This is the evaluated filter/config result, not a guarantee that a label is currently visible on screen.

| Fields | Value |
| --- | --- |
| textColor, menuTextColor | Non-null Color, including accessor defaults |
| backgroundColor, borderColor, textAccentColor, lootbeamColor, tileStrokeColor, tileFillColor | Nullable Color, including accessor fallback where defined |
| hidden, hideOverlay | Boolean; hidden also implies hideOverlay; overlay-only hiding does not imply menu hiding |
| showLootbeam, showValue, compact, showDespawn, notify, highlightTile | Boolean |
| textAccent | Nullable enum name String: USE_FILTER, SHADOW, OUTLINE, NONE, SHADOW_BOLD |
| fontType | Enum name String: USE_FILTER, NORMAL, LARGER, BOLD; absent config becomes NORMAL |
| menuSort | Integer, default 0; higher values sort nearer the top |
| icon | Nullable immutable descriptor map, below; compact resolves to current-item |
| sound | Nullable immutable descriptor map, below; hidden suppresses sound |
| evalTrace | Immutable copy of matched line/special-rule integers; empty when unavailable |

`menu` is an immutable map containing `sort` (Integer), `deprioritized` (Boolean), and `textColor` (Color). It applies the current Loot Filters `deprioritizeHidden`, `recolorHidden`, and `hiddenColor` preferences to the evaluated display, matching its menu composition. It does not include quantity/collapse behavior or transient menu mutations. `hidden` does not instruct a consumer to remove Take actions.

Icon descriptors use `type` plus:

- `sprite`: `id`, `index`.
- `item`: `id`, `height`.
- `current-item`: `id`, `quantity`, `height`.
- `file`: `filename` (informational only, not permission to access a file).

Sound descriptors use `type: sound-effect` with `id`, or `type: file` with `filename`. No provider objects or callbacks are exposed, and requesting a snapshot never plays a sound. Unknown future providers are represented as `type: unknown`.

Top-level `iconStatus` is `ready`, `hidden`, `none`, or `not-ready`. Only `ready` includes `icon`, a shared cached BufferedImage that consumers **must treat as read-only**. Copy pixels before modifying them. `itemIcon` is a Boolean identifying a picture of the requested item, to avoid duplicate native icons. Hidden overlays suppress the image but retain the descriptor and all evaluated display settings. Missing images do not make the rest of the display unavailable, and this interface does not trigger image loading.

All maps/lists are detached immutable snapshots. Color values are immutable; BufferedImage is the explicitly shared read-only exception. No reflection, external file access, filter evaluation, menu mutation, sound playback, or ground rendering is performed by this interface. Camera clipping, distance, temporary reveal hotkeys, and actual rendering visibility are not represented. Consumers choose which fields to honor and should leave unrelated features/settings unchanged.

This replaces the earlier unreleased `menu-icon-*-v1` proposal in this PR. Consumers of that draft must update both message names and payload handling; the old icon-only messages are not implemented alongside this proposal.
