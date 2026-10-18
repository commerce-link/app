package pl.commercelink.web.orders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.ContactDetails;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.web.dtos.ClientDataDto;
import pl.commercelink.web.orders.NewOrderPage.DeliveryState;

import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewOrderPageTest {

    private static final String ID = "8f3a21c7-1b2d-4e5f-9a0b-1c2d3e4f5a6b";

    private Store store;

    @BeforeEach
    void setUp() {
        store = new Store();
        CheckoutConfiguration checkout = new CheckoutConfiguration();
        DeliveryOption courier = option("courier", "Kurier InPost", 20.0, ShipmentType.Courier);
        DeliveryOption locker = option("locker", "Paczkomat", 12.0, ShipmentType.PickupPoint);
        checkout.setDeliveryOptions(new LinkedList<>(List.of(courier, locker)));
        store.setCheckoutConfiguration(checkout);
    }

    @Test
    void basketPageGoesBackToTheBasketPreview() {
        // given
        Basket basket = basket(BasketType.Basket, null);

        // when
        NewOrderPage page = NewOrderPage.of(basket, store);

        // then
        assertThat(page.offer()).isFalse();
        assertThat(page.basketId()).isEqualTo(ID);
        assertThat(page.backHref()).isEqualTo("/dashboard/basket/view/" + ID);
        assertThat(page.sourceName()).isEqualTo(basket.getShortenedBasketId());
        assertThat(page.itemsCount()).isEqualTo(1);
    }

    @Test
    void offerPageGoesBackToTheOfferAndFallsBackToShortIdWithoutName() {
        // given
        Basket named = basket(BasketType.Offer, null);
        named.setName("Gaming PC");
        Basket unnamed = basket(BasketType.Offer, null);
        unnamed.setName("  ");

        // when
        NewOrderPage namedPage = NewOrderPage.of(named, store);
        NewOrderPage unnamedPage = NewOrderPage.of(unnamed, store);

        // then
        assertThat(namedPage.offer()).isTrue();
        assertThat(namedPage.backHref()).isEqualTo("/dashboard/offer/" + ID);
        assertThat(namedPage.sourceName()).isEqualTo("Gaming PC");
        assertThat(unnamedPage.sourceName()).isEqualTo(unnamed.getShortenedBasketId());
    }

    @Test
    void pathSegmentOfTheIdIsEncoded() {
        // given
        Basket basket = basket(BasketType.Offer, null);
        basket.setBasketId("a/b c");

        // when
        NewOrderPage page = NewOrderPage.of(basket, store);

        // then
        assertThat(page.backHref()).isEqualTo("/dashboard/offer/a%2Fb%20c");
    }

    @Test
    void templateIsNotFound() {
        // given
        Basket template = basket(BasketType.OfferTemplate, null);

        // when / then
        assertThatThrownBy(() -> NewOrderPage.of(template, store))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void unknownDeliveryOptionDoesNotThrow() {
        // given
        Basket basket = basket(BasketType.Basket, "removed-option");

        // when
        NewOrderPage page = NewOrderPage.of(basket, store);
        ClientDataDto form = NewOrderPage.initialForm(basket, store);

        // then
        assertThat(page.deliveryState()).isEqualTo(DeliveryState.UNKNOWN);
        assertThat(page.hasDeliveryOption()).isFalse();
        assertThat(page.deliveryPrice()).isZero();
        assertThat(page.deliveryType()).isNull();
        assertThat(form.getShipmentType()).isEqualTo(ShipmentType.Courier);
    }

    @Test
    void unknownDeliveryOptionBlocksTheOrder() {
        // given
        Basket unknown = basket(BasketType.Basket, "removed-option");
        Basket known = basket(BasketType.Basket, "courier");

        // when
        NewOrderPage unknownPage = NewOrderPage.of(unknown, store);
        NewOrderPage knownPage = NewOrderPage.of(known, store);

        // then
        assertThat(unknownPage.deliveryUnknown()).isTrue();
        assertThat(knownPage.deliveryUnknown()).isFalse();
    }

    @Test
    void deliveryOptionWithoutTypeCountsAsCourier() {
        // given
        store.getCheckoutConfiguration().getDeliveryOptions().add(option("untyped", "Kurier", 15.0, null));
        Basket basket = basket(BasketType.Basket, "untyped");

        // when
        NewOrderPage page = NewOrderPage.of(basket, store);
        ClientDataDto form = NewOrderPage.initialForm(basket, store);

        // then
        assertThat(page.deliveryState()).isEqualTo(DeliveryState.CHOSEN);
        assertThat(page.deliveryType()).isEqualTo(ShipmentType.Courier);
        assertThat(form.getShipmentType()).isEqualTo(ShipmentType.Courier);
    }

    @Test
    void noDeliveryOptionMeansCourierAndNone() {
        // given
        Basket basket = basket(BasketType.Basket, null);

        // when
        NewOrderPage page = NewOrderPage.of(basket, store);
        ClientDataDto form = NewOrderPage.initialForm(basket, store);

        // then
        assertThat(page.deliveryState()).isEqualTo(DeliveryState.NONE);
        assertThat(form.getShipmentType()).isEqualTo(ShipmentType.Courier);
        assertThat(form.getPaymentSource()).isEqualTo(PaymentSource.BankTransfer);
        assertThat(form.getOrderReference()).isEqualTo(ID);
    }

    @Test
    void pickupPointOptionPresetsPickupPoint() {
        // given
        Basket basket = basket(BasketType.Basket, "locker");

        // when
        NewOrderPage page = NewOrderPage.of(basket, store);
        ClientDataDto form = NewOrderPage.initialForm(basket, store);

        // then
        assertThat(page.deliveryState()).isEqualTo(DeliveryState.CHOSEN);
        assertThat(page.hasDeliveryOption()).isTrue();
        assertThat(page.deliveryName()).isEqualTo("Paczkomat");
        assertThat(page.deliveryType()).isEqualTo(ShipmentType.PickupPoint);
        assertThat(form.getShipmentType()).isEqualTo(ShipmentType.PickupPoint);
    }

    @Test
    void offerWithoutBillingTakesTheContact() {
        // given
        Basket basket = basket(BasketType.Offer, null);
        ContactDetails contact = new ContactDetails();
        contact.setName("Jan");
        contact.setSurname("Kowalski");
        contact.setCompanyName("Acme");
        contact.setEmail("jan@acme.pl");
        contact.setPhone("123456789");
        basket.setContactDetails(contact);

        // when
        NewOrderPage page = NewOrderPage.of(basket, store);
        BillingDetails billing = NewOrderPage.initialForm(basket, store).getBillingDetails();

        // then
        assertThat(page.billingFromContact()).isTrue();
        assertThat(billing.getName()).isEqualTo("Jan");
        assertThat(billing.getSurname()).isEqualTo("Kowalski");
        assertThat(billing.getCompanyName()).isEqualTo("Acme");
        assertThat(billing.getEmail()).isEqualTo("jan@acme.pl");
        assertThat(billing.getPhone()).isEqualTo("123456789");
        assertThat(billing.getCountry()).isEqualTo("PL");
    }

    @Test
    void emptyContactIsNotTakenAsBilling() {
        // given
        Basket basket = basket(BasketType.Offer, null);
        basket.setContactDetails(new ContactDetails());

        // when
        NewOrderPage page = NewOrderPage.of(basket, store);
        BillingDetails billing = NewOrderPage.initialForm(basket, store).getBillingDetails();

        // then
        assertThat(page.billingFromContact()).isFalse();
        assertThat(billing.getName()).isNull();
        assertThat(billing.getCountry()).isEqualTo("PL");
    }

    @Test
    void basketBillingWinsOverContact() {
        // given
        Basket basket = basket(BasketType.Offer, null);
        BillingDetails billing = new BillingDetails();
        billing.setName("Anna");
        basket.setBillingDetails(billing);
        ContactDetails contact = new ContactDetails();
        contact.setName("Jan");
        basket.setContactDetails(contact);

        // when
        NewOrderPage page = NewOrderPage.of(basket, store);
        BillingDetails result = NewOrderPage.initialForm(basket, store).getBillingDetails();

        // then
        assertThat(page.billingFromContact()).isFalse();
        assertThat(result.getName()).isEqualTo("Anna");
    }

    @Test
    void differentShippingAddressPresetsOther() {
        // given
        Basket basket = basket(BasketType.Basket, null);
        BillingDetails billing = billing("Main 1");
        basket.setBillingDetails(billing);
        basket.setShippingDetails(shipping("Other 2"));

        // when / then
        assertThat(NewOrderPage.initialShippingAddress(basket)).isEqualTo(NewOrderForm.OTHER);
    }

    @Test
    void sameOrEmptyShippingAddressPresetsSame() {
        // given
        Basket same = basket(BasketType.Basket, null);
        same.setBillingDetails(billing("Main 1"));
        same.setShippingDetails(shipping("  main 1 "));
        Basket empty = basket(BasketType.Basket, null);
        empty.setBillingDetails(billing("Main 1"));
        empty.setShippingDetails(new ShippingDetails());
        Basket none = basket(BasketType.Basket, null);

        // when / then
        assertThat(NewOrderPage.initialShippingAddress(same)).isEqualTo(NewOrderForm.SAME);
        assertThat(NewOrderPage.initialShippingAddress(empty)).isEqualTo(NewOrderForm.SAME);
        assertThat(NewOrderPage.initialShippingAddress(none)).isEqualTo(NewOrderForm.SAME);
    }

    @Test
    void countryOnlyShippingAddressPresetsSame() {
        // given
        Basket basket = basket(BasketType.Basket, null);
        basket.setBillingDetails(billing("Main 1"));
        basket.setShippingDetails(ShippingDetails._default());

        // when / then
        assertThat(NewOrderPage.initialShippingAddress(basket)).isEqualTo(NewOrderForm.SAME);
    }

    @Test
    void totalIncludesDelivery() {
        // given
        Basket basket = basket(BasketType.Basket, "courier");

        // when
        NewOrderPage page = NewOrderPage.of(basket, store);

        // then
        assertThat(page.deliveryPrice()).isEqualTo(20.0);
        assertThat(page.total()).isEqualTo(120.0);
    }

    private static Basket basket(BasketType type, String deliveryOptionId) {
        BasketItem item = new BasketItem();
        item.setName("Laptop");
        item.setMfn("L-1");
        item.setQty(1);
        item.setUnitPrice(100.0);
        Basket basket = new Basket.Builder("store-1")
                .withBasketId(ID)
                .withType(type)
                .withDeliveryOptionId(deliveryOptionId)
                .build();
        // the builder drops items it considers incomplete, so set them directly
        basket.setBasketItems(new LinkedList<>(List.of(item)));
        return basket;
    }

    private static DeliveryOption option(String id, String name, double price, ShipmentType type) {
        DeliveryOption option = new DeliveryOption();
        option.setId(id);
        option.setName(name);
        option.setPrice(price);
        option.setType(type);
        return option;
    }

    private static BillingDetails billing(String street) {
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan");
        billing.setStreetAndNumber(street);
        billing.setPostalCode("00-001");
        billing.setCity("Warszawa");
        billing.setCountry("PL");
        return billing;
    }

    private static ShippingDetails shipping(String street) {
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("jan");
        shipping.setStreetAndNumber(street);
        shipping.setPostalCode("00-001");
        shipping.setCity("WARSZAWA");
        shipping.setCountry("pl");
        return shipping;
    }
}
