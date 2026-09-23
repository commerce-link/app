package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMessagesTest {

    /** A lone apostrophe in a MessageFormat pattern swallows the placeholder that follows it; a doubled one prints. */
    private static final Pattern LONE_APOSTROPHE = Pattern.compile("(?<!')'(?!')");

    private static final Pattern DOUBLED_APOSTROPHE = Pattern.compile("''");

    /**
     * D-M25/RF-30: a {@code MessageFormat} placeholder immediately followed by "produkt"/"pozycja" and its endings
     * reads wrong for most of the counts it can be given at runtime ("1 produktów", "3 produkty" are both
     * ungrammatical), because Polish inflects the noun by count in three classes (1 / 2-4 / 5+) that a single
     * placeholder cannot agree with. The fix is the label form ("Produkty do usunięcia: {0}") instead of a number
     * directly in front of the noun. A literal digit written into the text (e.g. "Powyżej 0 produktów") is not
     * flagged here: unlike a placeholder it never changes at runtime, so it is either right or wrong on its own.
     */
    private static final Pattern NUMBER_BEFORE_INFLECTED_NOUN =
            Pattern.compile("\\{\\d+\\}\\s*(produkt(y|ów|u)?|pozycj(i|ę|e|a)?)\\b");

    /** The messages of the catalog pages: their own keys and the product keys the product page reads. */
    private static Map<String, String> catalogMessages(String file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources", file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("catalog.") || key.startsWith("product."))
                .collect(Collectors.toMap(key -> key, properties::getProperty));
    }

    @Test
    void polishAndEnglishDefineTheSameCatalogKeys() throws Exception {
        // when / then
        assertThat(catalogMessages("messages_pl.properties").keySet())
                .isEqualTo(catalogMessages("messages_en.properties").keySet());
    }

    @Test
    void noCatalogMessageWithAPlaceholderHidesItBehindAnApostrophe() {
        // given
        Set<String> broken = Stream.of("messages_pl.properties", "messages_en.properties")
                .flatMap(CatalogMessagesTest::messagesOf)
                .filter(entry -> entry.getValue().contains("{") && LONE_APOSTROPHE.matcher(entry.getValue()).find())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // when / then
        assertThat(broken).isEmpty();
    }

    /**
     * The mirror image of the rule above. The application's {@code ResourceBundleMessageSource}
     * ({@code starter} LocalizationConfig) leaves {@code alwaysUseMessageFormat} off and nothing sets
     * {@code spring.messages.always-use-message-format}, so a message resolved without arguments is returned raw:
     * doubling the apostrophe in a text that has no placeholder would print it as two characters on the page.
     */
    @Test
    void noCatalogMessageWithoutAPlaceholderDoublesItsApostrophe() {
        // given
        Set<String> broken = Stream.of("messages_pl.properties", "messages_en.properties")
                .flatMap(CatalogMessagesTest::messagesOf)
                .filter(entry -> !entry.getValue().contains("{") && DOUBLED_APOSTROPHE.matcher(entry.getValue()).find())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // when / then
        assertThat(broken).isEmpty();
    }

    /**
     * D-M47: the catalog templates show an amount through {@code general.currency.amount}, not a literal " PLN"; the
     * key lives outside the catalog prefixes, so both languages are checked here. {@code general.currency} itself is
     * the word "Waluta" of other forms and is left as it is.
     */
    @Test
    void theAmountWithItsCurrencyIsAMessageInBothLanguages() throws Exception {
        // given
        Properties polish = new Properties();
        Properties english = new Properties();
        try (Reader pl = Files.newBufferedReader(Path.of("src/main/resources/messages_pl.properties"), StandardCharsets.UTF_8);
             Reader en = Files.newBufferedReader(Path.of("src/main/resources/messages_en.properties"), StandardCharsets.UTF_8)) {
            polish.load(pl);
            english.load(en);
        }

        // when / then
        assertThat(polish.getProperty("general.currency.amount")).isEqualTo("{0} PLN");
        assertThat(english.getProperty("general.currency.amount")).isEqualTo("{0} PLN");
    }

    /** D-M25/RF-30: no wrong Polish plural anywhere in a catalog message. */
    @Test
    void noPolishCatalogMessageInflectsAPluralNounDirectlyAfterItsNumberPlaceholder() throws Exception {
        // given
        Set<String> broken = catalogMessages("messages_pl.properties").entrySet().stream()
                .filter(entry -> NUMBER_BEFORE_INFLECTED_NOUN.matcher(entry.getValue()).find())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // when / then
        assertThat(broken).isEmpty();
    }

    private static Stream<Map.Entry<String, String>> messagesOf(String file) {
        try {
            return catalogMessages(file).entrySet().stream();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** D-M30: the example under the suppliers of the maximum price, as the accepted mock-up words it. */
    @Test
    void theSuppliersExampleIsTheMockUpsInBothLanguages() throws Exception {
        // when / then
        assertThat(catalogMessages("messages_pl.properties").get("product.page.mrpSuppliers.placeholder"))
                .isEqualTo("np. Acme, Elko");
        assertThat(catalogMessages("messages_en.properties").get("product.page.mrpSuppliers.placeholder"))
                .isEqualTo("e.g. Acme, Elko");
    }
}
