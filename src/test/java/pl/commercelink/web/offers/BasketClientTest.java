package pl.commercelink.web.offers;

import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.ContactDetails;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;

import static org.assertj.core.api.Assertions.assertThat;

class BasketClientTest {

    private static BillingDetails billing(String name, String surname, String company) {
        BillingDetails b = new BillingDetails();
        b.setName(name);
        b.setSurname(surname);
        b.setCompanyName(company);
        return b;
    }

    @Test
    void guestBasketHasNoClient() {
        // given
        Basket basket = new Basket();

        // then
        assertThat(BasketClient.shortName(basket)).isNull();
        assertThat(BasketClient.fullName(basket)).isNull();
    }

    @Test
    void billingDetailsNameTheClientWhenThereIsNoContact() {
        // given
        Basket basket = new Basket();
        basket.setBillingDetails(billing("Marta", "Zielińska", "Biuro Rachunkowe Zielińska"));

        // then
        assertThat(BasketClient.shortName(basket)).isEqualTo("Biuro Rachunkowe Zielińska");
        assertThat(BasketClient.fullName(basket)).isEqualTo("Marta Zielińska · Biuro Rachunkowe Zielińska");
    }

    @Test
    void shippingDetailsAreTheLastResort() {
        // given
        Basket basket = new Basket();
        basket.setBillingDetails(billing(" ", null, ""));
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Jan");
        shipping.setSurname("Nowak");
        basket.setShippingDetails(shipping);

        // then
        assertThat(BasketClient.shortName(basket)).isEqualTo("Jan Nowak");
        assertThat(BasketClient.fullName(basket)).isEqualTo("Jan Nowak");
    }

    @Test
    void contactComesFirst() {
        // given
        Basket basket = new Basket();
        ContactDetails contact = new ContactDetails();
        contact.setName("Anna");
        contact.setSurname("Lis");
        basket.setContactDetails(contact);
        basket.setBillingDetails(billing("Marta", "Zielińska", null));

        // then
        assertThat(BasketClient.shortName(basket)).isEqualTo("Anna Lis");
    }
}
