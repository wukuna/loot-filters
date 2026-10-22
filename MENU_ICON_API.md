# Menu icon compatibility proposal (v1)

Consumers may request the icon already selected by Loot Filters for an item menu row through RuneLite `PluginMessage`. There is no dependency on Loot Filters classes, file locations, or filter syntax.

On the client thread, post namespace `loot-filters`, name `menu-icon-request-v1`, with:

- `requestId`: a unique String owned by the requester.
- `tile`: the actual RuneLite `Tile` from the menu entry's world view and scene coordinates.
- `itemId`: the menu entry's integer item ID.

While Loot Filters is enabled, it replies synchronously on the same event bus with name `menu-icon-response-v1`, the same `requestId`, and `status`. Status is `ok`, `no-icon`, `not-ready`, or `ambiguous`. Only `ok` includes `icon` (a shared BufferedImage, which consumers must treat as read-only) and `itemIcon` (whether this is an image of the requested item, for avoiding duplicates).

Register the response listener before requesting. Match request IDs, discard unrelated replies, and do not retain results across menu openings. No reply means the installed Loot Filters does not support this protocol or is disabled. Fall back to native presentation. Do not poll each render frame.

The response uses the existing display and image caches, including default filters and custom images. It does not modify menu entries, colors, hiding, sounds, filter selection, settings, or files. It describes the selected icon, not whether a ground overlay is currently visible. Missing images are not loaded by this API. Different icons for same-ID stacks on a tile return `ambiguous` instead of guessing. Passing the actual Tile preserves world-view identity.

This is an opt-in consumer integration: without a request, Loot Filters performs no additional work. The plugin's existing subscriber lifecycle controls availability.
