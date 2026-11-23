package link_shortener.application;

import java.util.UUID;

@FunctionalInterface
public interface ShortCodeGenerator {
    String generate(int length, UUID uuid);

    default String generate(int length) {
        return generate(length, new UUID(0L, 0L));
    }
}
