package link_shortener.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Duration;
import java.util.Properties;
import java.util.function.Function;

import link_shortener.application.ApplicationConfig;

public final class PropertiesConfigLoader {
    private static final String DEFAULT_RESOURCE = "/application.properties";

    public ApplicationConfig load() {
        InputStream resource = PropertiesConfigLoader.class.getResourceAsStream(DEFAULT_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException("Configuration resource not found: " + DEFAULT_RESOURCE);
        }
        try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return read(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + DEFAULT_RESOURCE, e);
        }
    }

    public ApplicationConfig load(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            return read(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read configuration file " + file, e);
        }
    }

    private static ApplicationConfig read(Reader reader) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        return new ApplicationConfig(
                readProperty(properties, "short-link.base-url", URI::create),
                readProperty(properties, "short-link.ttl", Duration::parse),
                readProperty(properties, "short-link.cleanup-interval", Duration::parse),
                readProperty(properties, "short-link.code-length", Integer::parseInt),
                readProperty(properties, "transition-limit.min", Integer::parseInt),
                readProperty(properties, "transition-limit.max", Integer::parseInt));
    }

    private static <T> T readProperty(
            Properties properties, String key, Function<String, T> parser) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing configuration property: " + key);
        }
        try {
            return parser.apply(value.strip());
        } catch (IllegalArgumentException | DateTimeException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid value for configuration property: " + key, e);
        }
    }
}
