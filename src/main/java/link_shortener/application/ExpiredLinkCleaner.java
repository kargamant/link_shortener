package link_shortener.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import link_shortener.domain.ShortLink;

public final class ExpiredLinkCleaner {
    private final Storage storage;
    private final Clock clock;

    public ExpiredLinkCleaner(Storage storage, Clock clock) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public int removeExpiredLinks() {
        synchronized (storage) {
            List<ShortLink> expired = storage.findExpired(clock.instant());
            for (ShortLink link : expired) {
                storage.delete(link.code());
            }
            return expired.size();
        }
    }
}
