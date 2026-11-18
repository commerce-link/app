package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimEntryIndex;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreEanOnlyCodesTest {

    @Mock
    private PimCatalog pimCatalog;

    @Test
    void takesOnlyThisStoresFlaggedEntriesFromTheStoreView() {
        // given
        PimEntryIndex index = indexOf(
                flagged("SHINE10", "s1"),
                flagged("SHINE11", "s2"),
                flagged("SHINE12", null));
        when(pimCatalog.withStoreData("s1")).thenReturn(index.forStore("s1"));
        StoreEanOnlyCodes storeCodes = new StoreEanOnlyCodes(pimCatalog, InventoryMatchingProperties.allEnabled());

        // when
        VariantCodes codes = storeCodes.of("s1");

        // then
        assertThat(codes.contains("mova", "SHINE10")).isTrue();
        assertThat(codes.size()).isEqualTo(1);
    }

    @Test
    void isEmptyWhenEitherSwitchIsOff() {
        // given
        InventoryMatchingProperties storeOff = new InventoryMatchingProperties(true, true, true, true, true, false);
        InventoryMatchingProperties globalOff = new InventoryMatchingProperties(true, true, true, true, false, true);

        // when
        VariantCodes withStoreOff = new StoreEanOnlyCodes(pimCatalog, storeOff).of("s1");
        VariantCodes withGlobalOff = new StoreEanOnlyCodes(pimCatalog, globalOff).of("s1");

        // then
        assertThat(withStoreOff.size()).isZero();
        assertThat(withGlobalOff.size()).isZero();
        verifyNoInteractions(pimCatalog);
    }

    @Test
    void isEmptyForBlankStoreId() {
        // given
        StoreEanOnlyCodes storeCodes = new StoreEanOnlyCodes(pimCatalog, InventoryMatchingProperties.allEnabled());

        // when
        VariantCodes forNull = storeCodes.of(null);
        VariantCodes forBlank = storeCodes.of(" ");

        // then
        assertThat(forNull.size()).isZero();
        assertThat(forBlank.size()).isZero();
        verifyNoInteractions(pimCatalog);
    }

    private static PimEntryIndex indexOf(PimEntry... entries) {
        return new PimEntryIndex(List.of(entries), UnaryOperator.identity(), UnaryOperator.identity());
    }

    private static PimEntry flagged(String mpn, String ownerStoreId) {
        return new PimEntry("pim-" + mpn, List.of(
                new PimIdentifier("5901234567801", PimIdentifierType.GTIN),
                new PimIdentifier(mpn, PimIdentifierType.MPN)),
                "Mova", "Name", null, null, true, null, null, null, ownerStoreId, null, true);
    }
}
