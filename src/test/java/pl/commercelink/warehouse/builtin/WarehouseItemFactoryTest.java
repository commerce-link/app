package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.fulfilment.FulfilmentItem;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.ProductDataResolver;
import pl.commercelink.products.StoreCategoryResolver;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyRepository;
import pl.commercelink.warehouse.api.GoodsReceiptItem;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.ReservationRemovalItem;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.commercelink.pim.ScopedPimCatalog.storeEntry;

@ExtendWith(MockitoExtension.class)
class WarehouseItemFactoryTest {

    private static final String STORE_ID = "store-1";
    private static final String CODE = "MZ-V9P2T0BW";
    private static final String EAN_PLAIN = "8806094215038";
    private static final String EAN_HEATSINK = "8806094215045";

    @Mock
    private StoreCategoryResolver storeCategoryResolver;

    private final TaxonomyCache taxonomy = new TaxonomyCache(mock(TaxonomyRepository.class));

    private WarehouseItemFactory factory(PimEntry... entries) {
        return new WarehouseItemFactory(new ProductDataResolver(ScopedPimCatalog.of(entries), taxonomy),
                storeCategoryResolver);
    }

    static Stream<Arguments> itemSources() {
        return Stream.of(
                Arguments.of("reservation removal", (BiFunction<WarehouseItemFactory, OrderItem, WarehouseItem>)
                        (factory, orderItem) -> factory.create(STORE_ID, ReservationRemovalItem.from(orderItem))),
                Arguments.of("goods receipt of a return", (BiFunction<WarehouseItemFactory, OrderItem, WarehouseItem>)
                        (factory, orderItem) -> factory.create(STORE_ID, GoodsReceiptItem.from(returned(orderItem), ItemCondition.Sealed))),
                Arguments.of("fulfilment", (BiFunction<WarehouseItemFactory, OrderItem, WarehouseItem>)
                        (factory, orderItem) -> factory.create(STORE_ID, new FulfilmentItem(orderItem,
                                new InventoryItem(orderItem.getEan(), orderItem.getManufacturerCode(), 689.0, "PLN", 1, 1, "Elko"), true))),
                Arguments.of("delivery", (BiFunction<WarehouseItemFactory, OrderItem, WarehouseItem>)
                        (factory, orderItem) -> factory.create(STORE_ID, "Elko", delivered(orderItem), 1)));
    }

    private static RMAItem returned(OrderItem orderItem) {
        RMAItem rmaItem = new RMAItem();
        rmaItem.setEan(orderItem.getEan());
        rmaItem.setMfn(orderItem.getManufacturerCode());
        rmaItem.setName(orderItem.getName());
        rmaItem.setQty(1);
        return rmaItem;
    }

    private static DeliveryItem delivered(OrderItem orderItem) {
        DeliveryItem deliveryItem = new DeliveryItem();
        deliveryItem.setEan(orderItem.getEan());
        deliveryItem.setMfn(orderItem.getManufacturerCode());
        deliveryItem.setName(orderItem.getName());
        return deliveryItem;
    }

    private static OrderItem orderedHeatsinkVersion() {
        OrderItem orderItem = new OrderItem("order-1", Categories.UNCATEGORIZED, "Samsung 990 Pro z zamówienia", 1, 899.0, EAN_HEATSINK, false);
        orderItem.setEan(EAN_HEATSINK);
        orderItem.setManufacturerCode(CODE);
        return orderItem;
    }

    private static DeliveryItem deliveryItem(String ean, String name) {
        DeliveryItem deliveryItem = new DeliveryItem();
        deliveryItem.setEan(ean);
        deliveryItem.setMfn("MFN-1");
        deliveryItem.setName(name);
        return deliveryItem;
    }

