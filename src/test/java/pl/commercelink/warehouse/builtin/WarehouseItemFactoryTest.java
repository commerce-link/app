package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.taxonomy.TaxonomyResolver;
import pl.commercelink.taxonomy.TaxonomyResolver.ResolvedProduct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseItemFactoryTest {

    @Mock
    private TaxonomyResolver taxonomyResolver;

    @InjectMocks
    private WarehouseItemFactory factory;

    @Test
    void usesUnknownNameWhenResolvedNameIsMissing() {
        // given
        when(taxonomyResolver.resolve(any(), any(), any()))
                .thenReturn(new ResolvedProduct("MFN-1", null, Categories.UNCATEGORIZED));
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
                .thenReturn(new ResolvedProduct("MFN-1", "Widget", Categories.UNCATEGORIZED));
        DeliveryItem deliveryItem = new DeliveryItem();
        deliveryItem.setMfn("MFN-1");

        // when
        WarehouseItem item = factory.create("store-1", "Supplier", deliveryItem, 1);

        // then
        assertEquals("Widget", item.getName());
    }
}
