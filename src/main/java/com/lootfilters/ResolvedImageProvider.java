package com.lootfilters;

import com.lootfilters.model.BufferedImageProvider;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.inject.Inject;
import net.runelite.client.util.ImageUtil;

/** Creates consumer-owned images without exposing the overlay's image cache. */
class ResolvedImageProvider {
    @Inject private LootFiltersPlugin plugin;
    @Inject private ScheduledExecutorService executor;
    private final AtomicLong generation = new AtomicLong();
    private final Set<CompletableFuture<BufferedImage>> pending = ConcurrentHashMap.newKeySet();

    void invalidate() {
        generation.incrementAndGet();
        pending.forEach(future -> future.complete(null));
        pending.clear();
    }

    Supplier<CompletableFuture<BufferedImage>> provider(BufferedImageProvider.CacheKey key) {
        long version = generation.get();
        return () -> load(key, version);
    }

    private CompletableFuture<BufferedImage> load(BufferedImageProvider.CacheKey key, long version) {
        CompletableFuture<BufferedImage> future = new CompletableFuture<>();
        if (version != generation.get() || pending.size() >= 128) {
            future.complete(null);
            return future;
        }
        pending.add(future);
        // Never leave a caller waiting indefinitely for an unavailable cache archive.
        var timeout = executor.schedule(() -> { future.complete(null); }, 5, TimeUnit.SECONDS);
        future.whenComplete((image, error) -> {
            pending.remove(future);
            timeout.cancel(false);
        });
        if (key.getType() == 2) {
            executor.execute(() -> {
                if (stale(future, version)) return;
                try {
                    finish(future, version, new BufferedImageProvider.File(key.getParam2()).getImage(plugin, null));
                } catch (RuntimeException e) {
                    future.completeExceptionally(e);
                }
            });
        } else {
            plugin.getClientThread().invokeLater(() -> {
                if (stale(future, version)) return true;
                try {
                    if (key.getType() == 0) {
                        BufferedImage image = plugin.getSpriteManager().getSprite(key.getParam0(), key.getParam1());
                        if (image == null) return false;
                        finish(future, version, image);
                    } else if (key.getType() == 1 || key.getType() == 3) {
                        var image = key.getType() == 1 ? plugin.getItemManager().getImage(key.getParam0())
                            : plugin.getItemManager().getImage(key.getParam0(), key.getParam1(), false);
                        image.onLoaded(() -> {
                            if (stale(future, version)) return;
                            try {
                                finish(future, version, ImageUtil.resizeImage(image,
                                    image.getWidth() * key.getHeight() / image.getHeight(), key.getHeight(), true));
                            } catch (RuntimeException e) {
                                future.completeExceptionally(e);
                            }
                        });
                    } else {
                        future.complete(null);
                    }
                } catch (RuntimeException e) {
                    future.completeExceptionally(e);
                }
                return true;
            });
        }
        return future;
    }

    private boolean stale(CompletableFuture<BufferedImage> future, long version) {
        if (version != generation.get()) future.complete(null);
        return future.isDone();
    }

    private void finish(CompletableFuture<BufferedImage> future, long version, BufferedImage image) {
        if (!stale(future, version)) future.complete(image == null ? null : copy(image));
    }

    static BufferedImage copy(BufferedImage image) {
        return new BufferedImage(image.getColorModel(), image.copyData(null), image.isAlphaPremultiplied(), null);
    }
}
