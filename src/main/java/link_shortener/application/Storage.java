package link_shortener.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import link_shortener.domain.ShortLink;

public interface Storage {
    boolean userExists(UUID userId);

    void saveUser(UUID userId);

    Optional<ShortLink> findByCode(String code);

    List<ShortLink> findByOwnerId(UUID ownerId);

    List<ShortLink> findExpired(Instant now);

    void save(ShortLink link);

    void delete(String code);
}
