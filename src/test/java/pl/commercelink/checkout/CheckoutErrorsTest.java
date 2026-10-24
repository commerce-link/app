package pl.commercelink.checkout;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.payments.api.PaymentLink;
import pl.commercelink.payments.api.PaymentProvider;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.InvoicingConfiguration;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutErrorsTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "catalog-1";
    private static final String PRICELIST_ID = "pricelist-1";
    private static final String PIM_ID = "pim-1";
    private static final String PROVIDER = "paynow";

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

    @InjectMocks
    private Checkout checkout;

    private Store store;

    @BeforeEach
    void setUp() {
        store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Nova Tech");
        store.setCheckoutConfiguration(new CheckoutConfiguration());
        store.setInvoicingConfiguration(new InvoicingConfiguration());
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        store.setPayments(List.of(new PaymentIntegration(PROVIDER, true)));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        ProductCatalog catalog = new ProductCatalog(STORE_ID, CATALOG_ID);
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(pricelistRepository.findTopNPricelist(STORE_ID, CATALOG_ID, 1))
                .thenReturn(List.of(new Pricelist(PRICELIST_ID, "2026-10-24")));
        when(pricelistRepository.find(STORE_ID, CATALOG_ID, PRICELIST_ID))
                .thenReturn(new Pricelist(PRICELIST_ID, List.of(product(PIM_ID))));

        when(paymentProviderFactory.get(store, PROVIDER)).thenReturn(paymentProvider);
        when(paymentProvider.createPaymentLink(any())).thenReturn(new PaymentLink("https://pay.example/1", "GET", Map.of()));
    }

    @Test
    void validRequestCreatesPaymentLink() {
        // given
        CheckoutRequest request = request(validRequest());

        // when
        CheckoutResponse response = checkout.create(STORE_ID, request);

        // then
        assertThat(response.getUrl()).isEqualTo("https://pay.example/1");
    }

    @Test
    void missingBillingDetailsIsBillingDetailsMissing() {
        // given
        Map<String, Object> body = validRequest();
        body.remove("billingDetails");

        // when / then
        assertCheckoutError(body, CheckoutError.BillingDetailsMissing);
    }

    @Test
    void incompleteShippingDetailsIsShippingDetailsMissing() {
        // given
        Map<String, Object> body = validRequest();
        Map<String, Object> shipping = shippingDetails();
        shipping.remove("phone");
        body.put("shippingDetails", shipping);

        // when / then
        assertCheckoutError(body, CheckoutError.ShippingDetailsMissing);
    }

    @Test
    void pricelistOutsideAcceptedOnesIsPricelistOutdated() {
        // given
        Map<String, Object> body = validRequest();
        body.put("items", List.of(item(PIM_ID, "pricelist-old")));

        // when / then
        assertCheckoutError(body, CheckoutError.PricelistOutdated);
    }

    @Test
    void unreadablePricelistIsPricelistOutdated() {
        // given
        when(pricelistRepository.find(STORE_ID, CATALOG_ID, PRICELIST_ID)).thenReturn(null);

        // when / then
        assertCheckoutError(validRequest(), CheckoutError.PricelistOutdated);
    }

    @Test
    void itemMissingFromPricelistIsItemNotFound() {
        // given
        Map<String, Object> body = validRequest();
        body.put("items", List.of(item("pim-gone", PRICELIST_ID)));

        // when / then
        assertCheckoutError(body, CheckoutError.ItemNotFound);
    }

    @Test
    void unknownDeliveryOptionIsDeliveryOptionNotFound() {
        // given
        Map<String, Object> body = validRequest();
        body.put("deliveryOptionId", "delivery-gone");

        // when / then
        assertCheckoutError(body, CheckoutError.DeliveryOptionNotFound);
    }

    @Test
    void unknownPaymentOptionIsPaymentOptionNotFound() {
        // given
        Map<String, Object> body = validRequest();
        body.put("paymentOptionId", "gateway-gone");

        // when / then
        assertCheckoutError(body, CheckoutError.PaymentOptionNotFound);
    }

    @Test
    void storeWithoutDefaultGatewayIsPaymentUnavailable() {
        // given
        store.setPayments(List.of(new PaymentIntegration(PROVIDER, false)));

        // when / then
        assertCheckoutError(validRequest(), CheckoutError.PaymentUnavailable);
    }

    @Test
    void gatewayThatCannotBeCreatedIsPaymentUnavailable() {
        // given
        IllegalStateException cause = new IllegalStateException("adapter not installed");
        when(paymentProviderFactory.get(store, PROVIDER)).thenThrow(cause);

        // when / then
        assertThatThrownBy(() -> checkout.create(STORE_ID, request(validRequest())))
                .isInstanceOfSatisfying(CheckoutException.class, e -> {
                    assertThat(e.getError()).isEqualTo(CheckoutError.PaymentUnavailable);
                    assertThat(e.getCause()).isSameAs(cause);
                });
    }

    @Test
    void gatewayFailingToCreateLinkIsPaymentUnavailable() {
        // given
        RuntimeException cause = new RuntimeException("gateway timeout");
        when(paymentProvider.createPaymentLink(any())).thenThrow(cause);

        // when / then
        assertThatThrownBy(() -> checkout.create(STORE_ID, request(validRequest())))
                .isInstanceOfSatisfying(CheckoutException.class, e -> {
                    assertThat(e.getError()).isEqualTo(CheckoutError.PaymentUnavailable);
                    assertThat(e.getCause()).isSameAs(cause);
                });
    }

    private void assertCheckoutError(Map<String, Object> body, CheckoutError expected) {
        CheckoutRequest request = request(body);
        assertThatThrownBy(() -> checkout.create(STORE_ID, request))
                .isInstanceOfSatisfying(CheckoutException.class, e -> assertThat(e.getError()).isEqualTo(expected));
    }

    private static CheckoutRequest request(Map<String, Object> body) {
        return new ObjectMapper().convertValue(body, CheckoutRequest.class);
    }

    private static Map<String, Object> validRequest() {
        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(item(PIM_ID, PRICELIST_ID)));
        body.put("billingDetails", billingDetails());
        body.put("shippingDetails", shippingDetails());
        return body;
    }

    private static Map<String, Object> item(String pimId, String pricelistId) {
        return Map.of("itemId", pimId, "qty", 1, "catalogId", CATALOG_ID, "pricelistId", pricelistId);
    }

    private static Map<String, Object> billingDetails() {
        return new HashMap<>(Map.of("name", "Jan Kowalski", "streetAndNumber", "Zakopiańska 58", "postalCode", "30-418",
                "city", "Kraków", "country", "PL", "email", "jan@example.com"));
    }

    private static Map<String, Object> shippingDetails() {
        return new HashMap<>(Map.of("name", "Jan", "surname", "Kowalski", "streetAndNumber", "Zakopiańska 58",
                "postalCode", "30-418", "city", "Kraków", "country", "PL", "email", "jan@example.com", "phone", "500600700"));
    }

    private static AvailabilityAndPrice product(String pimId) {
        return new AvailabilityAndPrice(pimId, "5901234123457", "MFN-1", "Acme", "", "Karta graficzna", "Karty graficzne",
                100, 5, 2, 100, false);
    }
}
