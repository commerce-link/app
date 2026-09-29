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
}
