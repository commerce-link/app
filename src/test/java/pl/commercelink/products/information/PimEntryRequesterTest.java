package pl.commercelink.products.information;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimEntryRequest;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.products.Product;
import pl.commercelink.products.brand.BrandMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PimEntryRequesterTest {

    private static final String STORE_ID = "store-1";
    private static final String EAN = "4719331361699";

    @Mock
    private PimCatalog pimCatalog;

    @Mock
    private PimView pimView;

    @Mock
    private BrandMapper brandMapper;

    @BeforeEach
    void setUp() {
        when(pimCatalog.withStoreData(any())).thenReturn(pimView);
        when(brandMapper.unifyBrand(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pimView.findByGtin(any())).thenReturn(Optional.empty());
        when(pimView.findByGtinOrMpnAndBrand(any(), any(), any())).thenReturn(Optional.empty());
    }

    private PimEntryRequester requester(String env) {
        return new PimEntryRequester(pimCatalog, brandMapper, env);
    }

    private static Product product(String pimId, String ean, String mfn, String brand) {
        return new Product("cat-1", pimId, ean, mfn, brand, "l", "MSI RTX 5070", "Default");
    }

    @Test
    void pendingProductIsRequestedWithTheManagedPriority() {
        // when
        requester("prod").requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", "MSI"));

        // then
        verify(pimCatalog).submit(new PimEntryRequest(EAN, "MFN-1", "MSI", 100));
    }

    @Test
    void productWithAPimIdIsNotRequested() {
        // when
        requester("prod").requestMissingEntry(STORE_ID, product("pim-1", EAN, "MFN-1", "MSI"));

        // then
        verify(pimCatalog, never()).submit(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void productWithoutAnEanIsNotRequested(String ean) {
        // when
        requester("prod").requestMissingEntry(STORE_ID, product(null, ean, "MFN-1", null));

        // then
        verify(pimCatalog, never()).submit(any());
    }

    @Test
    void productThePimKnowsByManufacturerCodeAndBrandIsNotRequested() {
        // given
        when(pimView.findByGtinOrMpnAndBrand(EAN, "MFN-1", "MSI")).thenReturn(Optional.of(mock(PimEntry.class)));

        // when
        requester("prod").requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", "MSI"));

        // then
        verify(pimCatalog, never()).submit(any());
    }

    @Test
    void productWithoutABrandIsCheckedByEanOnlyAndRequested() {
        // when
        requester("prod").requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", null));

        // then
        verify(pimView).findByGtin(EAN);
        verify(pimCatalog).submit(new PimEntryRequest(EAN, "MFN-1", null, 100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "localhost", "test"})
    void nothingIsAskedOfThePimOutsideProd(String env) {
        // when
        requester(env).requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", "MSI"));

        // then
        verifyNoInteractions(pimCatalog);
    }

    @Test
    void failedSubmitDoesNotReachTheCaller() {
        // given
        doThrow(new IllegalStateException("SQS not configured")).when(pimCatalog).submit(any());

        // when / then
        assertThatCode(() -> requester("prod").requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", "MSI")))
                .doesNotThrowAnyException();
    }

    @Test
    void failureForOneProductDoesNotStopTheRest() {
        // given
        PimEntryRequest second = new PimEntryRequest("5901234567890", "MFN-2", "ASUS", 100);
        doThrow(new IllegalStateException("boom")).when(pimCatalog).submit(new PimEntryRequest(EAN, "MFN-1", "MSI", 100));

        // when
        requester("prod").requestMissingEntries(STORE_ID, List.of(
                product(null, EAN, "MFN-1", "MSI"), product(null, "5901234567890", "MFN-2", "ASUS")));

        // then
        verify(pimCatalog).submit(second);
    }

    @Test
    void failedLookupDoesNotReachTheCaller() {
        // given
        when(pimCatalog.withStoreData(STORE_ID)).thenThrow(new IllegalStateException("index not loaded"));

        // when / then
        assertThatCode(() -> requester("prod").requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", "MSI")))
                .doesNotThrowAnyException();
    }
}
