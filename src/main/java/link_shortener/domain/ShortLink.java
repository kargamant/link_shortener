package link_shortener.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ShortLink {
    private final String code;
    private final URI originalUrl;
    private final UUID ownerId;
    private int transitionLimit;
    private int transitionCount;
    private final Instant expiresAt;

    public ShortLink(
            String code,
            URI originalUrl,
            UUID ownerId,
            int transitionLimit,
            int transitionCount,
            Instant expiresAt) {
        this.code = Objects.requireNonNull(code, "code");
        if (code.isBlank()) {
            throw new IllegalArgumentException("Short code cannot be empty");
        }
        this.originalUrl = Objects.requireNonNull(originalUrl, "originalUrl");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (transitionCount < 0) {
            throw new IllegalArgumentException("Transition count cannot be negative");
        }
        validateTransitionLimit(transitionLimit);
        this.transitionLimit = transitionLimit;
        this.transitionCount = transitionCount;
    }

    public String code() {
        return code;
    }

    public URI originalUrl() {
        return originalUrl;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public int transitionLimit() {
        return transitionLimit;
    }

    public int transitionCount() {
        return transitionCount;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean hasAvailableTransitions() {
        return transitionCount < transitionLimit;
    }

    public void registerTransition() {
        if (!hasAvailableTransitions()) {
            throw new IllegalStateException("Transition limit is expired");
        }
        transitionCount++;
    }

    public void changeTransitionLimit(int newLimit) {
        validateTransitionLimit(newLimit);
        transitionLimit = newLimit;
    }

    private static void validateTransitionLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Transition limit cannot be negative");
        }
    }
}
