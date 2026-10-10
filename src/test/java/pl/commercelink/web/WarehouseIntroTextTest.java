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

    @Test
    void quantityDialogAsksAQuestionPerActionAndExplainsTheSplitAndTheWholeNumber() {
        // when / then
        assertThat(text("warehouse.bulk.reserve.dialog.title", "pl")).isEqualTo("Zarezerwować {0} poz.?");
        assertThat(text("warehouse.bulk.release.dialog.title", "pl")).isEqualTo("Przywrócić na stan {0} poz.?");
        assertThat(text("warehouse.bulk.rma.dialog.title", "pl")).isEqualTo("Zgłosić do reklamacji {0} poz.?");
        assertThat(text("warehouse.bulk.destroy.dialog.title", "pl")).isEqualTo("Zniszczyć {0} poz.?");
        assertThat(text("warehouse.bulk.reserve.dialog.title", "en")).isEqualTo("Reserve {0} item(s)?");
        assertThat(text("warehouse.bulk.release.dialog.title", "en")).isEqualTo("Return {0} item(s) to stock?");
        assertThat(text("warehouse.bulk.rma.dialog.title", "en")).isEqualTo("File a claim for {0} item(s)?");
        assertThat(text("warehouse.bulk.destroy.dialog.title", "en")).isEqualTo("Destroy {0} item(s)?");
        assertThat(text("warehouse.bulk.dialog.effect.split", "pl"))
                .isEqualTo("Jeśli wpiszesz mniej, pozycja się podzieli: wybrane sztuki zmienią status, reszta zostanie bez zmian.");
        assertThat(text("warehouse.bulk.dialog.effect.split", "en"))
                .isEqualTo("If you enter fewer, the item splits: the chosen units change status, the rest stays as it is.");
        assertThat(text("warehouse.bulk.dialog.whole", "pl")).isEqualTo("Wpisz liczbę całkowitą od 1 do {0}.");
        assertThat(text("warehouse.bulk.dialog.whole", "en")).isEqualTo("Enter a whole number from 1 to {0}.");
        assertThat(text("warehouse.bulk.dialog.effect.destroy", "pl")).startsWith("Powstanie dokument RW.");
    }

    @Test
    void englishConfirmTitlesFilledInTheBrowserReadRightForOneAndMoreItems() {
        // when / then: the browser puts the count into {n}, so they cannot choose a plural form
        assertThat(text("warehouse.bulk.allocate.confirm.title", "en")).isEqualTo("Send {n} item(s) to allocation?");
        assertThat(text("warehouse.bulk.externalService.confirm.title", "en")).isEqualTo("Send {n} item(s) to service?");
        assertThat(text("warehouse.bulk.received.confirm.title", "en")).isEqualTo("Receive {n} item(s) from service?");
    }

    @Test
    void englishOutcomeMessagesChooseTheSingularForOneItem() {
        // given
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        Locale en = Locale.forLanguageTag("en");

        // when / then
        assertThat(messages.getMessage("warehouse.bulk.reserve.done", new Object[]{1, 3}, en)).isEqualTo("Reserved 3 pcs in 1 item.");
        assertThat(messages.getMessage("warehouse.bulk.reserve.done", new Object[]{2, 3}, en)).isEqualTo("Reserved 3 pcs in 2 items.");
        assertThat(messages.getMessage("warehouse.bulk.release.done", new Object[]{1, 1}, en)).isEqualTo("Returned 1 pcs in 1 item to stock.");
        assertThat(messages.getMessage("warehouse.bulk.rma.done", new Object[]{1, 1}, en)).isEqualTo("Filed a claim for 1 pcs in 1 item.");
        assertThat(messages.getMessage("warehouse.bulk.allocate.done", new Object[]{1, 0}, en)).isEqualTo("Sent 1 item to allocation.");
        assertThat(messages.getMessage("warehouse.bulk.externalService.done", new Object[]{1, 0}, en)).isEqualTo("Sent 1 item to service (WZ).");
        assertThat(messages.getMessage("warehouse.bulk.received.done", new Object[]{2, 0}, en)).isEqualTo("Received 2 items from service (PZ).");
        assertThat(messages.getMessage("warehouse.bulk.destroy.done", new Object[]{1, 2}, en)).isEqualTo("Destroyed 2 pcs in 1 item (RW).");
    }

    @Test
    void systemCostPillIsShortAndItsSeparatorComesFromTheMessages() {
        // when / then
        assertThat(text("warehouse.row.systemCost", "pl")).isEqualTo("syst. {0}");
        assertThat(text("warehouse.row.systemCost", "en")).isEqualTo("sys. {0}");
        assertThat(text("warehouse.row.code.beforeNext", "pl")).isEqualTo("{0} ·");
        assertThat(text("warehouse.row.code.beforeNext", "en")).isEqualTo("{0} ·");
    }

    @Test
    void phoneCardFiguresSayQuantityTimesNetUnitCost() {
        // when / then
        assertThat(text("warehouse.row.card.figures", "pl")).isEqualTo("{0} szt. × {1} netto");
        assertThat(text("warehouse.row.card.figures", "en")).isEqualTo("{0} pcs × {1} net");
    }
}
