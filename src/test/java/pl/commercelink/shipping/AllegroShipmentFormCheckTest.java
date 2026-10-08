package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.shipping.api.DeliveryPoint;
import pl.commercelink.shipping.api.DeliveryType;
import pl.commercelink.shipping.api.PackageOption;
import pl.commercelink.shipping.api.ShipmentProposal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AllegroShipmentFormCheckTest {

    static ShipmentProposal proposal(BigDecimal maxCod, BigDecimal maxInsurance) {
        return ShipmentProposal.available("Allegro One Box, One Kurier", "ALLEGRO", new DeliveryPoint("ALBOX-WAW-0231"),
                DeliveryType.LOCKER, List.of(new PackageOption("PACKAGE", new BigDecimal("64"), new BigDecimal("38"),
                        new BigDecimal("41"), new BigDecimal("25"))), maxCod, maxInsurance);
    }

    static ShippingForm form(int length, int width, int height, int weight, int insurance, Double cod) {
        ShippingForm form = new ShippingForm("order-1", "orders");
        ParcelForm parcel = new ParcelForm(length, width, height, weight, insurance, "Akcesoria", "package");
        form.setParcels(new ArrayList<>(List.of(parcel)));
        if (cod != null) {
            form.setCashOnDelivery(true);
            form.setCashOnDeliveryAmount(cod);
        }
        return form;
    }

    @Test
    void parcelWithinTheLimitsPasses() {
        assertThat(AllegroShipmentFormCheck.check(form(30, 20, 15, 2, 920, 919.99),
                proposal(new BigDecimal("5000"), new BigDecimal("5000")))).isEmpty();
    }

    @Test
    void incompleteParcelIsReported() {
        assertThat(AllegroShipmentFormCheck.check(form(30, 0, 15, 2, 100, null), proposal(null, null)))
                .extracting(AllegroShipmentFormCheck.Problem::field, AllegroShipmentFormCheck.Problem::key)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("parcel", "shipping.allegro.error.parcel"));
    }

    @Test
    void parcelLargerThanTheMethodAllowsIsReportedWithTheLimit() {
        List<AllegroShipmentFormCheck.Problem> problems =
                AllegroShipmentFormCheck.check(form(65, 20, 15, 2, 100, null), proposal(null, null));

        assertThat(problems).extracting(AllegroShipmentFormCheck.Problem::key)
                .containsExactly("shipping.allegro.error.dimensions");
        assertThat(problems.get(0).args()).containsExactly("64", "38", "41");
    }

    @Test
    void heavierParcelIsReported() {
        assertThat(AllegroShipmentFormCheck.check(form(30, 20, 15, 26, 100, null), proposal(null, null)))
                .extracting(AllegroShipmentFormCheck.Problem::key).containsExactly("shipping.allegro.error.weight");
    }

    @Test
    void cashOnDeliveryAboveTheLimitIsReported() {
        assertThat(AllegroShipmentFormCheck.check(form(30, 20, 15, 2, 6000, 5500.0),
                proposal(new BigDecimal("5000"), new BigDecimal("10000"))))
                .extracting(AllegroShipmentFormCheck.Problem::field, AllegroShipmentFormCheck.Problem::key)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("cashOnDeliveryAmount", "shipping.allegro.error.cod"));
    }

    @Test
    void insuranceBelowTheCashOnDeliveryIsReported() {
        assertThat(AllegroShipmentFormCheck.check(form(30, 20, 15, 2, 900, 919.99), proposal(null, null)))
                .extracting(AllegroShipmentFormCheck.Problem::field, AllegroShipmentFormCheck.Problem::key)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("insurance", "shipping.allegro.error.insurance.min"));
    }

    @Test
    void insuranceAboveTheLimitIsReported() {
        assertThat(AllegroShipmentFormCheck.check(form(30, 20, 15, 2, 6000, null),
                proposal(null, new BigDecimal("5000"))))
                .extracting(AllegroShipmentFormCheck.Problem::key).containsExactly("shipping.allegro.error.insurance.max");
    }

    @Test
    void moreThanOneCompleteParcelIsReported() {
        ShippingForm form = form(30, 20, 15, 2, 100, null);
        form.getParcels().add(new ParcelForm(30, 20, 15, 2, 100, "Drugi karton", "package"));

        assertThat(AllegroShipmentFormCheck.check(form, proposal(null, null)))
                .extracting(AllegroShipmentFormCheck.Problem::key).containsExactly("shipping.allegro.error.oneParcel");
    }

    @Test
    void boxThatFitsOnlyTurnedAroundIsAccepted() {
        // given: the template's depth and width reach the form swapped, so 30 x 60 is the 60 x 30 box
        ShippingForm form = form(30, 60, 15, 2, 100, null);

        // when / then
        assertThat(AllegroShipmentFormCheck.check(form, proposal(null, null))).isEmpty();
    }

    @Test
    void boxWhoseLongestSideExceedsTheLongestLimitIsReportedWhicheverWayItLies() {
        // when / then
        assertThat(AllegroShipmentFormCheck.check(form(20, 65, 15, 2, 100, null), proposal(null, null)))
                .extracting(AllegroShipmentFormCheck.Problem::key).containsExactly("shipping.allegro.error.dimensions");
    }

    @Test
    void boxWithTwoLongSidesIsReportedAgainstTheSecondLimit() {
        // given: the limits sorted are 64, 41, 38, so the second side 42 does not fit
        assertThat(AllegroShipmentFormCheck.check(form(60, 42, 15, 2, 100, null), proposal(null, null)))
                .extracting(AllegroShipmentFormCheck.Problem::key).containsExactly("shipping.allegro.error.dimensions");
    }

    @Test
    void cashOnDeliveryWithoutAStoreBankAccountIsRefused() {
        // when
        List<AllegroShipmentFormCheck.Problem> problems =
                AllegroShipmentFormCheck.check(form(30, 20, 15, 2, 920, 919.99), proposal(null, null), false);

        // then
        assertThat(problems).extracting(AllegroShipmentFormCheck.Problem::field, AllegroShipmentFormCheck.Problem::key)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("cashOnDeliveryAmount", "shipping.allegro.error.cod.noAccount"));
    }

    @Test
    void prepaidOrderNeedsNoBankAccount() {
        // when / then
        assertThat(AllegroShipmentFormCheck.check(form(30, 20, 15, 2, 100, null), proposal(null, null), false)).isEmpty();
    }
}
