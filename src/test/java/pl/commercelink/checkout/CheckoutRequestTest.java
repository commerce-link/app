package pl.commercelink.checkout;

import pl.commercelink.baskets.BasketItem;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.products.information.StorePimAliases;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutRequestTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "catalog-1";
    private static final String PRICELIST_ID = "pricelist-1";

    @Mock
    private PricelistRepository pricelistRepository;

    @Test
    void unknownPricelistIsPricelistOutdated() {
        // given
        when(pricelistRepository.find(STORE_ID, CATALOG_ID, PRICELIST_ID)).thenReturn(null);
        CheckoutRequest request = requestFor("pim-1");

        // when / then
        assertThatThrownBy(() -> request.toBasketItems(STORE_ID, pricelistRepository, aliases))
                .isInstanceOfSatisfying(CheckoutException.class,
                        e -> assertThat(e.getError()).isEqualTo(CheckoutError.PricelistOutdated));
    }

    @Test
    void itemMissingFromPricelistIsItemNotFound() {
        // given
        when(pricelistRepository.find(STORE_ID, CATALOG_ID, PRICELIST_ID))
                .thenReturn(new Pricelist(PRICELIST_ID, List.of(product("pim-1"))));
        CheckoutRequest request = requestFor("pim-gone");

        // when / then
        assertThatThrownBy(() -> request.toBasketItems(STORE_ID, pricelistRepository, aliases))
                .isInstanceOfSatisfying(CheckoutException.class,
                        e -> assertThat(e.getError()).isEqualTo(CheckoutError.ItemNotFound));
    }

    @Test
    void cartWithTheSourceIdChecksOutAgainstAPricelistWithTheClone() {
        // given
        when(pricelistRepository.find(STORE_ID, CATALOG_ID, PRICELIST_ID))
                .thenReturn(new Pricelist(PRICELIST_ID, List.of(product("pim-clone"))));

        // when
        List<BasketItem> items = requestFor("pim-source").toBasketItems(STORE_ID, pricelistRepository, aliases);

        // then
        assertThat(items).singleElement().extracting(BasketItem::getId).isEqualTo("pim-clone");
    }

    @Test
    void cartWithTheCloneIdChecksOutAgainstAPricelistWithTheSource() {
        // given
        when(pricelistRepository.find(STORE_ID, CATALOG_ID, PRICELIST_ID))
                .thenReturn(new Pricelist(PRICELIST_ID, List.of(product("pim-source"))));

        // when
        List<BasketItem> items = requestFor("pim-clone").toBasketItems(STORE_ID, pricelistRepository, aliases);

        // then
        assertThat(items).singleElement().extracting(BasketItem::getId).isEqualTo("pim-source");
    }

    @Test
    void otherStoreDoesNotCheckOutAProductOfAnotherStoresClone() {
        // given
        when(pricelistRepository.find("store-2", CATALOG_ID, PRICELIST_ID))
                .thenReturn(new Pricelist(PRICELIST_ID, List.of(product("pim-clone"))));

        // when / then
        assertThatThrownBy(() -> requestFor("pim-source").toBasketItems("store-2", pricelistRepository, aliases))
                .isInstanceOfSatisfying(CheckoutException.class,
                        e -> assertThat(e.getError()).isEqualTo(CheckoutError.ItemNotFound));
    }

    private final StorePimAliases aliases = new StorePimAliases(ScopedPimCatalog.of(
            ScopedPimCatalog.globalEntry("pim-source", "5901234123457", "MFN-1", "cat"),
            ScopedPimCatalog.cloneEntry("pim-clone", STORE_ID, "pim-source", "5901234123457", "MFN-1", "cat")));

    private static CheckoutRequest requestFor(String pimId) {
        Map<String, Object> item = Map.of("itemId", pimId, "qty", 1, "catalogId", CATALOG_ID, "pricelistId", PRICELIST_ID);
        return new ObjectMapper().convertValue(Map.of("items", List.of(item)), CheckoutRequest.class);
    }

    private static AvailabilityAndPrice product(String pimId) {
        return new AvailabilityAndPrice(pimId, "5901234123457", "MFN-1", "Acme", "", "Karta graficzna", "Karty graficzne",
                100, 5, 2, 100, false);
    }
}
