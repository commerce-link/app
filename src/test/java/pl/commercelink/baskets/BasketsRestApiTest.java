package pl.commercelink.baskets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.checkout.CheckoutRequest;
import pl.commercelink.invoicing.InvoicingService;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Collections;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BasketsRestApiTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private BasketsRepository basketsRepository;
    @Mock
    private InvoicingService invoicingService;
    @Mock
    private PricelistRepository pricelistRepository;
    @Mock
    private CheckoutRequest req;

    @InjectMocks
    private BasketsRestApi basketsRestApi;

    @BeforeEach
    void setUp() {
        when(req.toBasketItems(STORE_ID, pricelistRepository)).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("createBasket creates proforma with configured fallback locale instead of JVM default")
    void createBasketCreatesProformaWithConfiguredFallbackLocale() {
        // given
        ReflectionTestUtils.setField(basketsRestApi, "fallbackLocale", Locale.GERMAN);
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn(STORE_ID);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(req.isSendInvoice()).thenReturn(true);
        InvoicingService.OperationResult result = mock(InvoicingService.OperationResult.class);
        when(invoicingService.createProforma(any(Basket.class), any(Locale.class), eq(true))).thenReturn(result);

        // when
        basketsRestApi.createBasket(req, STORE_ID);

        // then
        ArgumentCaptor<Locale> localeCaptor = ArgumentCaptor.forClass(Locale.class);
        verify(invoicingService).createProforma(any(Basket.class), localeCaptor.capture(), eq(true));
        assertThat(localeCaptor.getValue()).isEqualTo(Locale.GERMAN);
    }
}
