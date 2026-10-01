package pl.commercelink.web.dtos;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;

/** The Polish messages as the application formats them, for tests that pin the text an operator reads. */
final class PolishMessages {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    private PolishMessages() {
    }

    static String text(String key, Object... arguments) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/messages_pl.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        String pattern = properties.getProperty(key);
        if (pattern == null) {
            throw new IllegalArgumentException("No message " + key);
        }
        return arguments.length == 0 ? pattern : new MessageFormat(pattern, POLISH).format(arguments);
    }
}
