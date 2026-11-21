package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.products.ProductDataFixture;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimEntryIndex;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class InventoryLoadTest {

    private static final String EAN_176 = "6977728943176";
    private static final String EAN_169 = "6977728943169";

    @Mock
    private Warehouse warehouse;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private StoreInventoryProvider storeInventoryProvider;

    private final GlobalMatchedInventory globalInventory = new GlobalMatchedInventory();

    @Test
    void loadKeepsTheEansOfAFlaggedPairApart() {
        // given
        Inventory inventory = inventoryWith(InventoryMatchingProperties.allEnabled(), ScopedPimCatalog.of(flaggedMova()));
        InventoryItem kosatec = mova(EAN_176, 1200, "Kosatec");
        InventoryItem wave = mova(EAN_169, 1210, "Wave");

        // when
        inventory.init(List.of(List.of(kosatec), List.of(wave)));

        // then
        assertThat(offerSets()).containsExactlyInAnyOrder(Set.of(kosatec), Set.of(wave));
        assertThat(globalInventory.generation().eanOnlyCodes()).isEqualTo(PimEanOnlyCodes.of(List.of(flaggedMova())));
    }

    @Test
    void switchOffIgnoresFlaggedEntries() {
        // given
        Inventory inventory = inventoryWith(new InventoryMatchingProperties(true, true, true, true, false, true),
                ScopedPimCatalog.of(flaggedMova()));
        InventoryItem kosatec = mova(EAN_176, 1200, "Kosatec");
        InventoryItem wave = mova(EAN_169, 1210, "Wave");

        // when
        inventory.init(List.of(List.of(kosatec), List.of(wave)));

        // then
        assertThat(offerSets()).containsExactlyInAnyOrder(Set.of(kosatec, wave));
        assertThat(globalInventory.generation().eanOnlyCodes()).isEqualTo(VariantCodes.none());
    }

    @Test
    void regroupWithFlagThenWithoutGivesTheGroupsOfAPlainRegroup() {
        // given
        AtomicReference<PimEntryIndex> index = new AtomicReference<>(indexOf(mova(false)));
        Inventory inventory = inventoryWith(InventoryMatchingProperties.allEnabled(), switchableCatalog(index));
        InventoryItem kosatec = mova(EAN_176, 1200, "Kosatec");
        InventoryItem wave = mova(EAN_169, 1210, "Wave");
        InventoryItem other = other("Kosatec");
        inventory.init(List.of(List.of(kosatec, other), List.of(wave)));
        inventory.regroup();
        Set<Set<InventoryItem>> plain = offerSets();

        // when
        index.set(indexOf(mova(true)));
        inventory.regroup();
        Set<Set<InventoryItem>> flagged = offerSets();
        index.set(indexOf(mova(false)));
        inventory.regroup();

        // then
        assertThat(plain).containsExactlyInAnyOrder(Set.of(kosatec, wave), Set.of(other));
        assertThat(flagged).containsExactlyInAnyOrder(Set.of(kosatec), Set.of(wave), Set.of(other));
        assertThat(offerSets()).isEqualTo(plain);
        assertThat(globalInventory.generation().eanOnlyCodes()).isEqualTo(VariantCodes.none());
    }

    @Test
    void regroupKeepsEveryGroupedOffer() {
        // given
        AtomicReference<PimEntryIndex> index = new AtomicReference<>(indexOf(mova(true)));
        Inventory inventory = inventoryWith(InventoryMatchingProperties.allEnabled(), switchableCatalog(index));
        inventory.init(List.of(List.of(mova(EAN_176, 1200, "Kosatec"), other("Kosatec")),
                List.of(mova(EAN_169, 1210, "Wave"))));
        int before = globalInventory.allItems().size();

        // when
        index.set(indexOf(mova(false)));
        inventory.regroup();

        // then
        assertThat(globalInventory.allItems()).hasSize(before);
        assertThat(before).isEqualTo(3);
    }

    // a catalog whose index the test swaps, as a PIM refresh does
    private static PimCatalog switchableCatalog(AtomicReference<PimEntryIndex> index) {
        return mock(PimCatalog.class, invocation -> switch (invocation.getMethod().getName()) {
            case "withGlobalData" -> index.get().global();
            case "withStoreData" -> index.get().forStore(invocation.getArgument(0));
            default -> null;
        });
    }

    private static PimEntryIndex indexOf(PimEntry... entries) {
        return new PimEntryIndex(List.of(entries), UnaryOperator.identity(), UnaryOperator.identity());
    }

    private static PimEntry mova(boolean eanOnlyMatching) {
        return new PimEntry("pim-mova", List.of(new PimIdentifier("6977728943999", PimIdentifierType.GTIN),
                new PimIdentifier("SHINE10", PimIdentifierType.MPN)),
                "Mova", "Name", null, null, true, null, null, null, null, null, eanOnlyMatching);
    }

    private static InventoryItem other(String supplier) {
        return new InventoryItem("Logitech", null, "5099206000001", "G502", supplier, 200, "PLN", 5, 1, true, false, false);
    }

    private Inventory inventoryWith(InventoryMatchingProperties properties, PimCatalog pimCatalog) {
        InventoryAutoDiscovery autoDiscovery = new InventoryAutoDiscovery(pimCatalog, supplierRegistry, properties);
        return new Inventory(warehouse, storesRepository, autoDiscovery, supplierRegistry,
                storeInventoryProvider, globalInventory, pimCatalog, properties,
                ProductDataFixture.resolverOver(taxonomyCache));
    }

    private Set<Set<InventoryItem>> offerSets() {
        return globalInventory.all().stream()
                .map(group -> Set.copyOf(group.getInventoryItems()))
                .collect(Collectors.toSet());
    }

    // the entry lists neither EAN of the offers, so it can take them only by code
    private static PimEntry flaggedMova() {
        return new PimEntry("pim-mova", List.of(new PimIdentifier("6977728943999", PimIdentifierType.GTIN),
                new PimIdentifier("SHINE10", PimIdentifierType.MPN)),
                "Mova", "Name", null, null, true, null, null, null, null, null, true);
    }

    private static InventoryItem mova(String ean, double netPrice, String supplier) {
        return new InventoryItem("Mova", null, ean, "SHINE10", supplier, netPrice, "PLN", 5, 1, true, false, false);
    }
}
