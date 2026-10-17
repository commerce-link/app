package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.Basket;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.web.dtos.ClientDataDto;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NewOrderFormTest {

    private static BillingDetails person(String street) {
        BillingDetails b = new BillingDetails();
        b.setName("Jan");
        b.setSurname("Kowalski");
        b.setStreetAndNumber(street);
        b.setPostalCode(street.isEmpty() ? "" : "00-001");
        b.setCity(street.isEmpty() ? "" : "Warszawa");
        b.setCountry(street.isEmpty() ? "" : "pl");
        b.setEmail("jan@example.com");
        b.setPhone("600700800");
        return b;
    }

    private static ClientDataDto dto(ShipmentType type, BillingDetails billing, ShippingDetails shipping) {
        ClientDataDto d = new ClientDataDto();
        d.setOrderReference("REF");
        d.setShipmentType(type);
        d.setPaymentSource(PaymentSource.values()[0]);
        d.setBillingDetails(billing);
        d.setShippingDetails(shipping);
        return d;
    }

    private static ShippingDetails shippingWith(String street, String country) {
        ShippingDetails s = new ShippingDetails();
        s.setName("Anna");
        s.setStreetAndNumber(street);
        s.setPostalCode("10115");
        s.setCity("Berlin");
        s.setCountry(country);
        s.setEmail("anna@example.com");
        s.setPhone("600700801");
        return s;
    }

    @Test
    void courierRequiresBillingStreetPostalCityCountry() {
        // given
        NewOrderForm form = NewOrderForm.of(dto(ShipmentType.Courier, person(""), null), "same");

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors)
                .containsEntry("billingDetails.streetAndNumber", "billing.street.required")
                .containsEntry("billingDetails.postalCode", "billing.postalCode.required")
                .containsEntry("billingDetails.city", "billing.city.required")
                .containsEntry("billingDetails.country", "billing.country.required");
    }

    @Test
    void personalCollectionAndPickupPointDoNotRequireBillingAddress() {
        // when / then
        for (ShipmentType type : new ShipmentType[]{ShipmentType.PersonalCollection, ShipmentType.PickupPoint}) {
            assertThat(NewOrderForm.of(dto(type, person(""), null), "same").validate()).isEmpty();
        }
    }

    @Test
    void wrongCountryFormatIsAnErrorEvenWithoutCourier() {
        // given
        BillingDetails billing = person("");
        billing.setCountry("Polska");

        // when
        Map<String, String> errors = NewOrderForm.of(dto(ShipmentType.PersonalCollection, billing, null), "same").validate();

        // then
        assertThat(errors).containsEntry("billingDetails.country", "order.address.country.invalid");
    }

    @Test
    void billingNeedsNameOrCompany() {
        // given
        BillingDetails nobody = person("");
        nobody.setName("");
        nobody.setSurname("");
        BillingDetails company = person("");
        company.setName("");
        company.setCompanyName("ACME");

        // when / then
        assertThat(NewOrderForm.of(dto(ShipmentType.PersonalCollection, nobody, null), "same").validate())
                .containsEntry("billingDetails.name", "order.address.name.or.company.required");
        assertThat(NewOrderForm.of(dto(ShipmentType.PersonalCollection, company, null), "same").validate()).isEmpty();
    }

    @Test
    void missingShipmentTypeAndPaymentSourceAreErrors() {
        // given
        ClientDataDto d = dto(null, person("Prosta 1"), null);
        d.setPaymentSource(null);

        // when
        Map<String, String> errors = NewOrderForm.of(d, "same").validate();

        // then
        assertThat(errors)
                .containsEntry("shipmentType", "order.new.shipment.required")
                .containsEntry("paymentSource", "order.new.payment.required");
    }

    @Test
    void pickupPointRequiresNothingButChecksFormats() {
        // given
        ShippingDetails empty = new ShippingDetails();
        ShippingDetails badEmail = new ShippingDetails();
        badEmail.setEmail("x@");

        // when / then
        assertThat(NewOrderForm.of(dto(ShipmentType.PickupPoint, person("Prosta 1"), empty), "other").validate()).isEmpty();
        assertThat(NewOrderForm.of(dto(ShipmentType.PickupPoint, person("Prosta 1"), badEmail), "other").validate())
                .containsExactly(Map.entry("shippingDetails.email", "billing.email.invalid"));
    }

    @Test
    void pickupPointWithEmptyShippingGetsTheBillingCopy() {
        // given
        ShippingDetails onlyCountry = new ShippingDetails();
        onlyCountry.setCountry("PL");
        ShippingDetails filled = new ShippingDetails();
        filled.setCountry("PL");
        filled.setCity("Gdansk");

        // when
        ClientDataDto copied = NewOrderForm.of(dto(ShipmentType.PickupPoint, person("Prosta 1"), onlyCountry), "same").toImport(null);
        ClientDataDto posted = NewOrderForm.of(dto(ShipmentType.PickupPoint, person("Prosta 1"), filled), "same").toImport(null);

        // then
        assertThat(copied.getShippingDetails().getCity()).isEqualTo("Warszawa");
        assertThat(copied.getShippingDetails().getStreetAndNumber()).isEqualTo("Prosta 1");
        assertThat(posted.getShippingDetails().getCity()).isEqualTo("Gdansk");
        assertThat(posted.getShippingDetails().getStreetAndNumber()).isNull();
    }

    @Test
    void courierSameAddressIgnoresShippingFields() {
        // given
        ShippingDetails junk = new ShippingDetails();
        junk.setEmail("not-an-email");
        junk.setCountry("Germany");
        NewOrderForm form = NewOrderForm.of(dto(ShipmentType.Courier, person("Prosta 1"), junk), "same");

        // when
        Map<String, String> errors = form.validate();
        ClientDataDto result = form.toImport(null);

        // then
        assertThat(errors).isEmpty();
        assertThat(result.getBillingDetails().getCountry()).isEqualTo("PL");
        ShippingDetails shipping = result.getShippingDetails();
        assertThat(shipping.getName()).isEqualTo("Jan");
        assertThat(shipping.getSurname()).isEqualTo("Kowalski");
        assertThat(shipping.getStreetAndNumber()).isEqualTo("Prosta 1");
        assertThat(shipping.getPostalCode()).isEqualTo("00-001");
        assertThat(shipping.getCity()).isEqualTo("Warszawa");
        assertThat(shipping.getCountry()).isEqualTo("PL");
        assertThat(shipping.getEmail()).isEqualTo("jan@example.com");
        assertThat(shipping.getPhone()).isEqualTo("600700800");
    }

    @Test
    void courierOtherAddressUsesPostedShipping() {
        // given
        NewOrderForm form = NewOrderForm.of(
                dto(ShipmentType.Courier, person("Prosta 1"), shippingWith("Strasse 5", "de")), "other");

        // when
        Map<String, String> errors = form.validate();
        ClientDataDto result = form.toImport(null);

        // then
        assertThat(errors).isEmpty();
        assertThat(form.shipsToOtherAddress()).isTrue();
        assertThat(result.getShippingDetails().getStreetAndNumber()).isEqualTo("Strasse 5");
        assertThat(result.getShippingDetails().getCountry()).isEqualTo("DE");
    }

    @Test
    void personalCollectionKeepsTheBasketShippingDetails() {
        // given
        ShippingDetails inBasket = shippingWith("Koszykowa 1", "PL");
        Basket withAddress = new Basket();
        withAddress.setShippingDetails(inBasket);
        NewOrderForm form = NewOrderForm.of(dto(ShipmentType.PersonalCollection, person(""), null), "same");

        // when
        ClientDataDto kept = form.toImport(withAddress);
        ClientDataDto fallback = form.toImport(new Basket());

        // then
        assertThat(kept.getShippingDetails()).isSameAs(inBasket);
        assertThat(fallback.getShippingDetails().getCountry()).isEqualTo("PL");
    }

    @Test
    void missingBillingAndShippingObjectsDoNotThrow() {
        // given
        NewOrderForm form = NewOrderForm.of(dto(ShipmentType.Courier, null, null), "other");

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors).isNotEmpty();
    }

    @Test
    void unknownShippingAddressValueMeansSame() {
        // when
        NewOrderForm form = NewOrderForm.of(dto(ShipmentType.Courier, person("Prosta 1"), null), "xyz");

        // then
        assertThat(form.shipsToOtherAddress()).isFalse();
    }
}
