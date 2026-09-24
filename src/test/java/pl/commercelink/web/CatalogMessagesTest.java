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
     * D-M25/RF-30: a {@code MessageFormat} placeholder immediately followed by "produkt"/"pozycja"/"minuta" and
     * their endings reads wrong for most of the counts it can be given at runtime ("1 produktów", "3 produkty",
     * "{1} minut" are all ungrammatical for some count), because Polish inflects the noun by count in three
     * classes (1 / 2-4 / 5+) that a single placeholder cannot agree with. The fix is either the label form
     * ("Produkty do usunięcia: {0}") instead of a number directly in front of the noun, or the invariant
     * abbreviation ("co {1} min", already the convention in {@code store.supplier.schedule.summary.*}) where the
     * unit allows one. A literal digit written into the text (e.g. "Gdy wartość jest większa od 0, produkt...")
     * is not flagged here: unlike a placeholder it never changes at runtime, so it is either right or wrong on its
     * own, and is not the class of bug this test guards against.
     */
    private static final Pattern NUMBER_BEFORE_INFLECTED_NOUN =
            Pattern.compile("\\{\\d+\\}\\s*(produkt(y|ów|u)?|pozycj(i|ę|e|a)?|minut(a|y)?)\\b");

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

    /** The "what this screen is for" intro of the catalogs screen, outside the {@code catalog.}/{@code product.} prefixes. */
    private static Map<String, String> introCatalogsMessages(String file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources", file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("intro.catalogs."))
                .collect(Collectors.toMap(key -> key, properties::getProperty));
    }

    @Test
    void polishAndEnglishDefineTheSameCatalogKeys() throws Exception {
        // when / then
        assertThat(catalogMessages("messages_pl.properties").keySet())
                .isEqualTo(catalogMessages("messages_en.properties").keySet());
    }

    @Test
    void polishAndEnglishDefineTheSameCatalogsIntroKeys() throws Exception {
        // when / then
        assertThat(introCatalogsMessages("messages_pl.properties").keySet())
                .isEqualTo(introCatalogsMessages("messages_en.properties").keySet());
    }

    /**
     * D-M31/OD-11: the catalogs intro describes catalog → categories → products; "mapowanie marek" /
     * "brand mapping" is not a concept this redesign surfaces (spec §5.1), so it must not reappear here.
     */
    @Test
    void theCatalogsIntroLeadDoesNotMentionBrandMapping() throws Exception {
        // when / then
        assertThat(introCatalogsMessages("messages_pl.properties").get("intro.catalogs.lead"))
                .isNotNull().doesNotContain("mapowan");
        assertThat(introCatalogsMessages("messages_en.properties").get("intro.catalogs.lead"))
                .isNotNull().doesNotContainIgnoringCase("brand mapping");
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

    /** The client's wording: the operator knows the purchase suggestions, not the name of the view that lists them. */
    @Test
    void restockCheckboxSaysWhereTheProductsShowUp() throws Exception {
        // when
        String pl = catalogMessages("messages_pl.properties").get("catalog.category.restock.desc");
        String en = catalogMessages("messages_en.properties").get("catalog.category.restock.desc");

        // then
        assertThat(pl).isEqualTo("Produkty z ustawionym oczekiwanym stanem magazynowym pojawią się jako sugestie zakupu "
                + "w momencie tworzenia dostaw lub bezpośrednio w magazynie.");
        assertThat(en).doesNotContain("Restock the warehouse");
    }
}
