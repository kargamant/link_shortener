package link_shortener.application;

import java.net.URI;
import java.util.UUID;

@FunctionalInterface
public interface ExpirationNotifier {
    void notifyExpired(UUID ownerId, URI shortUrl);
}
