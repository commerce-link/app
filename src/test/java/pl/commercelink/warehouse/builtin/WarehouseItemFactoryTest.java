package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.products.StoreCategoryResolver;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.taxonomy.TaxonomyResolver;
import pl.commercelink.taxonomy.TaxonomyResolver.ResolvedProduct;
import pl.commercelink.warehouse.api.GoodsReceiptItem;
import pl.commercelink.warehouse.api.ItemCondition;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseItemFactoryTest {

    @Mock
    private TaxonomyResolver taxonomyResolver;

    @Mock
    private StoreCategoryResolver storeCategoryResolver;

    @InjectMocks
    private WarehouseItemFactory factory;

    @Test
    void carriesConditionFromGoodsReceiptItem() {
        // given
        when(taxonomyResolver.resolve(any(), any(), any()))
                .thenReturn(new ResolvedProduct("MFN-1", "Widget", Categories.UNCATEGORIZED, null));
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
                .thenReturn(new ResolvedProduct("MFN-1", null, Categories.UNCATEGORIZED, null));
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
                .thenReturn(new ResolvedProduct("MFN-1", "Widget", Categories.UNCATEGORIZED, null));
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
                .thenReturn(new ResolvedProduct("MFN-1", "Widget", "Computer Cases & Holders", "PIM-100"));
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
                .thenReturn(new ResolvedProduct("MFN-1", "Widget", "Computer Cases & Holders", "PIM-100"));
        when(storeCategoryResolver.findCategoryName("store-1", "PIM-100")).thenReturn(Optional.empty());
        DeliveryItem deliveryItem = new DeliveryItem();
        deliveryItem.setMfn("MFN-1");

        // when
        WarehouseItem item = factory.create("store-1", "Supplier", deliveryItem, 1);

        // then
        assertEquals("Computer Cases & Holders", item.getCategory());
    }
}
