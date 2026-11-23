package link_shortener;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;

import link_shortener.application.ApplicationConfig;
import link_shortener.application.ExpiredLinkCleaner;
import link_shortener.application.LinkService;
import link_shortener.application.Storage;
import link_shortener.cli.ConsoleApplication;
import link_shortener.infrastructure.LocalFileStorage;
import link_shortener.infrastructure.PropertiesConfigLoader;

public final class Main {
    private static final Path STORAGE_FILE = Path.of("data", "links.properties");

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("usage: link_shortener.Main [config-file]");
            System.exit(1);
        }
        try {
            PropertiesConfigLoader loader = new PropertiesConfigLoader();
            ApplicationConfig config = args.length == 0 ? loader.load() : loader.load(Path.of(args[0]));
            Clock clock = Clock.systemUTC();
            Storage storage = new LocalFileStorage(STORAGE_FILE);
            LinkService links = new LinkService(storage, config, clock);
            ExpiredLinkCleaner cleaner = new ExpiredLinkCleaner(storage, clock);

            Console console = System.console();
            BufferedReader input = new BufferedReader(
                    console == null ? new InputStreamReader(System.in) : console.reader());
            PrintWriter output = console == null ? new PrintWriter(System.out, true) : console.writer();
            new ConsoleApplication(links, cleaner, config, clock, input, output).run();
        } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException
                 | SecurityException e) {
            System.err.println("Application error. " + e.getMessage());
            System.exit(1);
        }
    }
}
