package pl.commercelink.receipts;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@code ReceiptAlerts.message()}'s use of the real message bundles: it always passes a non-null args array
 * for {@code receipts.attention.*}, so Spring formats those templates with {@link java.text.MessageFormat}, which
 * treats a lone {@code '} as the start of a quoted (literal) section. Every apostrophe in those templates must
 * therefore be doubled ({@code ''}) or the text after it silently disappears from the rendered alert.
 */
class ReceiptAttentionMessagesTest {

    private static final Object[] ARGS = {"ORDER-1", "ORDER-1:R1", "LAST-ERROR", "FAILURE-MESSAGE", "BLOCKED-REASON", 7};

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    @Test
    void formatsEveryAttentionMessageWithoutLosingTextOrApostrophesInBothLanguages() {
        ResourceBundleMessageSource messageSource = messageSource();

        for (String language : java.util.List.of("pl", "en")) {
            Locale locale = Locale.forLanguageTag(language);
            ResourceBundle rawBundle = ResourceBundle.getBundle("messages", locale);

            for (ReceiptAttention attention : ReceiptAttention.values()) {
                String key = attention.messageKey();
                String raw = rawBundle.getString(key);
                String formatted = messageSource.getMessage(key, ARGS, locale);

                assertThat(formatted).as(language + " " + key).isNotBlank();

                if (raw.contains("{1}")) {
                    assertThat(formatted).as(language + " " + key + " should contain the receipt key argument")
                            .contains("ORDER-1:R1");
                }

                // every '' in the raw template is one literal apostrophe once MessageFormat un-escapes it; a lone,
                // un-doubled ' instead starts a quoted section and swallows the following text, which would show up
                // here as a mismatched apostrophe count.
                int intendedApostrophes = StringUtils.countMatches(raw, "''");
                int renderedApostrophes = StringUtils.countMatches(formatted, "'");
                assertThat(renderedApostrophes).as(language + " " + key + " apostrophe count").isEqualTo(intendedApostrophes);
            }
        }
    }

    @Test
    void keepsTheStoreAndOrderPossessivesInTheEnglishEmailNotSentMessage() {
        ResourceBundleMessageSource messageSource = messageSource();

        String formatted = messageSource.getMessage(
                ReceiptAttention.EMAIL_NOT_SENT.messageKey(), ARGS, Locale.forLanguageTag("en"));

        assertThat(formatted).contains("store's email templates").contains("order's documents");
    }

    private static java.util.Properties raw(String file) throws java.io.IOException {
        java.util.Properties properties = new java.util.Properties();
        try (java.io.Reader reader = new java.io.InputStreamReader(
                ReceiptAttentionMessagesTest.class.getResourceAsStream("/" + file),
                java.nio.charset.StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    @Test
    void everyAttentionHasAnOrderPageCauseAndActionInPolishAndEnglish() throws java.io.IOException {
        // given
        java.util.Properties pl = raw("messages_pl.properties");
        java.util.Properties en = raw("messages_en.properties");

        for (ReceiptAttention attention : ReceiptAttention.values()) {
            for (String part : java.util.List.of("cause", "action")) {
                // when
                String key = "receipts.page." + attention.name() + "." + part;

                // then
                assertThat(pl.getProperty(key)).as("pl " + key).isNotBlank();
                assertThat(en.getProperty(key)).as("en " + key).isNotBlank();
            }
        }
    }

    @Test
    void everyOrderPageKeyExistsInBothLanguagesAndKeepsItsApostrophes() throws java.io.IOException {
        // given: the page texts are always formatted with arguments, so a lone ' would swallow text
        java.util.Properties pl = raw("messages_pl.properties");
        java.util.Properties en = raw("messages_en.properties");
        ResourceBundleMessageSource messageSource = messageSource();
        Object[] args = {"PROVIDER", "LAST-ERROR", "FAILURE", "BLOCKED", 7, "ORDER-1:R1"};

        for (java.util.Properties bundle : java.util.List.of(pl, en)) {
            java.util.Properties other = bundle == pl ? en : pl;
            Locale locale = Locale.forLanguageTag(bundle == pl ? "pl" : "en");
            for (String key : bundle.stringPropertyNames()) {
                if (!key.startsWith("receipts.page.")) {
                    continue;
                }
                // when
                String formatted = messageSource.getMessage(key, args, locale);

                // then
                assertThat(other.getProperty(key)).as(locale + " " + key + " in the other bundle").isNotBlank();
                assertThat(StringUtils.countMatches(formatted, "'")).as(locale + " " + key + " apostrophes")
                        .isEqualTo(StringUtils.countMatches(bundle.getProperty(key), "''"));
            }
        }
    }

    @Test
    void formatsThePosBlockedMessageWithoutLosingApostrophesInBothLanguages() {
        // given
        ResourceBundleMessageSource messageSource = messageSource();

        for (String language : java.util.List.of("pl", "en")) {
            Locale locale = Locale.forLanguageTag(language);
            String raw = ResourceBundle.getBundle("messages", locale).getString("receipts.attention.BLOCKED_POS");

            // when
            String formatted = messageSource.getMessage("receipts.attention.BLOCKED_POS", ARGS, locale);

            // then
            assertThat(formatted).as(language).contains("ORDER-1");
            assertThat(StringUtils.countMatches(formatted, "'")).as(language + " apostrophes")
                    .isEqualTo(StringUtils.countMatches(raw, "''"));
        }
    }
}
