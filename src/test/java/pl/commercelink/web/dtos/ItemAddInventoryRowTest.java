package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.MatchedInventoryFixture;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.products.ProductData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

class ItemAddInventoryRowTest {

    private static final String EAN = "5900000000016";

    @Test
    void rowDescribesTheProductWithTheResolvedProductData() {
        // given
        MatchedInventory matched = MatchedInventoryFixture.describedAs(
                new ProductData("PIM name", "PIM brand", "PIM category", "989", EAN, "PIM-MFN", null, null),
                new MatchedInventory(InventoryKey.fromEan(EAN), new InventoryItem(EAN, "PIM-MFN", 100.0, "PLN", 5, 1, "Elko"),
                        mock(SupplierRegistry.class, RETURNS_DEEP_STUBS)));

        // when
        ItemAddInventoryRow row = ItemAddInventoryRow.of(EAN, matched);

        // then
        assertThat(row.ean()).isEqualTo(EAN);
        assertThat(row.mfn()).isEqualTo("PIM-MFN");
        assertThat(row.brand()).isEqualTo("PIM brand");
        assertThat(row.name()).isEqualTo("PIM name");
        assertThat(row.category()).isEqualTo("PIM category");
    }
}
