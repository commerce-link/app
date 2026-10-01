package pl.commercelink.web.deliveries.create;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryCreateMessagesTest {

    static final List<String> KEYS = List.of(
            "deliveries.create.page.title", "deliveries.create.step", "deliveries.create.lead.what",
            "deliveries.create.lead.warehouse", "deliveries.create.lead.orders", "deliveries.create.lead.restock",
            "deliveries.create.lead.order", "deliveries.create.lead.dropship", "deliveries.create.lead.purchase",
            "deliveries.create.lead.manual",
            "deliveries.create.items.title", "deliveries.create.items.title.dropship", "deliveries.create.items.desc",
            "deliveries.create.items.desc.dropship", "deliveries.create.col.qty", "deliveries.create.col.unitCost",
            "deliveries.create.col.value", "deliveries.create.col.targetStock", "deliveries.create.purchase.reason.checkFailed", "deliveries.create.suggestions.atSupplier", "deliveries.create.sources",
            "deliveries.create.lines", "deliveries.create.min", "deliveries.create.source.include",
            "deliveries.create.source.include.label", "deliveries.create.source.type",
            "deliveries.create.source.type.Order", "deliveries.create.source.type.Warehouse",
            "deliveries.create.source.qty", "deliveries.create.adjustment", "deliveries.create.adjustment.value",
            "deliveries.create.edit.label", "deliveries.create.include.label", "deliveries.create.qty.label",
            "deliveries.create.unitCost.label", "deliveries.create.release.warehouse",
            "deliveries.create.release.warehouse.help", "deliveries.create.release.dropship",
            "deliveries.create.release.dropship.help", "deliveries.create.suggestions.summary",
            "deliveries.create.suggestions.help",
            "deliveries.create.recipient", "deliveries.create.recipient.warehouse",
            "deliveries.create.recipient.help.items", "deliveries.create.recipient.help.purchase",
            "deliveries.create.recipient.help.manual",
            "deliveries.create.summary", "deliveries.create.summary.products", "deliveries.create.summary.pieces",
            "deliveries.create.summary.net", "deliveries.create.summary.total",
            "deliveries.create.action.purchase", "deliveries.create.action.purchase.help",
            "deliveries.create.action.purchase.help.dropship", "deliveries.create.action.purchase.help.approval",
            "deliveries.create.action.manual", "deliveries.create.action.manual.help",
            "deliveries.create.action.manual.help.dropship", "deliveries.create.action.next",
            "deliveries.create.action.next.help", "deliveries.create.action.release",
            "deliveries.create.action.release.help", "deliveries.create.action.empty",
            "deliveries.create.error.nothingRequested", "deliveries.create.error.itemNumber", "deliveries.create.error.orderNumber",
            "deliveries.create.error.deliveryDate", "deliveries.create.error.date", "deliveries.create.error.number",
            "deliveries.create.error.notNegative", "deliveries.create.error.tax",
            "deliveries.create.purchase.title", "deliveries.create.purchase.availability",
            "deliveries.create.purchase.recheck", "deliveries.create.purchase.col.requested",
            "deliveries.create.purchase.col.available", "deliveries.create.purchase.col.feedPrice",
            "deliveries.create.purchase.col.livePrice", "deliveries.create.purchase.col.delta",
            "deliveries.create.purchase.missing", "deliveries.create.purchase.total",
            "deliveries.create.purchase.unavailable", "deliveries.create.purchase.unavailable.after",
            "deliveries.create.purchase.address.title", "deliveries.create.purchase.address.desc",
            "deliveries.create.purchase.address.filter", "deliveries.create.purchase.address.filter.placeholder",
            "deliveries.create.purchase.address.none", "deliveries.create.purchase.options.title",
            "deliveries.create.purchase.submit", "deliveries.create.purchase.submit.help",
            "deliveries.create.purchase.submit.help.approval", "deliveries.create.purchase.reasons",
            "deliveries.create.purchase.reason.checking", "deliveries.create.purchase.reason.availability",
            "deliveries.create.purchase.reason.address", "deliveries.create.purchase.reason.options",
            "deliveries.create.purchase.reason.blocked", "deliveries.create.back",
            "deliveries.create.manual.title", "deliveries.create.manual.orderData",
            "deliveries.create.manual.orderData.desc", "deliveries.create.manual.orderData.desc.dropship",
            "deliveries.create.field.orderNumber", "deliveries.create.field.deliveryDate",
            "deliveries.create.field.currency", "deliveries.create.field.shipping", "deliveries.create.field.payment",
            "deliveries.create.field.tax", "deliveries.create.field.tax.help", "deliveries.create.field.terms",
            "deliveries.create.field.terms.help", "deliveries.create.manual.changeItems",
            "deliveries.create.manual.save", "deliveries.create.manual.save.help",
            "deliveries.create.fulfilment.title", "deliveries.create.fulfilment.effect",
            "deliveries.create.fulfilment.cost", "deliveries.create.fulfilment.saved",
            "deliveries.create.fulfilment.costKept", "deliveries.create.fulfilment.failed",
            "deliveries.purchase.error.nothingRequested");

    @Test
    void everyKeyOfTheCreatePagesExistsInBothLanguages() throws Exception {
        // given
        Properties pl = load("messages_pl.properties");
        Properties en = load("messages_en.properties");

        // then
        for (String key : KEYS) {
            assertThat(pl.getProperty(key)).as(key + " pl").isNotBlank();
            assertThat(en.getProperty(key)).as(key + " en").isNotBlank();
        }
    }

    private static Properties load(String file) throws Exception {
        Properties properties = new Properties();
        properties.load(Files.newBufferedReader(Path.of("src/main/resources/" + file), StandardCharsets.UTF_8));
        return properties;
    }
}
