package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerViewTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    private static Order order() {
        Order order = new Order("store-1");
        order.setOrderId("o-1");
        order.setStatus(OrderStatus.Assembly);
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan");
        billing.setSurname("Kowalski");
        billing.setStreetAndNumber("ul. Długa 14/3");
        billing.setPostalCode("31-147");
        billing.setCity("Kraków");
        billing.setCountry("PL");
        billing.setEmail("jan@example.pl");
        billing.setPhone("+48600123456");
        order.setBillingDetails(billing);
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Jan");
        shipping.setSurname("Kowalski");
        shipping.setStreetAndNumber("ul. Długa 14/3");
        shipping.setPostalCode("31-147");
        shipping.setCity("Kraków");
        shipping.setCountry("PL");
        order.setShippingDetails(shipping);
        order.addShipment(new Shipment(ShipmentType.Courier));
        return order;
    }

    @Test
    void bothAddressesAreEditableBeforeAnInvoiceAndALabel() {
        // when
        CustomerView view = CustomerView.of(order(), false, PL);

        // then
        assertThat(view.billing().name()).isEqualTo("Jan Kowalski");
        assertThat(view.billing().cityLine()).isEqualTo("31-147 Kraków");
        assertThat(view.billing().country()).isEqualTo("Polska");
        assertThat(view.shippingSameAsBilling()).isTrue();
        assertThat(view.shipmentTypeKey()).isEqualTo("ShipmentType.Courier");
        assertThat(view.billingEditHref()).isEqualTo("/dashboard/orders/o-1/address?type=billing");
        assertThat(view.shippingEditHref()).isEqualTo("/dashboard/orders/o-1/address?type=shipping");
    }

    @Test
    void anInvoiceLocksTheBillingDataAndALabelLocksTheShippingAddressWithAReason() {
        // given
        Order order = order();
        order.addDocument(new Document("fv", "FV/1", null, DocumentType.InvoiceVat));
        order.getShipments().get(0).setTrackingNo("T-1");

        // when
        CustomerView view = CustomerView.of(order, false, PL);

        // then
        assertThat(view.billingEditHref()).isNull();
        assertThat(view.billingLockedKey()).isEqualTo("order.customer.billing.locked");
        assertThat(view.shippingEditHref()).isNull();
        assertThat(view.shippingLockedKey()).isEqualTo("order.customer.shipping.locked.label");
    }

    @Test
    void aReadOnlyViewerGetsNoEditLinksAndNoReasons() {
        // when
        CustomerView view = CustomerView.of(order(), true, PL);

        // then
        assertThat(view.billingEditHref()).isNull();
        assertThat(view.shippingEditHref()).isNull();
        assertThat(view.billingLockedKey()).isNull();
        assertThat(view.shippingLockedKey()).isNull();
    }

    @Test
    void lockedKeyIsTheSingleRuleTheCardAndTheAddressPageBothRead() {
        // given: the address page and its save (OrdersController) call this same static method, so the card and
        // the page cannot drift apart the way two hand-copied conditions could
        Order open = order();

        // given: delivered but never labelled — only a label fixes the shipping address
        Order inTransit = order();
        inTransit.setStatus(OrderStatus.Delivered);

        // then
        assertThat(CustomerView.lockedKey(open, true)).isNull();
        assertThat(CustomerView.lockedKey(open, false)).isNull();
        assertThat(CustomerView.lockedKey(inTransit, false)).isNull();
    }

    @Test
    void theRecipientsOwnPhoneAndEmailStayVisibleWhenTheAddressIsTheSameAsBilling() {
        // given: the parcel goes to the billing address, but to a recipient with their own contact
        Order order = order();
        order.getShippingDetails().setPhone("+48 600 700 800");
        order.getShippingDetails().setEmail("odbiorca@example.com");

        // when
        CustomerView view = CustomerView.of(order, false, PL);

        // then
        assertThat(view.shippingSameAsBilling()).isTrue();
        assertThat(view.shippingPhone()).isEqualTo("+48 600 700 800");
        assertThat(view.shippingEmail()).isEqualTo("odbiorca@example.com");
    }

    @Test
    void aRecipientContactEqualToBillingOrMissingIsNotRepeated() {
        // given: the same e-mail (other case) as billing, no phone of its own
        Order order = order();
        order.getShippingDetails().setEmail("JAN@example.pl");

        // when
        CustomerView view = CustomerView.of(order, false, PL);

        // then
        assertThat(view.shippingSameAsBilling()).isTrue();
        assertThat(view.shippingEmail()).isNull();
        assertThat(view.shippingPhone()).isNull();
    }
}
