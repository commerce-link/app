package pl.commercelink.pricelist;

import org.junit.jupiter.api.Test;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.products.information.StorePimAliases;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PricelistFinderTest {

    private static final String STORE = "s1";
    private static final String CATALOG = "c1";

    private final PricelistRepository repository = mock(PricelistRepository.class);
    private final PricelistFinder finder = new PricelistFinder(repository, new StorePimAliases(ScopedPimCatalog.of(
            ScopedPimCatalog.globalEntry("S", "5900000000011", "M1", "cat"),
            ScopedPimCatalog.cloneEntry("C", STORE, "S", "5900000000011", "M1", "cat"))));

    private void pricelistWith(String storeId, String pimId) {
        when(repository.findNewestPricelistIdCached(storeId, CATALOG)).thenReturn("p1");
        when(repository.find(storeId, CATALOG, "p1")).thenReturn(new Pricelist("p1", List.of(
                new AvailabilityAndPrice(pimId, "5900000000011", "M1", "Brand", "", "Name", "cat", 100L, 5L, 1, 0L, false))));
    }

    @Test
    void offerAndOrderDialogsFindTheMovedProduct() {
        // given
        pricelistWith(STORE, "C");

        // when / then
        assertThat(finder.findByPimId(STORE, CATALOG, "S")).map(AvailabilityAndPrice::getPimId).contains("C");
        assertThat(finder.findByPimId(STORE, CATALOG, "C")).map(AvailabilityAndPrice::getPimId).contains("C");
    }

    @Test
    void otherStoresAreUnaffected() {
        // given
        pricelistWith("s2", "C");

        // when / then
        assertThat(finder.findByPimId("s2", CATALOG, "S")).isEmpty();
    }
}
