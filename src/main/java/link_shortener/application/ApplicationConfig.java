package link_shortener.application;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public record ApplicationConfig(
        URI shortLinkBaseUrl,
        Duration ttl,
        Duration cleanupInterval,
        int codeLength,
        int minTransitionLimit,
        int maxTransitionLimit) {

    public ApplicationConfig {
        Objects.requireNonNull(shortLinkBaseUrl, "shortLinkBaseUrl");
        String scheme = shortLinkBaseUrl.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || shortLinkBaseUrl.getHost() == null
                || shortLinkBaseUrl.getRawQuery() != null
                || shortLinkBaseUrl.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "Short link base URL must be a correct HTTP(S) URL");
        }
        String baseUrl = shortLinkBaseUrl.toString();
        if (!baseUrl.endsWith("/")) {
            shortLinkBaseUrl = URI.create(baseUrl + "/");
        }
        if (!Objects.requireNonNull(ttl, "ttl").isPositive()) {
            throw new IllegalArgumentException("TTL cannot be negative");
        }
        if (!Objects.requireNonNull(cleanupInterval, "cleanupInterval").isPositive()) {
            throw new IllegalArgumentException("Cleanup interval cannot be negative");
        }
        if (codeLength < 1) {
            throw new IllegalArgumentException("Short code length cannot be negative");
        }
        if (minTransitionLimit < 1 || maxTransitionLimit < minTransitionLimit) {
            throw new IllegalArgumentException("Invalid transition limit bounds");
        }
    }

    public URI shortUrl(String code) {
        return shortLinkBaseUrl.resolve(code);
    }

    void validateTransitionLimit(int limit) {
        if (limit < minTransitionLimit || limit > maxTransitionLimit) {
            throw new IllegalArgumentException(
                    "Transition limit must be between " + minTransitionLimit
                            + " and " + maxTransitionLimit);
        }
    }
}
