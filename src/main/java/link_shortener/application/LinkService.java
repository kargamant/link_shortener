package link_shortener.application;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import link_shortener.domain.ShortLink;

public final class LinkService {
    private static final int MAX_CODE_ATTEMPTS = 100;

    private final Storage storage;
    private final ShortCodeGenerator codes;
    private final ApplicationConfig config;
    private final Clock clock;
    private final Consumer<URI> browser;

    public LinkService(
            Storage storage, ShortCodeGenerator codes, ApplicationConfig config, Clock clock) {
        this(storage, codes, config, clock, LinkService::openInBrowser);
    }

    public LinkService(
            Storage storage, ShortCodeGenerator codes,
            ApplicationConfig config, Clock clock, Consumer<URI> browser) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.codes = Objects.requireNonNull(codes, "codes");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.browser = Objects.requireNonNull(browser, "browser");
    }

    public ShortLink createLink(UUID currentUserId, URI originalUrl, int transitionLimit) {
        validateOriginalUrl(originalUrl);
        config.validateTransitionLimit(transitionLimit);

        synchronized (storage) {
            if (currentUserId != null && !storage.userExists(currentUserId)) {
                throw new IllegalArgumentException("Unknown user UUID");
            }
            String code = generateUniqueCode();
            UUID ownerId = currentUserId == null ? issueUserId() : currentUserId;
            Instant expiresAt = clock.instant().plus(config.ttl());
            ShortLink link = new ShortLink(code, originalUrl, ownerId, transitionLimit, 0, expiresAt);
            storage.save(link);
            return link;
        }
    }

    public boolean isKnownUser(UUID userId) {
        synchronized (storage) {
            return userId != null && storage.userExists(userId);
        }
    }

    public FollowResult followLink(String code) {
        synchronized (storage) {
            ShortLink link = storage.findByCode(code).orElse(null);
            if (link == null) {
                return FollowResult.NOT_FOUND;
            }
            if (link.isExpired(clock.instant())) {
                return FollowResult.EXPIRED;
            }
            if (!link.hasAvailableTransitions()) {
                return FollowResult.LIMIT_EXHAUSTED;
            }
            try {
                browser.accept(link.originalUrl());
            } catch (UncheckedIOException | UnsupportedOperationException | SecurityException e) {
                return FollowResult.OPEN_FAILED;
            }

            link.registerTransition();
            storage.save(link);
            return link.hasAvailableTransitions()
                    ? FollowResult.OPENED
                    : FollowResult.OPENED_LIMIT_REACHED;
        }
    }

    public List<ShortLink> findOwnedLinks(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        synchronized (storage) {
            return storage.findByOwnerId(ownerId);
        }
    }

    public void changeTransitionLimit(UUID ownerId, String code, int newLimit) {
        synchronized (storage) {
            ShortLink link = requireOwnedLink(ownerId, code);
            if (link.isExpired(clock.instant())) {
                throw new IllegalStateException("Link has expired");
            }
            config.validateTransitionLimit(newLimit);
            link.changeTransitionLimit(newLimit);
            storage.save(link);
        }
    }

    public void deleteLink(UUID ownerId, String code) {
        synchronized (storage) {
            ShortLink link = requireOwnedLink(ownerId, code);
            storage.delete(link.code());
        }
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codes.generate(config.codeLength());
            if (storage.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Can't generate an unused short code");
    }

    private UUID issueUserId() {
        UUID userId;
        do {
            userId = UUID.randomUUID();
        } while (storage.userExists(userId));
        storage.saveUser(userId);
        return userId;
    }

    private ShortLink requireOwnedLink(UUID ownerId, String code) {
        ShortLink link = storage.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("Short link not found"));
        if (!link.ownerId().equals(ownerId)) {
            throw new SecurityException("Only the owner may require this link");
        }
        return link;
    }

    private static void validateOriginalUrl(URI originalUrl) {
        Objects.requireNonNull(originalUrl, "originalUrl");
        String scheme = originalUrl.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || originalUrl.getHost() == null) {
            throw new IllegalArgumentException("Original URL must be an absolute HTTP(S) URL");
        }
    }

    private static void openInBrowser(URI url) {
        try {
            Desktop.getDesktop().browse(url);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public enum FollowResult {
        OPENED,
        OPENED_LIMIT_REACHED,
        NOT_FOUND,
        EXPIRED,
        LIMIT_EXHAUSTED,
        OPEN_FAILED
    }
}
