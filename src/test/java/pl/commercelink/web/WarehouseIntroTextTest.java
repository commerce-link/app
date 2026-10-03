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

    private static String text(String key, String language) {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages.getMessage(key, null, Locale.forLanguageTag(language));
    }

    @Test
    void itemStatusIsCalledStatusNotStateInMenuErrorAndGuide() {
        // given
        String[] keys = {"warehouse.bulk.menu", "warehouse.error.status", "intro.warehouse.lead", "intro.warehouse.item2",
                "intro.warehouse.item2.text", "warehouse.bulk.dialog.effect.split", "warehouse.bulk.externalService.confirm.message"};

        // when / then
        assertThat(text("warehouse.bulk.menu", "pl")).isEqualTo("Zmień status");
        assertThat(text("warehouse.bulk.menu", "en")).isEqualTo("Change status");
        for (String key : keys) {
            assertThat(text(key, "pl")).as(key).doesNotContainPattern("\\b(stan|stanu|stanem)\\b").doesNotContain("obecnym stanie")
                    .doesNotContain("każdym stanie");
            assertThat(text(key, "en")).as(key).doesNotContainPattern("\\b(state|states)\\b");
        }
        assertThat(text("warehouse.error.status", "pl")).contains("ma status");
    }

    @Test
    void leadIsOneSentenceAndTheSearchPlaceholderNamesOnlyTheMainFields() {
        // when / then
        assertThat(text("warehouse.page.lead", "pl")).isEqualTo("Pozycje w magazynie sklepu i ich status.");
        assertThat(text("warehouse.page.lead", "en")).isEqualTo("Items in the store's warehouse and their status.");
        assertThat(text("warehouse.list.search.placeholder", "pl")).isEqualTo("Nazwa, EAN, kod, dostawa");
        assertThat(text("warehouse.list.search.placeholder", "en")).isEqualTo("Name, EAN, code, delivery");
        assertThat(text("warehouse.tile.toReceive.hint", "en")).isEqualTo("in allocation and ordered");
    }
}
