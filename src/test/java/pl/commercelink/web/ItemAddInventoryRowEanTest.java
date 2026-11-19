package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.InventoryViewFixture;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.products.ProductDataResolver;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyRepository;
import pl.commercelink.web.dtos.ItemAddInventoryRow;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The dialog posts the row's EAN back, and the order or offer item is looked up and saved under it: the EAN must lead
 * to the same product with its offers again, whatever primary GTIN the PIM entry describing the row has.
 */
class ItemAddInventoryRowEanTest {

    private static final String STORE_ID = "store-1";
    private static final String PRIMARY_GTIN = "5900000000999";
    private static final String OFFERED_EAN = "5900000000101";
    private static final String OTHER_OFFERED_EAN = "5900000000102";

    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class, RETURNS_DEEP_STUBS);
    private final TaxonomyCache taxonomy = new TaxonomyCache(mock(TaxonomyRepository.class));
    private final Inventory inventory = mock(Inventory.class);
    private final ItemAddInventoryController controller = new ItemAddInventoryController(inventory);

    private MockedStatic<CustomSecurityContext> securityStub;

    @BeforeEach
    void storeOfTheSession() {
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    @Test
    void rowOfAStoreEntryGivesAnEanThatFindsTheOfferedProductAgain() {
        // given
        PimEntry storeEntry = new PimEntry("st-1", gtins(PRIMARY_GTIN, OFFERED_EAN), "Brand", "Store product",
                "Procesory", null, true, null, null, "989", STORE_ID);
        InventoryView view = viewOf(group(OFFERED_EAN), storeEntry);

        // when
        ItemAddInventoryRow row = controller.find(OFFERED_EAN).get(0);

        // then
        assertThat(row.name()).isEqualTo("Store product");
        assertThat(row.ean()).isEqualTo(OFFERED_EAN);
        assertThat(view.findByInventoryKey(InventoryKey.fromEan(row.ean())).hasAnyOffers()).isTrue();
    }

    @Test
    void rowOfAnApprovedGlobalEntryGivesTheEanItsOffersCarry() {
        // given
        PimEntry globalEntry = new PimEntry("g1", gtins(PRIMARY_GTIN, OFFERED_EAN), "Brand", "Global product",
                "Procesory", null, true, null, null, "989");
        InventoryView view = viewOf(group(OFFERED_EAN), globalEntry);

        // when
        ItemAddInventoryRow row = controller.find(OFFERED_EAN).get(0);

        // then
        assertThat(row.name()).isEqualTo("Global product");
        assertThat(row.ean()).isEqualTo(OFFERED_EAN);
        MatchedInventory again = view.findByInventoryKey(InventoryKey.fromEan(row.ean()));
        assertThat(again.getInventoryItems()).extracting(InventoryItem::ean).contains(row.ean());
    }

    @Test
    void offeredPrimaryGtinIsTheRowsEanWhicheverOfTheProductsEansWasTyped() {
        // given
        PimEntry globalEntry = new PimEntry("g1", gtins(PRIMARY_GTIN, OFFERED_EAN), "Brand", "Global product",
                "Procesory", null, true, null, null, "989");
        viewOf(group(OFFERED_EAN, PRIMARY_GTIN), globalEntry);

        // when
        ItemAddInventoryRow row = controller.find(OFFERED_EAN).get(0);

        // then
        assertThat(row.ean()).isEqualTo(PRIMARY_GTIN);
    }

    @Test
    void typedEanWithoutAnOfferGivesAnEanAnOfferCarries() {
        // given
        PimEntry globalEntry = new PimEntry("g1", gtins(PRIMARY_GTIN, OFFERED_EAN), "Brand", "Global product",
                "Procesory", null, true, null, null, "989");
        MatchedInventory group = new MatchedInventory(new InventoryKey(PRIMARY_GTIN, "M-1"),
                List.of(offer(OTHER_OFFERED_EAN, "M-1")), supplierRegistry);
        InventoryView view = viewOf(group, globalEntry);

        // when
        ItemAddInventoryRow row = controller.find(PRIMARY_GTIN).get(0);

        // then
        assertThat(row.ean()).isEqualTo(OTHER_OFFERED_EAN);
        assertThat(view.findByInventoryKey(InventoryKey.fromEan(row.ean())).hasAnyOffers()).isTrue();
    }

    @Test
    void rowWithoutAPimEntryKeepsTheEanOfTheTaxonomyRecord() {
        // given
        taxonomy.add(new Taxonomy(OFFERED_EAN, "M-" + OFFERED_EAN, "Brand", "Taxonomy product", "Procesory", 1, null,
                null, null, "989"));
        InventoryView view = viewOf(group(OFFERED_EAN));

        // when
        ItemAddInventoryRow row = controller.find(OFFERED_EAN).get(0);

        // then
        assertThat(row.name()).isEqualTo("Taxonomy product");
        assertThat(row.ean()).isEqualTo(OFFERED_EAN);
        assertThat(view.findByInventoryKey(InventoryKey.fromEan(row.ean())).hasAnyOffers()).isTrue();
    }

    private InventoryView viewOf(MatchedInventory group, PimEntry... entries) {
        ProductDataResolver resolver = new ProductDataResolver(ScopedPimCatalog.of(entries), taxonomy);
        InventoryView view = InventoryViewFixture.over(STORE_ID, resolver, supplierRegistry, group);
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(view);
        return view;
    }

    private MatchedInventory group(String... eans) {
        List<InventoryItem> offers = Arrays.stream(eans).map(ean -> offer(ean, "M-" + eans[0])).toList();
        return new MatchedInventory(new InventoryKey(eans[0], "M-" + eans[0]), offers, supplierRegistry);
    }

    private static InventoryItem offer(String ean, String mfn) {
        return new InventoryItem(ean, mfn, 100.0, "PLN", 5, 1, "Elko", true, true, false);
    }

    private static List<PimIdentifier> gtins(String primary, String other) {
        return List.of(new PimIdentifier(primary, PimIdentifierType.GTIN), new PimIdentifier(other, PimIdentifierType.GTIN));
    }
}
