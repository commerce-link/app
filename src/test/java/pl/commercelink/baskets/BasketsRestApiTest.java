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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.checkout.CheckoutRequest;
import pl.commercelink.invoicing.InvoicingService;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.util.Collections;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BasketsRestApiTest {

    private static final String STORE_ID = "store-1";
    private static final String BASKET_ID = "basket-1";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private BasketsRepository basketsRepository;
    @Mock
    private InvoicingService invoicingService;
    @Mock
    private PricelistRepository pricelistRepository;
    @Mock
    private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock
    private CheckoutRequest req;

    @InjectMocks
    private BasketsRestApi basketsRestApi;

    @BeforeEach
    void setupExecutorPassThrough() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
        when(req.toBasketItems(STORE_ID, pricelistRepository)).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("updateBasket returns 404 Not Found when the basket does not exist")
    void updateBasketReturnsNotFoundWhenBasketDoesNotExist() {
        // given
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.empty());

        // when
        ResponseEntity<Void> response = basketsRestApi.updateBasket(STORE_ID, BASKET_ID, req);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(optimisticLockingExecutor, never()).modifyAndSave(any(), any(), any());
        verify(basketsRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateBasket applies changes from request payload to existing basket and returns 200 OK")
    void updateBasketAppliesChangesFromRequestAndReturnsOk() {
        // given
        Basket basket = basketBase();
        BillingDetails billing = new BillingDetails();
        billing.setCity("Krakow");
        ShippingDetails shipping = new ShippingDetails();
        shipping.setCity("Warsaw");
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket));
        when(req.getDeliveryOptionId()).thenReturn("delivery-1");
        when(req.getAffiliateId()).thenReturn("aff-1");
        when(req.getBillingDetails()).thenReturn(billing);
        when(req.getShippingDetails()).thenReturn(shipping);

        // when
        ResponseEntity<Void> response = basketsRestApi.updateBasket(STORE_ID, BASKET_ID, req);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        Basket saved = basketCaptor.getValue();
        assertThat(saved.getDeliveryOptionId()).isEqualTo("delivery-1");
        assertThat(saved.getAffiliateId()).isEqualTo("aff-1");
        assertThat(saved.getBillingDetails().getCity()).isEqualTo("Krakow");
        assertThat(saved.getShippingDetails().getCity()).isEqualTo("Warsaw");
    }

    @Test
    @DisplayName("updateBasket preserves existing billing and shipping details when request payload has null fields")
    void updateBasketDoesNotOverwriteBillingOrShippingDetailsWhenDtoFieldsAreNull() {
        // given
        Basket basket = basketBase();
        BillingDetails existingBilling = new BillingDetails();
        existingBilling.setCity("Original-Billing");
        ShippingDetails existingShipping = new ShippingDetails();
        existingShipping.setCity("Original-Shipping");
        basket.setBillingDetails(existingBilling);
        basket.setShippingDetails(existingShipping);

        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket));
        when(req.getBillingDetails()).thenReturn(null);
        when(req.getShippingDetails()).thenReturn(null);

        // when
        basketsRestApi.updateBasket(STORE_ID, BASKET_ID, req);

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        Basket saved = basketCaptor.getValue();
        assertThat(saved.getBillingDetails().getCity()).isEqualTo("Original-Billing");
        assertThat(saved.getShippingDetails().getCity()).isEqualTo("Original-Shipping");
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

    private Basket basketBase() {
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketId(BASKET_ID);
        return basket;
    }
}
