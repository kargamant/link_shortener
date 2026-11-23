package link_shortener.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import link_shortener.domain.ShortLink;

public final class ExpiredLinkCleaner {
    private final Storage storage;
    private final ExpirationNotifier notifier;
    private final ApplicationConfig config;
    private final Clock clock;

    public ExpiredLinkCleaner(
            Storage storage, ExpirationNotifier notifier,
            ApplicationConfig config, Clock clock) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public int removeExpiredLinks() {
        synchronized (storage) {
            List<ShortLink> expired = storage.findExpired(clock.instant());
            for (ShortLink link : expired) {
                storage.delete(link.code());
                notifier.notifyExpired(link.ownerId(), config.shortUrl(link.code()));
            }
            return expired.size();
        }
    }
}
