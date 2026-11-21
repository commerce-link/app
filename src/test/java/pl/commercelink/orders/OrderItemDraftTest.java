package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.products.ProductDataFixture;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.MatchedInventoryFixture;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.products.ProductData;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.taxonomy.Taxonomy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderItemDraftTest {

    @Mock
    private MatchedInventory matched;

    @Test
    void anInventoryItemGetsTheChosenEanAndTheNameOfItsTaxonomy() {
        // given
        when(matched.hasAnyOffers()).thenReturn(true);
        when(matched.getProductData()).thenReturn(ProductDataFixture.from(new Taxonomy("5901234123457", "MZ-V9P1T0BW", "Samsung", "Dysk",
                "Dyski", 0, null, null)));
        when(matched.getMedianPrice()).thenReturn(Price.fromNet(200));

        // when
        OrderItemDraft draft = OrderItemDraft.of(matched, "5901234123464", 1);

        // then
        assertThat(draft.sku()).isEqualTo("5901234123464");
        assertThat(draft.name()).isEqualTo("Dysk");
        assertThat(draft.category()).isEqualTo("Dyski");
    }

    @Test
    void anInventoryItemIsNamedAndFiledByTheResolvedProductData() {
        // given
        MatchedInventory row = MatchedInventoryFixture.describedAs(
                new ProductData("PIM name", "PIM brand", "PIM category", "989", "5900000000016", "PIM-MFN", null, null),
                new MatchedInventory(InventoryKey.fromEan("5900000000016"),
                        new InventoryItem("5900000000016", "PIM-MFN", 100.0, "PLN", 5, 1, "Elko"),
                        mock(SupplierRegistry.class, RETURNS_DEEP_STUBS)));

        // when
        OrderItemDraft draft = OrderItemDraft.of(row, "5900000000016", 1);

        // then
        assertThat(draft.name()).isEqualTo("PIM name");
        assertThat(draft.category()).isEqualTo("PIM category");
    }

    @Test
    void anItemWithoutOffersGetsTheChosenEanAsAMissingProduct() {
        // given
        MatchedInventory empty = MatchedInventory.empty(InventoryKey.fromEan("5901234123457"));

        // when
        OrderItemDraft draft = OrderItemDraft.of(empty, "5901234123457", 2);

        // then
        assertThat(draft.sku()).isEqualTo("5901234123457");
        assertThat(draft.qty()).isEqualTo(2);
        assertThat(draft.name()).isEqualTo("Brak produktu");
        assertThat(draft.category()).isEqualTo("Uncategorized");
    }
}
