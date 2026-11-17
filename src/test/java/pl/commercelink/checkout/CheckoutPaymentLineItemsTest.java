package pl.commercelink.checkout;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.payments.api.PaymentLineItem;
import pl.commercelink.payments.api.PaymentProvider;
import pl.commercelink.payments.api.PaymentRequest;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.InvoicingConfiguration;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutPaymentLineItemsTest {

    private static final String STORE_ID = "store-1";
    private static final String BASKET_ID = "basket-1";
    private static final String PROVIDER = "paynow";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private BasketsRepository basketsRepository;
    @Mock
    private PaymentProviderFactory paymentProviderFactory;
    @Mock
    private PaymentProvider paymentProvider;
    @Captor
    private ArgumentCaptor<PaymentRequest> paymentRequest;

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
        when(paymentProviderFactory.get(store, PROVIDER)).thenReturn(paymentProvider);
    }

    @ParameterizedTest
    @CsvSource({"19.99, 1999", "1151.10, 115110", "0.29, 29"})
    void lineAmountIsRoundedNotTruncated(double unitPrice, int expectedAmount) {
        // given
        givenBasket(item(unitPrice, 1, false), null);

        // when
        checkout.create(STORE_ID, BASKET_ID, PROVIDER);

        // then
        verify(paymentProvider).createPaymentLink(paymentRequest.capture());
        assertThat(paymentRequest.getValue().lineItems())
                .extracting(PaymentLineItem::amount, PaymentLineItem::quantity)
                .containsExactly(tuple(expectedAmount, 1));
    }

    @Test
    void paymentTotalEqualsBasketTotalWithDelivery() {
        // given
        DeliveryOption courier = new DeliveryOption();
        courier.setName("Kurier");
        courier.setPrice(4.35);
        store.getCheckoutConfiguration().addDeliveryOption(courier);
        givenBasket(item(19.99, 1, false), courier.getId());

        // when
        checkout.create(STORE_ID, BASKET_ID, PROVIDER);

        // then
        verify(paymentProvider).createPaymentLink(paymentRequest.capture());
        PaymentRequest request = paymentRequest.getValue();
        assertThat(request.lineItems())
                .extracting(PaymentLineItem::amount, PaymentLineItem::quantity)
                .containsExactly(tuple(1999, 1));
        assertThat(request.shippingItem().amount()).isEqualTo(435);
        int linesTotal = request.lineItems().stream().mapToInt(line -> line.amount() * line.quantity()).sum();
        assertThat(linesTotal + request.shippingItem().amount()).isEqualTo(2434);
    }

    @Test
    void consolidatedAmountIsRounded() {
        // given
        givenBasket(item(1151.10, 2, true), null);

        // when
        checkout.create(STORE_ID, BASKET_ID, PROVIDER);

        // then
        verify(paymentProvider).createPaymentLink(paymentRequest.capture());
        assertThat(paymentRequest.getValue().lineItems())
                .extracting(PaymentLineItem::amount, PaymentLineItem::quantity)
                .containsExactly(tuple(230220, 1));
    }

    @Test
    void shippingAmountIsRounded() {
        // given
        DeliveryOption courier = new DeliveryOption();
        courier.setName("Kurier");
        courier.setPrice(4.35);
        store.getCheckoutConfiguration().addDeliveryOption(courier);
        givenBasket(item(100.00, 1, false), courier.getId());

        // when
        checkout.create(STORE_ID, BASKET_ID, PROVIDER);

        // then
        verify(paymentProvider).createPaymentLink(paymentRequest.capture());
        assertThat(paymentRequest.getValue().shippingItem().amount()).isEqualTo(435);
    }

    @Test
    void onlyTheChosenVariantOfAGroupIsCharged() {
        // given
        BasketItem cheaper = item(500.00, 1, false);
        cheaper.setVariantGroupId("gpu");
        BasketItem dearer = item(700.00, 1, false);
        dearer.setVariantGroupId("gpu");
        Basket basket = givenBasket(List.of(cheaper, dearer, item(100.00, 1, false)), null);
        basket.selectVariant("gpu", dearer.getPosition());

        // when
        checkout.create(STORE_ID, BASKET_ID, PROVIDER);

        // then
        verify(paymentProvider).createPaymentLink(paymentRequest.capture());
        assertThat(paymentRequest.getValue().lineItems())
                .extracting(PaymentLineItem::amount, PaymentLineItem::quantity)
                .containsExactly(tuple(70000, 1), tuple(10000, 1));
    }

    @Test
    void servicesAreChargedAndEveryLineKeepsItsQuantity() {
        // given
        BasketItem assembly = item(150.00, 1, false);
        assembly.setService(true);
        givenBasket(List.of(item(129.90, 3, false), assembly), null);

        // when
        checkout.create(STORE_ID, BASKET_ID, PROVIDER);

        // then
        verify(paymentProvider).createPaymentLink(paymentRequest.capture());
        PaymentRequest request = paymentRequest.getValue();
        assertThat(request.lineItems())
                .extracting(PaymentLineItem::amount, PaymentLineItem::quantity)
                .containsExactly(tuple(15000, 1), tuple(12990, 3));
        assertThat(request.lineItems().stream().mapToInt(line -> line.amount() * line.quantity()).sum()).isEqualTo(53970);
        assertThat(request.shippingItem()).isNull();
    }

    private void givenBasket(BasketItem item, String deliveryOptionId) {
        givenBasket(List.of(item), deliveryOptionId);
    }

    private Basket givenBasket(List<BasketItem> items, String deliveryOptionId) {
        BillingDetails billingDetails = new BillingDetails();
        billingDetails.setEmail("jan@example.com");
        Basket basket = new Basket.Builder(STORE_ID)
                .withBasketId(BASKET_ID)
                .withBillingDetails(billingDetails)
                .withBasketItems(items)
                .withDeliveryOptionId(deliveryOptionId)
                .build();
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket));
        return basket;
    }

    private BasketItem item(double unitPrice, long qty, boolean consolidated) {
        return new BasketItem("pim-1", "Karta graficzna", "MFN-1", "Karty graficzne", unitPrice, 0, qty, "catalog-1", 1, consolidated);
    }
}
