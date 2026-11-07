package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static pl.commercelink.pim.ScopedPimCatalog.globalEntry;
import static pl.commercelink.pim.ScopedPimCatalog.storeEntry;

class ProductDataResolverTest {

    private static final String STORE_ID = "s1";
    private static final String EAN = "5900000000590";
    private static final String OTHER_EAN = "5900000000606";

    private final TaxonomyCache taxonomy = new TaxonomyCache(mock(TaxonomyRepository.class));

    private ProductDataResolver resolver(PimEntry... entries) {
        return new ProductDataResolver(ScopedPimCatalog.of(entries), taxonomy);
    }

    @Test
    void storeEntryComesBeforeTheGlobalEntryAndTheTaxonomy() {
        // given
        taxonomy.add(record(EAN));
        ProductDataResolver resolver = resolver(globalEntry("g1", EAN, "M-1", "GPU"),
                storeEntry("s1-1", STORE_ID, EAN, "M-1", "GPU"));

        // when
        Optional<ProductData> product = resolver.ofEan(STORE_ID, EAN);

        // then
        assertThat(product).map(ProductData::name).contains("Store s1-1");
    }

    @Test
    void globalEntryComesBeforeTheTaxonomy() {
        // given
        taxonomy.add(record(EAN));
        ProductDataResolver resolver = resolver(globalEntry("g1", EAN, "M-1", "GPU"));

        // when
        Optional<ProductData> product = resolver.ofEan(STORE_ID, EAN);

        // then
        assertThat(product).contains(new ProductData("Global g1", "Brand", "GPU", EAN, "M-1", 1000, 1200));
    }

    @Test
    void emptyFieldsOfThePimEntryAreFilledFromTheTaxonomy() {
        // given
        taxonomy.add(record(EAN));
        ProductDataResolver resolver = resolver(entryWithNameOnly("g1", EAN));

        // when
        Optional<ProductData> product = resolver.ofEan(STORE_ID, EAN);

        // then
        assertThat(product).contains(new ProductData("Global g1", "Samsung", "Dyski SSD", EAN, "M-1", 9, 60));
    }

    @Test
    void emptyFieldsOfTheGroupsPimEntryAreFilledFromTheTaxonomy() {
        // given
        taxonomy.add(record(EAN));
        ProductDataResolver resolver = resolver(entryWithNameOnly("g1", EAN));

        // when
        Optional<ProductData> product = resolver.ofGroup(STORE_ID, InventoryKey.fromPimIdAndEan("g1", EAN));

        // then
        assertThat(product).contains(new ProductData("Global g1", "Samsung", "Dyski SSD", EAN, "M-1", 9, 60));
    }

    @Test
    void emptyFieldsOfAnEntryFoundByPimIdAreFilledFromTheTaxonomyOfItsEan() {
        // given
        taxonomy.add(record(EAN));
        ProductDataResolver resolver = resolver(entryWithNameOnly("g1", EAN));

        // when
        Optional<ProductData> product = resolver.ofPimId(STORE_ID, "g1");

        // then
        assertThat(product).contains(new ProductData("Global g1", "Samsung", "Dyski SSD", EAN, "M-1", 9, 60));
    }

    @Test
    void taxonomyAnswersWhenPimDoesNotKnowTheEan() {
        // given
        taxonomy.add(record(EAN));
        ProductDataResolver resolver = resolver(globalEntry("g1", OTHER_EAN, "M-2", "GPU"));

        // when
        Optional<ProductData> product = resolver.ofEan(STORE_ID, EAN);

        // then
        assertThat(product).contains(new ProductData("Taxonomy name", "Samsung", "Dyski SSD", EAN, "M-1", 9, 60));
    }

    @Test
    void eanNobodyKnowsHasNoData() {
        // given
        ProductDataResolver resolver = resolver(globalEntry("g1", OTHER_EAN, "M-2", "GPU"));

        // when
        Optional<ProductData> product = resolver.ofEan(STORE_ID, EAN);

        // then
        assertThat(product).isEmpty();
    }

    @Test
    void withoutStoreTheStoreEntryIsNotSeen() {
        // given
        ProductDataResolver resolver = resolver(globalEntry("g1", EAN, "M-1", "GPU"),
                storeEntry("s1-1", STORE_ID, EAN, "M-1", "GPU"));

        // when
        Optional<ProductData> product = resolver.ofEan(null, EAN);

        // then
        assertThat(product).map(ProductData::name).contains("Global g1");
    }

    @Test
    void groupIsFoundInPimByItsPimIdBeforeTheTaxonomy() {
        // given
        taxonomy.add(record(EAN));
        ProductDataResolver resolver = resolver(globalEntry("g1", OTHER_EAN, "M-2", "GPU"));

        // when
        Optional<ProductData> product = resolver.ofGroup(STORE_ID, InventoryKey.fromPimIdAndEan("g1", EAN));

        // then
        assertThat(product).map(ProductData::name).contains("Global g1");
    }

    @Test
    void taxonomyAnswersForAGroupPimDoesNotKnow() {
        // given
        taxonomy.add(record(EAN));
        ProductDataResolver resolver = resolver(globalEntry("g1", OTHER_EAN, "M-2", "GPU"));

        // when
        Optional<ProductData> product = resolver.ofGroup(STORE_ID, InventoryKey.fromEan(EAN));

        // then
        assertThat(product).map(ProductData::name).contains("Taxonomy name");
    }

    @Test
    void groupNobodyKnowsHasNoData() {
        // given
        ProductDataResolver resolver = resolver(globalEntry("g1", OTHER_EAN, "M-2", "GPU"));

        // when
        Optional<ProductData> product = resolver.ofGroup(STORE_ID, InventoryKey.fromEan(EAN));

        // then
        assertThat(product).isEmpty();
    }

    @Test
    void storeEntryIsFoundByItsPimIdInItsStoreOnly() {
        // given
        ProductDataResolver resolver = resolver(storeEntry("s1-1", STORE_ID, EAN, "M-1", "GPU"));

        // when
        Optional<ProductData> inOwner = resolver.ofPimId(STORE_ID, "s1-1");
        Optional<ProductData> inOther = resolver.ofPimId("s2", "s1-1");

        // then
        assertThat(inOwner).map(ProductData::name).contains("Store s1-1");
        assertThat(inOther).isEmpty();
    }

    private static PimEntry entryWithNameOnly(String pimId, String gtin) {
        return new PimEntry(pimId, ScopedPimCatalog.identifiers(gtin, null), " ", "Global " + pimId, null, null, true,
                null, null);
    }

    private static Taxonomy record(String ean) {
        return new Taxonomy(ean, "M-1", "Samsung", "Taxonomy name", "Dyski SSD", 1, 9, 60);
    }
}
