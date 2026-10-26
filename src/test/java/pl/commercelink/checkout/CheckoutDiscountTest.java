package pl.commercelink.checkout;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.DiscountCodesRepository;
import pl.commercelink.discounts.DiscountService;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.payments.api.PaymentLineItem;
import pl.commercelink.payments.api.PaymentLink;
import pl.commercelink.payments.api.PaymentProvider;
import pl.commercelink.payments.api.PaymentRequest;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.InvoicingConfiguration;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutDiscountTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "catalog-1";
    private static final String PRICELIST_ID = "pricelist-1";
    private static final String DEFAULT_PROVIDER = "paynow";
    private static final String OTHER_PROVIDER = "przelewy24";
    private static final String CODE = "LATO-10";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private BasketsRepository basketsRepository;
    @Mock
    private PricelistRepository pricelistRepository;
    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private PaymentProviderFactory paymentProviderFactory;
    @Mock
    private PaymentProvider paymentProvider;
    @Mock
    private DiscountCodesRepository discountCodesRepository;
    @Captor
    private ArgumentCaptor<PaymentRequest> paymentRequest;
    @Captor
    private ArgumentCaptor<Basket> savedBasket;

    private Checkout checkout;
    private Store store;

    @BeforeEach
    void setUp() {
        checkout = new Checkout(storesRepository, basketsRepository, pricelistRepository, productCatalogRepository,
                paymentProviderFactory, new DiscountService(discountCodesRepository), "https://app.example");

        store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Nova Tech");
        CheckoutConfiguration checkoutConfiguration = new CheckoutConfiguration();
        DeliveryOption inpost = new DeliveryOption();
        inpost.setId("inpost");
        inpost.setName("Kurier InPost");
        inpost.setPrice(15.00);
        checkoutConfiguration.setDeliveryOptions(new LinkedList<>(List.of(inpost)));
        store.setCheckoutConfiguration(checkoutConfiguration);
        store.setInvoicingConfiguration(new InvoicingConfiguration());
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        store.setPayments(List.of(new PaymentIntegration(DEFAULT_PROVIDER, true), new PaymentIntegration(OTHER_PROVIDER, false)));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(new ProductCatalog(STORE_ID, CATALOG_ID));
        when(pricelistRepository.findTopNPricelist(STORE_ID, CATALOG_ID, 1))
                .thenReturn(List.of(new Pricelist(PRICELIST_ID, "2026-10-24")));
        when(pricelistRepository.find(STORE_ID, CATALOG_ID, PRICELIST_ID)).thenReturn(new Pricelist(PRICELIST_ID, List.of(
                row("pim-gpu", "Karta graficzna", "Karty graficzne", 1000, false),
                row("pim-ram", "Pamięć RAM", "Pamięci", 200, false),
                row("pim-srv", "Montaż", "Usługi", 100, true))));

        when(paymentProviderFactory.get(any(), any())).thenReturn(paymentProvider);
        when(paymentProvider.createPaymentLink(any())).thenReturn(new PaymentLink("https://pay.example/1", "GET", Map.of()));
    }

    @Test
    void checkoutRequestWithCodeCreatesDiscountedPaymentLink() {
        // given
        givenCode(percentage(10));

        // when
        CheckoutResponse response = checkout.create(STORE_ID, request(withCode(validRequest("pim-gpu", "pim-ram"))));

        // then
        assertThat(response.getUrl()).isEqualTo("https://pay.example/1");
        verify(paymentProvider).createPaymentLink(paymentRequest.capture());
        assertThat(paymentRequest.getValue().lineItems()).extracting(PaymentLineItem::amount, PaymentLineItem::quantity)
                .containsExactly(tuple(90000, 1), tuple(18000, 1));
        verify(basketsRepository).save(savedBasket.capture());
        assertThat(savedBasket.getValue().getDiscountCode()).isEqualTo(CODE);
        assertThat(savedBasket.getValue().getDiscountAmount()).isEqualTo(120.00);
    }

    @Test
    void checkoutWithoutCodeIsNotDiscounted() {
        // when
        checkout.create(STORE_ID, request(validRequest("pim-gpu")));

        // then
        verify(discountCodesRepository, never()).find(any(), any());
        verify(basketsRepository).save(savedBasket.capture());
        assertThat(savedBasket.getValue().getDiscountCode()).isNull();
        assertThat(savedBasket.getValue().getBasketItems()).extracting(BasketItem::getUnitPrice).containsExactly(1000.0);
    }

    @Test
    void invalidCodeInCheckoutRequestCreatesNothing() {
        // given
        when(discountCodesRepository.find(STORE_ID, CODE)).thenReturn(Optional.empty());

        // when / then
        assertRejectedWithNothingCreated(withCode(validRequest("pim-gpu")), CheckoutError.DiscountCodeNotFound);
    }

    @Test
    void notApplicableCodeCreatesNothing() {
        // given
        givenCode(percentage(10));

        // when / then
        assertRejectedWithNothingCreated(withCode(validRequest("pim-srv")), CheckoutError.DiscountCodeNotApplicable);
    }

    @Test
    void codeForOtherPaymentMethodIsRejected() {
        // given
        DiscountCode code = percentage(10);
        code.setPaymentProvider(OTHER_PROVIDER);
        givenCode(code);
        Map<String, Object> body = withCode(validRequest("pim-gpu"));
        body.put("paymentOptionId", DEFAULT_PROVIDER);

        // when / then
        assertRejectedWithNothingCreated(body, CheckoutError.DiscountCodePaymentMethodMismatch);
    }

    @Test
    void codeForChosenPaymentMethodIsAccepted() {
        // given
        DiscountCode code = percentage(10);
        code.setPaymentProvider(OTHER_PROVIDER);
        givenCode(code);
        Map<String, Object> body = withCode(validRequest("pim-gpu"));
        body.put("paymentOptionId", OTHER_PROVIDER);

        // when
        checkout.create(STORE_ID, request(body));

        // then
        verify(paymentProviderFactory).get(store, OTHER_PROVIDER);
        verify(basketsRepository).save(savedBasket.capture());
        assertThat(savedBasket.getValue().getDiscountCode()).isEqualTo(CODE);
    }

    @Test
    void checkoutWithoutPaymentOptionChecksDefaultProvider() {
        // given
        DiscountCode code = percentage(10);
        code.setPaymentProvider(OTHER_PROVIDER);
        givenCode(code);

        // when / then
        assertRejectedWithNothingCreated(withCode(validRequest("pim-gpu")), CheckoutError.DiscountCodePaymentMethodMismatch);
    }

    @Test
    void basketBelowMinimumCreatesNothing() {
        // given
        DiscountCode code = percentage(10);
        code.setMinOrderValue(1210);
        givenCode(code);
        Map<String, Object> body = withCode(validRequest("pim-gpu", "pim-ram"));
        body.put("deliveryOptionId", "inpost");

        // when / then
        assertRejectedWithNothingCreated(body, CheckoutError.DiscountCodeMinimumNotReached);
    }

    @Test
    void codeWithoutGatewayWorksWithAnyGateway() {
        // given
        givenCode(percentage(10));
        Map<String, Object> viaOther = withCode(validRequest("pim-gpu"));
        viaOther.put("paymentOptionId", OTHER_PROVIDER);

        // when
        checkout.create(STORE_ID, request(withCode(validRequest("pim-gpu"))));
        checkout.create(STORE_ID, request(viaOther));

        // then
        verify(paymentProviderFactory).get(store, DEFAULT_PROVIDER);
        verify(paymentProviderFactory).get(store, OTHER_PROVIDER);
        verify(paymentProvider, times(2)).createPaymentLink(paymentRequest.capture());
        assertThat(paymentRequest.getAllValues()).allSatisfy(r ->
                assertThat(r.lineItems()).extracting(PaymentLineItem::amount).containsExactly(90000));
    }

    private void assertRejectedWithNothingCreated(Map<String, Object> body, CheckoutError expected) {
        CheckoutRequest request = request(body);
        assertThatThrownBy(() -> checkout.create(STORE_ID, request))
                .isInstanceOfSatisfying(CheckoutException.class, e -> assertThat(e.getError()).isEqualTo(expected));
        verify(basketsRepository, never()).save(any());
        verify(paymentProvider, never()).createPaymentLink(any());
    }

    private void givenCode(DiscountCode code) {
        when(discountCodesRepository.find(STORE_ID, CODE)).thenReturn(Optional.of(code));
    }

    private static DiscountCode percentage(double value) {
        DiscountCode code = new DiscountCode();
        code.setStoreId(STORE_ID);
        code.setCode(CODE);
        code.setName("Kampania letnia");
        code.setActive(true);
        code.setType(DiscountType.Percentage);
        code.setValue(value);
        return code;
    }

    private static CheckoutRequest request(Map<String, Object> body) {
        return new ObjectMapper().convertValue(body, CheckoutRequest.class);
    }

    private static Map<String, Object> withCode(Map<String, Object> body) {
        body.put("discountCode", " lato-10 ");
        return body;
    }

    private static Map<String, Object> validRequest(String... pimIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(pimIds).stream()
                .map(id -> Map.of("itemId", id, "qty", 1, "catalogId", CATALOG_ID, "pricelistId", PRICELIST_ID))
                .toList());
        body.put("billingDetails", Map.of("name", "Jan Kowalski", "streetAndNumber", "Zakopiańska 58", "postalCode", "30-418",
                "city", "Kraków", "country", "PL", "email", "jan@example.com"));
        body.put("shippingDetails", Map.of("name", "Jan", "surname", "Kowalski", "streetAndNumber", "Zakopiańska 58",
                "postalCode", "30-418", "city", "Kraków", "country", "PL", "email", "jan@example.com", "phone", "500600700"));
        return body;
    }

    private static AvailabilityAndPrice row(String pimId, String name, String category, long price, boolean service) {
        return new AvailabilityAndPrice(pimId, "590123412345" + pimId.length(), "MFN-" + pimId, "Acme", "", name, category,
                price, 5, 2, price, service);
    }
}
