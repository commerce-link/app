package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseIntroTextTest {

    private static String lead(String language) {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages.getMessage("intro.warehouse.lead", null, Locale.forLanguageTag(language));
    }

    @Test
    void polishIntroPointsToTheResultsLineForValueAndMentionsNoValueTile() {
        // when
        String lead = lead("pl");

        // then
        assertThat(lead).doesNotContain("wartość magazynu").contains("linii nad tabelą").contains("netto i brutto");
    }

    @Test
    void englishIntroPointsToTheResultsLineForValueAndMentionsNoValueTile() {
        // when
        String lead = lead("en");

        // then
        assertThat(lead).doesNotContain("warehouse value").contains("line above the table").contains("net and gross");
    }
}