    private static PimEntry entryInCategory(String gtin, String category, String categoryId) {
        return new PimEntry("pim-1", ScopedPimCatalog.identifiers(gtin, null), "Brand", "Widget", category, null, true,
                null, null, categoryId);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("itemSources")
    void itemTakesTheRecordOfItsEan(String path, BiFunction<WarehouseItemFactory, OrderItem, WarehouseItem> create) {
        // given
        taxonomy.add(new Taxonomy(EAN_PLAIN, CODE, "Samsung", "Samsung 990 Pro 2TB", "Dyski SSD", 1, null, null));
        taxonomy.add(new Taxonomy(EAN_HEATSINK, CODE, "Samsung", "Samsung 990 Pro 2TB z radiatorem", "Dyski SSD z radiatorem", 5, null, null));

        // when
        WarehouseItem item = create.apply(factory(), orderedHeatsinkVersion());

        // then
        assertThat(item.getName()).isEqualTo("Samsung 990 Pro 2TB z radiatorem");
        assertThat(item.getCategory()).isEqualTo("Dyski SSD z radiatorem");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("itemSources")
    void itemTakesTheStoresPimEntryOfItsEanBeforeTheTaxonomy(String path, BiFunction<WarehouseItemFactory, OrderItem, WarehouseItem> create) {
        // given
        taxonomy.add(new Taxonomy(EAN_HEATSINK, CODE, "Samsung", "Samsung 990 Pro 2TB z radiatorem", "Dyski SSD z radiatorem", 5, null, null));
        WarehouseItemFactory factory = factory(storeEntry("s-1", STORE_ID, EAN_HEATSINK, CODE, "Dyski NVMe"));

        // when
        WarehouseItem item = create.apply(factory, orderedHeatsinkVersion());

        // then
        assertThat(item.getName()).isEqualTo("Store s-1");
        assertThat(item.getCategory()).isEqualTo("Dyski NVMe");
    }

    @Test
    void carriesConditionFromGoodsReceiptItem() {
        // given
        RMAItem rmaItem = new RMAItem();
        rmaItem.setDeliveryId("delivery-1");
        rmaItem.setMfn("MFN-1");
        rmaItem.setQty(1);

        // when
        WarehouseItem item = factory().create(STORE_ID, GoodsReceiptItem.from(rmaItem, ItemCondition.OpenBox));

        // then
        assertEquals(ItemCondition.OpenBox, item.getCondition());
    }

    @Test
    void usesUnknownNameWhenNeitherTheProductNorTheItemHasOne() {
        // when
        WarehouseItem item = factory().create(STORE_ID, "Supplier", deliveryItem(null, null), 1);

        // then
        assertEquals("Unknown", item.getName());
    }

    @Test
    void keepsTheItemsOwnNameAndIsUncategorisedWhenNobodyKnowsItsEan() {
        // when
        WarehouseItem item = factory().create(STORE_ID, "Supplier", deliveryItem(EAN_PLAIN, "Widget z faktury"), 1);

        // then
        assertEquals("Widget z faktury", item.getName());
        assertEquals(Categories.UNCATEGORIZED, item.getCategory());
    }

    @Test
    void usesMerchantCategoryNameWhenPimCategoryIsMapped() {
        // given
        when(storeCategoryResolver.findCategoryName(STORE_ID, "PIM-100")).thenReturn(Optional.of("Case"));
        WarehouseItemFactory factory = factory(entryInCategory(EAN_PLAIN, "Computer Cases & Holders", "PIM-100"));

        // when
        WarehouseItem item = factory.create(STORE_ID, "Supplier", deliveryItem(EAN_PLAIN, null), 1);

        // then
        assertEquals("Case", item.getCategory());
    }

    @Test
    void fallsBackToTheProductsCategoryWhenPimCategoryIsNotMapped() {
        // given
        when(storeCategoryResolver.findCategoryName(STORE_ID, "PIM-100")).thenReturn(Optional.empty());
        WarehouseItemFactory factory = factory(entryInCategory(EAN_PLAIN, "Computer Cases & Holders", "PIM-100"));

        // when
        WarehouseItem item = factory.create(STORE_ID, "Supplier", deliveryItem(EAN_PLAIN, null), 1);

        // then
        assertEquals("Computer Cases & Holders", item.getCategory());
    }
}
