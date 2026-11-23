package link_shortener.application;

@FunctionalInterface
public interface ShortCodeGenerator {
    String generate(int length);
}
