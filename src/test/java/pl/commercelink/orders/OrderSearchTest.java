package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderSearchTest {

    private static Order order() {
        Order order = new Order("store-1");
        order.setOrderId("ab58c563-full");
        order.setExternalOrderId("5749922740");
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Anna");
        shipping.setSurname("Nowak");
        shipping.setCompanyName("Nowak Sp. z o.o.");
        shipping.setEmail("anna.nowak74@test.com");
        order.setShippingDetails(shipping);
        BillingDetails billing = new BillingDetails();
        billing.setEmail("faktury@nowak.pl");
        order.setBillingDetails(billing);
        return order;
    }

    @Test
    void matchesIdExternalIdEmailsAndNamesIgnoringCase() {
        assertThat(OrderSearch.matches(order(), "AB58")).isTrue();
        assertThat(OrderSearch.matches(order(), "5749922740")).isTrue();
        assertThat(OrderSearch.matches(order(), "faktury@")).isTrue();
        assertThat(OrderSearch.matches(order(), "nowak74")).isTrue();
        assertThat(OrderSearch.matches(order(), "sp. z o.o")).isTrue();
        assertThat(OrderSearch.matches(order(), "  NOWAK ")).isTrue();
        assertThat(OrderSearch.matches(order(), "kowalski")).isFalse();
    }

    @Test
    void blankQueryMatchesEverythingAndNullDetailsDoNotBreak() {
        Order bare = new Order("store-1");
        bare.setOrderId("x");
        assertThat(OrderSearch.matches(bare, "")).isTrue();
        assertThat(OrderSearch.matches(bare, null)).isTrue();
        assertThat(OrderSearch.matches(bare, "anything")).isFalse();
    }

    @Test
    void regexCharactersAreLiteral() {
        Order order = order();
        order.getBillingDetails().setEmail("a(b)@test.com");
        assertThat(OrderSearch.matches(order, "a(b)")).isTrue();
        assertThat(OrderSearch.matches(order, ".*")).isFalse();
    }
}
