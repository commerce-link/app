package pl.commercelink.web.rma;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

class RmaNewMessagesTest {

    static final List<String> KEYS = List.of(
            "rma.new.lead", "rma.new.back.list", "rma.new.back.order",
            "rma.new.search.label", "rma.new.search.clear", "rma.new.search.title", "rma.new.search.desc",
            "rma.new.search.hints", "rma.new.search.hint.order", "rma.new.search.hint.serial", "rma.new.search.hint.email",
            "rma.new.search.note",
            "rma.new.lead.order", "rma.new.lead.ordered", "rma.new.lead.choice.email", "rma.new.lead.choice.match",
            "rma.new.choice.title.email", "rma.new.choice.title.match", "rma.new.choice.limit",
            "rma.new.choice.col.order", "rma.new.choice.col.ordered", "rma.new.choice.col.status", "rma.new.choice.col.value",
            "rma.new.choice.unavailable.early", "rma.new.choice.unavailable.cancelled",
            "rma.new.notfound.title", "rma.new.notfound.desc", "rma.new.notfound.desc.email", "rma.new.notfound.hint",
            "rma.new.ineligible.title.early", "rma.new.ineligible.title.cancelled", "rma.new.ineligible.desc.early",
            "rma.new.ineligible.desc.cancelled", "rma.new.ineligible.open",
            "rma.new.items.title", "rma.new.items.desc", "rma.new.items.col.select", "rma.new.items.col.item",
            "rma.new.items.col.qty", "rma.new.items.col.price", "rma.new.items.select.label", "rma.new.items.qty.label",
            "rma.new.items.of", "rma.new.items.ean", "rma.new.items.serial", "rma.new.items.service", "rma.new.items.claimed",
            "rma.new.items.none", "rma.new.items.allClaimed",
            "rma.new.resolution.legend", "rma.new.reason.label", "rma.new.reason.placeholder",
            "rma.new.order.title", "rma.new.order.number", "rma.new.order.status", "rma.new.order.ordered",
            "rma.new.order.client", "rma.new.order.source", "rma.new.order.source.shop",
            "rma.new.summary.title", "rma.new.email.label", "rma.new.email.help", "rma.new.summary.items",
            "rma.new.summary.pieces", "rma.new.summary.value", "rma.new.summary.currency", "rma.new.create", "rma.new.create.help",
            "rma.new.create.empty", "rma.new.create.blocked", "rma.new.cancel",
            "rma.new.errors.title", "rma.new.error.noItems", "rma.new.error.orderGone", "rma.new.error.itemsChanged",
            "rma.new.error.qty", "rma.new.error.resolution", "rma.new.error.reason", "rma.new.error.claimed",
            "rma.new.error.email.required", "rma.new.error.email.invalid",
            "order.page.rma.create", "rma.create.success", "rma.new",
            "RMAResolutionType.Repair", "RMAResolutionType.Replacement", "RMAResolutionType.Return");

    static final List<String> GONE = List.of("rma.by.order.line", "rma.by.serial.no", "rma.by.email",
            "rma.reload.order.preload.items", "rma.order.item.validation", "rma.max.qty", "rma.order.id.placeholder",
            "rma.serial.no.placeholder");

    @Test
    void everyKeyExistsInBothLanguagesAndTheOldOnesAreGone() {
        for (Locale locale : List.of(Locale.forLanguageTag("pl"), Locale.ENGLISH)) {
            // given
            ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);

            // then
            assertThat(KEYS).allSatisfy(key -> assertThat(bundle.containsKey(key)).as(locale + " " + key).isTrue());
            assertThat(GONE).allSatisfy(key -> assertThat(bundle.containsKey(key)).as(locale + " " + key).isFalse());
        }
    }
}
