package link_shortener.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import link_shortener.application.ApplicationConfig;
import link_shortener.application.ExpiredLinkCleaner;
import link_shortener.application.LinkService;
import link_shortener.domain.ShortLink;

public final class ConsoleApplication {
    private final LinkService links;
    private final ExpiredLinkCleaner cleaner;
    private final ApplicationConfig config;
    private final Clock clock;
    private final BufferedReader input;
    private final PrintWriter output;
    private final String shortLinkPrefix;
    private UUID currentUserId;

    public ConsoleApplication(
            LinkService links, ExpiredLinkCleaner cleaner, ApplicationConfig config,
            Clock clock, BufferedReader input, PrintWriter output) {
        this.links = Objects.requireNonNull(links, "links");
        this.cleaner = Objects.requireNonNull(cleaner, "cleaner");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
        this.shortLinkPrefix = config.shortLinkBaseUrl().normalize().toString();
    }

    public void run() throws IOException {
        cleanExpiredLinks();
        long interval = TimeUnit.NANOSECONDS.convert(config.cleanupInterval());
        try (ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()) {
            scheduler.scheduleWithFixedDelay(
                    this::cleanExpiredLinks, interval, interval, TimeUnit.NANOSECONDS);
            while (true) {
                output.print("> ");
                output.flush();
                String line = input.readLine();
                if (line == null || (!line.isBlank() && !execute(line.strip()))) {
                    return;
                }
            }
        }
    }

    private boolean execute(String line) {
        String[] parts = line.split("\\s+");
        try {
            if (parts[0].startsWith(shortLinkPrefix)) {
                requireArguments(parts, 0, "<short-link>");
                follow(parts[0]);
                return true;
            }
            switch (parts[0].toLowerCase(Locale.ROOT)) {
                case "create" -> {
                    requireArguments(parts, 2, "create <URL> <limit>");
                    create(parts[1], parts[2]);
                }
                case "open" -> {
                    requireArguments(parts, 1, "open <short-link-or-code>");
                    follow(parts[1]);
                }
                case "list" -> {
                    requireArguments(parts, 0, "list");
                    listLinks();
                }
                case "limit" -> {
                    requireArguments(parts, 2, "limit <short-link-or-code> <limit>");
                    links.changeTransitionLimit(
                            requireCurrentUser(), extractCode(parts[1]), parseLimit(parts[2]));
                    output.println("Visit limit updated.");
                }
                case "delete" -> {
                    requireArguments(parts, 1, "delete <short-link-or-code>");
                    links.deleteLink(requireCurrentUser(), extractCode(parts[1]));
                    output.println("Link deleted.");
                }
                case "use" -> {
                    requireArguments(parts, 1, "use <UUID>");
                    useUser(parts[1]);
                }
                case "new" -> {
                    requireArguments(parts, 0, "new");
                    currentUserId = null;
                    output.println("New session started. Create your first link to get a UUID.");
                }
                case "help" -> {
                    requireArguments(parts, 0, "help");
                    printHelp();
                }
                case "exit" -> {
                    requireArguments(parts, 0, "exit");
                    return false;
                }
                default -> output.println("Unknown command. Type help to see available commands.");
            }
        } catch (NoSuchElementException e) {
            output.println("Short link not found.");
        } catch (SecurityException e) {
            output.println("You do not have permission to perform this action.");
        } catch (UncheckedIOException e) {
            output.println("Storage error. " + e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException | DateTimeException
                 | ArithmeticException e) {
            output.println("Error. " + e.getMessage());
        }
        return true;
    }

    private void create(String url, String limit) {
        URI originalUrl;
        try {
            originalUrl = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL.", e);
        }
        ShortLink link = links.createLink(currentUserId, originalUrl, parseLimit(limit));
        if (currentUserId == null) {
            currentUserId = link.ownerId();
            output.println("Your UUID is " + currentUserId);
            output.println("Save it and type use <UUID> the next time you start the app.");
        }
        output.println("Short link — " + config.shortUrl(link.code()));
    }

    private void follow(String link) {
        String message = switch (links.followLink(extractCode(link))) {
            case OPENED -> "Sent the URL to the browser.";
            case OPENED_LIMIT_REACHED -> "Sent the URL to the browser. The visit limit has been reached.";
            case NOT_FOUND -> "Short link not found.";
            case EXPIRED -> "This link has expired.";
            case LIMIT_EXHAUSTED -> "The visit limit has been reached.";
            case OPEN_FAILED -> "Could not open the browser. This visit was not counted.";
        };
        output.println(message);
    }

    private void listLinks() {
        List<ShortLink> ownedLinks = links.findOwnedLinks(requireCurrentUser());
        if (ownedLinks.isEmpty()) {
            output.println("You have no links yet.");
            return;
        }
        Instant now = clock.instant();
        for (ShortLink link : ownedLinks) {
            String status;
            if (link.isExpired(now)) {
                status = "expired";
            } else if (!link.hasAvailableTransitions()) {
                status = "visit limit reached";
            } else {
                status = "active";
            }
            output.printf("%s → %s%n  %s, %d/%d visits, expires at %s%n",
                    config.shortUrl(link.code()), link.originalUrl(), status,
                    link.transitionCount(), link.transitionLimit(), link.expiresAt());
        }
    }

    private void useUser(String value) {
        UUID userId;
        try {
            userId = UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID.", e);
        }
        if (!links.isKnownUser(userId)) {
            throw new IllegalArgumentException("No user found with that UUID.");
        }
        currentUserId = userId;
        output.println("Current UUID — " + userId);
    }

    private UUID requireCurrentUser() {
        if (currentUserId == null) {
            throw new IllegalStateException("Type use <UUID> or create your first link.");
        }
        return currentUserId;
    }

    private String extractCode(String value) {
        String code = value.startsWith(shortLinkPrefix)
                ? value.substring(shortLinkPrefix.length())
                : value;
        if (!code.matches("[0-9A-Za-z]+")) {
            throw new IllegalArgumentException("Enter a short link from this service or its code.");
        }
        return code;
    }

    private static int parseLimit(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The visit limit must be an integer.", e);
        }
    }

    private static void requireArguments(String[] parts, int count, String usage) {
        if (parts.length != count + 1) {
            throw new IllegalArgumentException("Expected command format — " + usage);
        }
    }

    private void printHelp() {
        output.print("""
                create <URL> <limit>                Shorten a URL
                open <short-link-or-code>           Open the original URL
                list                                Show your links
                limit <short-link-or-code> <limit>  Change the visit limit for one of your links
                delete <short-link-or-code>         Delete one of your links
                use <UUID>                          Resume with an existing UUID
                new                                 Start a new user session
                help                                Show this help
                exit                                Quit

                """);
        output.printf("Example: create https://youtu.be/eOrb93UZfPU?si=sk9wBjP2jpc5Yrt2 %d%n",
                config.minTransitionLimit());
    }

    private void cleanExpiredLinks() {
        try {
            int removed = cleaner.removeExpiredLinks();
            if (removed > 0) {
                output.println("Expired links removed — " + removed);
                output.flush();
            }
        } catch (UncheckedIOException | SecurityException e) {
            output.println("Could not remove expired links. " + e.getMessage());
            output.flush();
        }
    }
}
