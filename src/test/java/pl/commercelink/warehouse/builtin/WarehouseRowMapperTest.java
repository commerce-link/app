package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.api.ItemCondition;

import java.util.Locale;
import java.util.Set;

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

    private final WarehouseRowMapper mapper = mapper(true);

    /**
     * A store connected to Acme only, with Kosatec a supplier the application knows but the store no longer uses; admin
     * tells whether the viewer may open the new-delivery page.
     */
    private static WarehouseRowMapper mapper(boolean admin) {
        return new WarehouseRowMapper(messages(), PL, new DeliveryRedirectResolver(),
                identity -> "Acme".equals(identity) ? "Acme Polska" : identity, "Acme"::equals, Set.of("Acme", "Kosatec")::contains, admin, null);
    }

    private static WarehouseItem item() {
        WarehouseItem item = new WarehouseItem("store-1", "delivery-1", "Karty graficzne", "Gigabyte RTX 4060 Ti",
                "5900000000065", "GV-N406TWF2OC", 1243.0, 3);
        item.setItemId("6e01f99c-0000-0000-0000-000000000000");
        item.setStatus(FulfilmentStatus.Delivered);
        return item;
    }

    @Test
    void rowLinkCarriesTheListItCameFrom() {
        // given
        WarehouseItem item = item();
        WarehouseRowMapper mapper = new WarehouseRowMapper(messages(), PL, new DeliveryRedirectResolver(), s -> s, s -> false,
                s -> false, false, "/dashboard/warehouse?statuses=all&page=2");

        // when
        WarehouseItemRow row = mapper.map(item);

        // then
        assertThat(row.href()).isEqualTo("/dashboard/warehouse/items/" + item.getItemId()
                + "?back=%2Fdashboard%2Fwarehouse%3Fstatuses%3Dall%26page%3D2");
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
        assertThat(row.deliveryHref()).isEqualTo("/dashboard/deliveries/details?deliveryId=delivery-1");
        assertThat(row.deliveryNumber()).isEqualTo("delivery");
        assertThat(row.supplier()).isNull();
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
    void damagedItemSystemCostAndSerialAreReadyTexts() {
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
        assertThat(row.systemCost()).isEqualTo("Koszt systemowy: 1 200,00 netto / 1 476,00 brutto");
        assertThat(row.serialNo()).isEqualTo("S/N SN-1");
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
        WarehouseItem formerSupplier = item();
        formerSupplier.setDeliveryId("Kosatec");
        formerSupplier.setStatus(FulfilmentStatus.Ordered);

        // when / then
        for (WarehouseItem item : new WarehouseItem[]{unknown, blank, warehouse, formerSupplier}) {
            WarehouseItemRow row = mapper.map(item);
            assertThat(row.deliveryHref()).as(item.getDeliveryId()).isNull();
            assertThat(row.deliveryNumber()).as(item.getDeliveryId()).isNull();
            assertThat(row.supplier()).as(item.getDeliveryId()).isNull();
            assertThat(row.hasDelivery()).as(item.getDeliveryId()).isFalse();
        }
    }

    @Test
    void deliveryIdLinksWithItsShortIdAndNoSupplierLineSinceTheListReadsNoDelivery() {
        // given
        WarehouseItem item = item();
        item.setDeliveryId("7e7e1971-0000-0000-0000-000000000000");

        // when
        WarehouseItemRow row = mapper.map(item);

        // then
        assertThat(row.deliveryHref()).isEqualTo("/dashboard/deliveries/details?deliveryId=7e7e1971-0000-0000-0000-000000000000");
        assertThat(row.deliveryNumber()).isEqualTo("7e7e1971");
        assertThat(row.supplier()).isNull();
        assertThat(row.hasDelivery()).isTrue();
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
        assertThat(row.hasDelivery()).isFalse();
    }

    @Test
    void userSeesTheSupplierOfAWaitingItemAsTextBecauseThePlanningPageIsForAdminsOnly() {
        // given
        WarehouseItem item = item();
        item.setDeliveryId("Acme");
        item.setStatus(FulfilmentStatus.New);

        // when
        WarehouseItemRow row = mapper(false).map(item);

        // then
        assertThat(row.deliveryHref()).isNull();
        assertThat(row.deliveryNumber()).isNull();
        assertThat(row.supplier()).isEqualTo("Acme Polska");
    }

    @Test
    void orderedItemCarryingItsSupplierNameShowsThatSupplierWithoutALink() {
        // given
        WarehouseItem connected = item();
        connected.setDeliveryId("Acme");
        connected.setStatus(FulfilmentStatus.Ordered);
        WarehouseItem notConnected = item();
        notConnected.setDeliveryId("Kosatec");
        notConnected.setStatus(FulfilmentStatus.Ordered);

        // when
        WarehouseItemRow connectedRow = mapper.map(connected);
        WarehouseItemRow notConnectedRow = mapper.map(notConnected);

        // then
        assertThat(connectedRow.deliveryHref()).isNull();
        assertThat(connectedRow.supplier()).isEqualTo("Acme Polska");
        assertThat(connectedRow.hasDelivery()).isFalse();
        assertThat(notConnectedRow.deliveryHref()).isNull();
        assertThat(notConnectedRow.supplier()).isNull();
    }

    @Test
    void rowTellsWhetherItCarriesADeliveryId() {
        // given
        WarehouseItem fromDelivery = item();
        WarehouseItem addedByHand = item();
        addedByHand.setDeliveryId("Unknown");
        addedByHand.setStatus(FulfilmentStatus.InRMA);

        // when / then
        assertThat(mapper.map(fromDelivery).hasDelivery()).isTrue();
        assertThat(mapper.map(addedByHand).hasDelivery()).isFalse();
    }
}
