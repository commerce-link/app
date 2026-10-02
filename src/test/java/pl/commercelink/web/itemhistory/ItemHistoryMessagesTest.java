package pl.commercelink.web.itemhistory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

class ItemHistoryMessagesTest {

    static final List<String> KEYS = List.of(
            "item.history.lead", "item.history.search.label", "item.history.search.clear", "item.history.search.title",
            "item.history.search.desc", "item.history.search.hints", "item.history.search.hint.now",
            "item.history.search.hint.path", "item.history.search.hint.rma", "item.history.notfound.title",
            "item.history.notfound.desc", "item.history.notfound.hint", "item.history.serial", "item.history.ean",
            "item.history.mfn", "item.history.copy.serial", "item.history.copy.ean", "item.history.copy.mfn",
            "item.history.product.unnamed", "item.history.product.many", "item.history.now",
            "item.history.now.IN_RMA", "item.history.now.IN_ORDER", "item.history.now.AT_CUSTOMER",
            "item.history.now.IN_STOCK", "item.history.now.RESERVED", "item.history.now.INBOUND",
            "item.history.now.UNKNOWN", "item.history.now.text.atCustomer", "item.history.now.text.inStock",
            "item.history.now.text.reserved", "item.history.now.text.inbound", "item.history.now.text.unknown",
            "item.history.now.text.ambiguous", "item.history.record.order", "item.history.record.rma",
            "item.history.record.delivery", "item.history.record.warehouse", "item.history.fact.condition",
            "item.history.fact.itemStatus", "item.history.fact.client", "item.history.fact.fromWarehouse",
            "item.history.fact.supplier", "item.history.fact.supplierRef", "item.history.fact.expected",
            "item.history.fact.actual", "item.history.event.RMA_CREATED", "item.history.event.ORDER_PLACED",
            "item.history.event.DELIVERY_RECEIVED", "item.history.event.DELIVERY_ORDERED", "item.history.events",
            "item.history.events.order", "item.history.events.truncated", "item.history.events.empty",
            "item.history.warning.title", "item.history.warning.text", "item.history.warning.counts",
            "item.history.link.order", "item.history.link.rma",
            "nav.item.history");

    @Test
    void everyKeyExistsInBothLanguagesAndTheOldOnesAreGone() {
        for (Locale locale : List.of(Locale.forLanguageTag("pl"), Locale.ENGLISH)) {
            ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
            assertThat(KEYS).allSatisfy(key -> assertThat(bundle.containsKey(key)).as(locale + " " + key).isTrue());
            assertThat(bundle.containsKey("item.history.start")).isFalse();
            assertThat(bundle.containsKey("item.history.view.detail")).isFalse();
        }
    }
}
