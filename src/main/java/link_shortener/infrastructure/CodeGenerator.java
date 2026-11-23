package link_shortener.infrastructure;

import java.security.SecureRandom;
import java.util.UUID;

import link_shortener.application.ShortCodeGenerator;

public final class CodeGenerator implements ShortCodeGenerator {
    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate(int length, UUID uuid) {
        int alphabetSize = ALPHABET.length();

        // salt for alphabet rotation to add more randomness
        int offset = Math.floorMod(uuid.hashCode(), alphabetSize);
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = (random.nextInt(alphabetSize) + offset) % alphabetSize;
            code.append(ALPHABET.charAt(index));
        }
        return code.toString();
    }
}
