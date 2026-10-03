package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.api.ItemCondition;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseRowMapperTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    private static Delivery delivery(String id, String provider) {
        Delivery delivery = new Delivery("store-1", null, provider);
        delivery.setDeliveryId(id);
        return delivery;
    }

    private final WarehouseRowMapper mapper = new WarehouseRowMapper(messages(), PL, new DeliveryRedirectResolver(),
            Map.of("delivery-1", delivery("delivery-1", "Acme"), "delivery-2", delivery("delivery-2", null)),
            identity -> "Acme".equals(identity) ? "Acme Polska" : identity);

    private static WarehouseItem item() {
        WarehouseItem item = new WarehouseItem("store-1", "delivery-1", "Karty graficzne", "Gigabyte RTX 4060 Ti",
                "5900000000065", "GV-N406TWF2OC", 1243.0, 3);
        item.setItemId("6e01f99c-0000-0000-0000-000000000000");
        item.setStatus(FulfilmentStatus.Delivered);
        return item;
    }

    @Test
    void rowCarriesReadyTextsAndLinks() {
        // given
        WarehouseItem item = item();

        // when
        WarehouseItemRow row = mapper.map(item);

        // then
        assertThat(row.href()).isEqualTo("/dashboard/warehouse/items/6e01f99c-0000-0000-0000-000000000000");
        assertThat(row.codes()).containsExactly("EAN 5900000000065", "GV-N406TWF2OC");
        assertThat(row.costNet()).isEqualTo("1 243,00");
        assertThat(row.costGross()).startsWith("brutto 1 528,");
        assertThat(row.statusLabel()).isEqualTo("Na stanie");
        assertThat(row.statusTone()).isEqualTo("is-ok");
        assertThat(row.selectable()).isTrue();
        assertThat(row.source()).isEqualTo("Acme");
        assertThat(row.deliveryHref()).isEqualTo("/dashboard/deliveries/details?deliveryId=delivery-1");
        assertThat(row.deliveryNumber()).isEqualTo("delivery");
        assertThat(row.supplier()).isEqualTo("Acme Polska");
        assertThat(row.conditionLabel()).isNull();
        assertThat(row.systemCost()).isNull();
    }

    @Test
    void uncategorizedConstantAndBlankReadAsNoCategory() {
        // given
        WarehouseItem a = item();
        a.setCategory(Categories.UNCATEGORIZED);
        WarehouseItem b = item();
        b.setCategory(null);

        // when / then
        assertThat(mapper.map(a).categoryLabel()).isEqualTo("Bez kategorii");
        assertThat(mapper.map(a).uncategorized()).isTrue();
        assertThat(mapper.map(b).uncategorized()).isTrue();
        assertThat(WarehouseRowMapper.categoryValue(" ")).isEqualTo(WarehouseListQuery.NO_CATEGORY);
        assertThat(WarehouseRowMapper.categoryValue("GPU")).isEqualTo("GPU");
    }

    @Test
    void openBoxSystemCostSerialAndUnknownDeliveryFallBack() {
        // given
        WarehouseItem item = item();
        item.setCondition(ItemCondition.Damaged);
        item.setUnitSystemCost(1200.0);
        item.setSerialNo("SN-1");
        item.setDeliveryId("Kosatec");
        item.setStatus(FulfilmentStatus.Ordered);

        // when
        WarehouseItemRow row = mapper.map(item);

        // then
        assertThat(row.conditionLabel()).isEqualTo("Uszkodzony");
        assertThat(row.conditionTone()).isEqualTo("is-bad");
        assertThat(row.systemCost()).isEqualTo("syst. 1 200,00");
        assertThat(row.systemCostTitle()).startsWith("Koszt systemowy: ").contains("1 200,00");
        assertThat(row.serialNo()).isEqualTo("S/N SN-1");
        assertThat(row.source()).isEqualTo("Kosatec");
        assertThat(row.selectable()).isFalse();
    }

    @Test
    void deliveryThatIsNotARecordOfTheStoreShowsNoLinkAndNoSupplier() {
        // given
        WarehouseItem unknown = item();
        unknown.setDeliveryId("Unknown");
        WarehouseItem blank = item();
        blank.setDeliveryId(null);
        WarehouseItem warehouse = item();
        warehouse.setDeliveryId("Warehouse");

        // when / then
        for (WarehouseItem item : new WarehouseItem[]{unknown, blank, warehouse}) {
            WarehouseItemRow row = mapper.map(item);
            assertThat(row.deliveryHref()).as(item.getDeliveryId()).isNull();
            assertThat(row.deliveryNumber()).as(item.getDeliveryId()).isNull();
            assertThat(row.supplier()).as(item.getDeliveryId()).isNull();
        }
    }

    @Test
    void deliveryWithoutProviderLinksWithoutASupplierLine() {
        // given
        WarehouseItem item = item();
        item.setDeliveryId("delivery-2");

        // when
        WarehouseItemRow row = mapper.map(item);

        // then
        assertThat(row.deliveryHref()).isEqualTo("/dashboard/deliveries/details?deliveryId=delivery-2");
        assertThat(row.supplier()).isNull();
    }

    @Test
    void newItemWaitingForItsSupplierLinksToThePlanningPageUnderTheSupplierLabel() {
        // given
        WarehouseItem item = item();
        item.setDeliveryId("Acme");
        item.setStatus(FulfilmentStatus.New);

        // when
        WarehouseItemRow row = mapper.map(item);

        // then
        assertThat(row.deliveryHref()).isEqualTo("/dashboard/deliveries/create/Acme");
        assertThat(row.deliveryNumber()).isEqualTo("Acme Polska");
        assertThat(row.supplier()).isNull();
        assertThat(row.source()).isEqualTo("Acme");
    }
}
