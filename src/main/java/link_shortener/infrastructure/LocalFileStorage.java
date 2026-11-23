package link_shortener.infrastructure;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

import link_shortener.application.Storage;
import link_shortener.domain.ShortLink;

public final class LocalFileStorage implements Storage {
    private static final String USER_PREFIX = "user.";
    private static final String LINK_PREFIX = "link.";

    private final Path file;
    private Properties data;

    public LocalFileStorage(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        if (this.file.getFileName() == null) {
            throw new IllegalArgumentException("Storage path must point to a file");
        }
        this.data = load();
    }

    @Override
    public synchronized boolean userExists(UUID userId) {
        return data.containsKey(USER_PREFIX + Objects.requireNonNull(userId, "userId"));
    }

    @Override
    public synchronized void saveUser(UUID userId) {
        writeEntry(USER_PREFIX + Objects.requireNonNull(userId, "userId"), "true");
    }

    @Override
    public synchronized Optional<ShortLink> findByCode(String code) {
        String value = data.getProperty(LINK_PREFIX + Objects.requireNonNull(code, "code"));
        return Optional.ofNullable(value).map(entry -> decodeLink(code, entry));
    }

    @Override
    public synchronized List<ShortLink> findByOwnerId(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return links().filter(link -> link.ownerId().equals(ownerId)).toList();
    }

    @Override
    public synchronized List<ShortLink> findExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        return links().filter(link -> link.isExpired(now)).toList();
    }

    @Override
    public synchronized void save(ShortLink link) {
        String value = String.join("\t",
                link.ownerId().toString(),
                Integer.toString(link.transitionLimit()),
                Integer.toString(link.transitionCount()),
                link.expiresAt().toString(),
                link.originalUrl().toString());
        writeEntry(LINK_PREFIX + link.code(), value);
    }

    @Override
    public synchronized void delete(String code) {
        writeEntry(LINK_PREFIX + Objects.requireNonNull(code, "code"), null);
    }

    private Stream<ShortLink> links() {
        return data.stringPropertyNames().stream()
                .filter(key -> key.startsWith(LINK_PREFIX))
                .sorted()
                .map(key -> decodeLink(key.substring(LINK_PREFIX.length()), data.getProperty(key)));
    }

    private Properties load() {
        Properties loaded = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            loaded.load(reader);
            for (String key : loaded.stringPropertyNames()) {
                if (key.startsWith(USER_PREFIX)) {
                    UUID.fromString(key.substring(USER_PREFIX.length()));
                } else if (key.startsWith(LINK_PREFIX)) {
                    decodeLink(key.substring(LINK_PREFIX.length()), loaded.getProperty(key));
                } else {
                    throw new IllegalArgumentException("Unknown storage entry: " + key);
                }
            }
            return loaded;
        } catch (NoSuchFileException e) {
            return loaded;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read storage file " + file, e);
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new IllegalStateException("Invalid storage file " + file, e);
        }
    }

    private static ShortLink decodeLink(String code, String value) {
        String[] fields = value.split("\t", -1);
        if (fields.length != 5) {
            throw new IllegalArgumentException("Invalid storage entry for link " + code);
        }
        return new ShortLink(
                code,
                URI.create(fields[4]),
                UUID.fromString(fields[0]),
                Integer.parseInt(fields[1]),
                Integer.parseInt(fields[2]),
                Instant.parse(fields[3]));
    }

    private void writeEntry(String key, String value) {
        if (Objects.equals(data.getProperty(key), value)) {
            return;
        }
        Properties updated = new Properties();
        updated.putAll(data);
        if (value == null) {
            updated.remove(key);
        } else {
            updated.setProperty(key, value);
        }
        write(updated);
        data = updated;
    }

    private void write(Properties updated) {
        Path temporaryFile = null;
        try {
            Path directory = file.getParent();
            Files.createDirectories(directory);
            temporaryFile = Files.createTempFile(directory, ".link-shortener-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporaryFile)) {
                updated.store(writer, "Link shortener storage");
            }
            try {
                Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException cleanupError) {
                    e.addSuppressed(cleanupError);
                }
            }
            throw new UncheckedIOException("Could not write storage file " + file, e);
        }
    }
}
