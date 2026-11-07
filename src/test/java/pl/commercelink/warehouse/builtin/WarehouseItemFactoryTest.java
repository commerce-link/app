package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.fulfilment.FulfilmentItem;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.products.StoreCategoryResolver;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyRepository;
import pl.commercelink.taxonomy.TaxonomyResolver;
import pl.commercelink.taxonomy.TaxonomyResolver.ResolvedProduct;
import pl.commercelink.warehouse.api.GoodsReceiptItem;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.ReservationRemovalItem;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseItemFactoryTest {

    private static final String CODE = "MZ-V9P2T0BW";
    private static final String EAN_PLAIN = "8806094215038";
    private static final String EAN_HEATSINK = "8806094215045";

    @Mock
    private TaxonomyResolver taxonomyResolver;

    @Mock
    private StoreCategoryResolver storeCategoryResolver;

    @InjectMocks
    private WarehouseItemFactory factory;

    static Stream<Arguments> itemSources() {
        return Stream.of(
                Arguments.of("reservation removal", (BiFunction<WarehouseItemFactory, OrderItem, WarehouseItem>)
                        (factory, orderItem) -> factory.create("store-1", ReservationRemovalItem.from(orderItem))),
                Arguments.of("goods receipt of a return", (BiFunction<WarehouseItemFactory, OrderItem, WarehouseItem>)
                        (factory, orderItem) -> factory.create("store-1", GoodsReceiptItem.from(returned(orderItem), ItemCondition.Sealed))),
                Arguments.of("fulfilment", (BiFunction<WarehouseItemFactory, OrderItem, WarehouseItem>)
                        (factory, orderItem) -> factory.create("store-1", new FulfilmentItem(orderItem,
                                new InventoryItem(orderItem.getEan(), orderItem.getManufacturerCode(), 689.0, "PLN", 1, 1, "Elko"), true))),
                Arguments.of("delivery", (BiFunction<WarehouseItemFactory, OrderItem, WarehouseItem>)
                        (factory, orderItem) -> factory.create("store-1", "Elko", delivered(orderItem), 1)));
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("itemSources")
    void itemTakesTheRecordOfItsEan(String path, BiFunction<WarehouseItemFactory, OrderItem, WarehouseItem> create) {
        // given
        TaxonomyCache taxonomy = new TaxonomyCache(mock(TaxonomyRepository.class));
        taxonomy.add(new Taxonomy(EAN_PLAIN, CODE, "Samsung", "Samsung 990 Pro 2TB", "Dyski SSD", 1, null, null));
        taxonomy.add(new Taxonomy(EAN_HEATSINK, CODE, "Samsung", "Samsung 990 Pro 2TB z radiatorem", "Dyski SSD z radiatorem", 5, null, null));
        WarehouseItemFactory realFactory = new WarehouseItemFactory(new TaxonomyResolver(taxonomy), storeCategoryResolver);
        OrderItem orderItem = new OrderItem("order-1", Categories.UNCATEGORIZED, "Samsung 990 Pro z zamówienia", 1, 899.0, EAN_HEATSINK, false);
        orderItem.setEan(EAN_HEATSINK);
        orderItem.setManufacturerCode(CODE);

        // when
        WarehouseItem item = create.apply(realFactory, orderItem);

        // then
        assertThat(item.getName()).isEqualTo("Samsung 990 Pro 2TB z radiatorem");
        assertThat(item.getCategory()).isEqualTo("Dyski SSD z radiatorem");
    }

    @Test
    void carriesConditionFromGoodsReceiptItem() {
        // given
        when(taxonomyResolver.resolve(any(), any(), any()))
                .thenReturn(new ResolvedProduct("Widget", Categories.UNCATEGORIZED, null));
        RMAItem rmaItem = new RMAItem();
        rmaItem.setDeliveryId("delivery-1");
        rmaItem.setMfn("MFN-1");
        rmaItem.setQty(1);

        // when
        WarehouseItem item = factory.create("store-1", GoodsReceiptItem.from(rmaItem, ItemCondition.OpenBox));

        // then
        assertEquals(ItemCondition.OpenBox, item.getCondition());
    }

    @Test
    void usesUnknownNameWhenResolvedNameIsMissing() {
        // given
        when(taxonomyResolver.resolve(any(), any(), any()))
                .thenReturn(new ResolvedProduct(null, Categories.UNCATEGORIZED, null));
        DeliveryItem deliveryItem = new DeliveryItem();
        deliveryItem.setMfn("MFN-1");

        // when
        WarehouseItem item = factory.create("store-1", "Supplier", deliveryItem, 1);

        // then
        assertEquals("Unknown", item.getName());
    }

    @Test
    void keepsResolvedName() {
        // given
        when(taxonomyResolver.resolve(any(), any(), any()))
                .thenReturn(new ResolvedProduct("Widget", Categories.UNCATEGORIZED, null));
        DeliveryItem deliveryItem = new DeliveryItem();
        deliveryItem.setMfn("MFN-1");

        // when
        WarehouseItem item = factory.create("store-1", "Supplier", deliveryItem, 1);

        // then
        assertEquals("Widget", item.getName());
    }

    @Test
    void usesMerchantCategoryNameWhenPimCategoryIsMapped() {
        // given
        when(taxonomyResolver.resolve(any(), any(), any()))
                .thenReturn(new ResolvedProduct("Widget", "Computer Cases & Holders", "PIM-100"));
        when(storeCategoryResolver.findCategoryName("store-1", "PIM-100")).thenReturn(Optional.of("Case"));
        DeliveryItem deliveryItem = new DeliveryItem();
        deliveryItem.setMfn("MFN-1");

        // when
        WarehouseItem item = factory.create("store-1", "Supplier", deliveryItem, 1);

        // then
        assertEquals("Case", item.getCategory());
    }

    @Test
    void fallsBackToTaxonomyCategoryWhenPimCategoryIsNotMapped() {
        // given
        when(taxonomyResolver.resolve(any(), any(), any()))
                .thenReturn(new ResolvedProduct("Widget", "Computer Cases & Holders", "PIM-100"));
        when(storeCategoryResolver.findCategoryName("store-1", "PIM-100")).thenReturn(Optional.empty());
        DeliveryItem deliveryItem = new DeliveryItem();
        deliveryItem.setMfn("MFN-1");

        // when
        WarehouseItem item = factory.create("store-1", "Supplier", deliveryItem, 1);

        // then
        assertEquals("Computer Cases & Holders", item.getCategory());
    }
}
