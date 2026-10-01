package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.Delivery;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTermsFormTest {

    private static DeliveryTermsForm valid() {
        DeliveryTermsForm form = new DeliveryTermsForm();
        form.setDeliveryId("d-1");
        form.setEstimatedDeliveryAt("2026-10-08");
        form.setPaymentTerms("14");
        form.setShippingCost("19,90");
        form.setPaymentCost("");
        form.setVat("23");
        form.setComment("Rampa B");
        return form;
    }

    @Test
    void theFormShowsTheDeliveryWithVatAsAPercentage() {
        // given
        Delivery delivery = new Delivery("store-1", "MH-1", "Manual-Hurt", LocalDate.of(2026, 10, 8), 19.9, 0, 14, 1.23);
        delivery.setComment("Rampa B");

        // when
        DeliveryTermsForm form = DeliveryTermsForm.of(delivery);

        // then
        assertThat(form.getDeliveryId()).isEqualTo(delivery.getDeliveryId());
        assertThat(form.getEstimatedDeliveryAt()).isEqualTo("2026-10-08");
        assertThat(form.getPaymentTerms()).isEqualTo("14");
        assertThat(form.getShippingCost()).isEqualTo("19.90");
        assertThat(form.getPaymentCost()).isEqualTo("0.00");
        assertThat(form.getVat()).isEqualTo("23");
        assertThat(form.getComment()).isEqualTo("Rampa B");
        assertThat(form.getSource()).isEqualTo(DeliveryTermsForm.TERMS);
    }

    @Test
    void vatPercentReadsTheMultiplierWithoutFloatingPointNoise() {
        // when / then
        assertThat(DeliveryTermsForm.vatPercent(1.23)).isEqualTo("23");
        assertThat(DeliveryTermsForm.vatPercent(1.0)).isEqualTo("0");
        assertThat(DeliveryTermsForm.vatPercent(1.08)).isEqualTo("8");
        assertThat(DeliveryTermsForm.vatPercent(1.055)).isEqualTo("5.5");
    }

    @Test
    void polishDecimalsAndBlankAmountsParse() {
        // given
        DeliveryTermsForm form = valid();
        form.setShippingCost("1 499,99");

        // when
        Map<String, String> errors = form.validate(true);
        Delivery delivery = form.toDelivery("store-1");

        // then
        assertThat(errors).isEmpty();
        assertThat(delivery.getStoreId()).isEqualTo("store-1");
        assertThat(delivery.getDeliveryId()).isEqualTo("d-1");
        assertThat(delivery.getEstimatedDeliveryAt()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(delivery.getPaymentTerms()).isEqualTo(14);
        assertThat(delivery.getShippingCost()).isEqualTo(1499.99);
        assertThat(delivery.getPaymentCost()).isEqualTo(0.0);
        assertThat(delivery.getTax()).isEqualTo(1.23);
        assertThat(delivery.getComment()).isEqualTo("Rampa B");
    }

    @Test
    void zeroVatMeansReverseCharge() {
        // given
        DeliveryTermsForm form = valid();
        form.setVat("0");

        // when
        Delivery delivery = form.toDelivery("store-1");

        // then
        assertThat(delivery.getTax()).isEqualTo(1.0);
    }

    @Test
    void invalidValuesAreReportedPerField() {
        // given
        DeliveryTermsForm form = new DeliveryTermsForm();
        form.setEstimatedDeliveryAt("08.10.2026");
        form.setPaymentTerms("2,5");
        form.setShippingCost("abc");
        form.setPaymentCost("-5");
        form.setVat("101");

        // when
        Map<String, String> errors = form.validate(true);

        // then
        assertThat(errors).containsExactly(
                Map.entry("estimatedDeliveryAt", "deliveries.details.terms.error.date"),
                Map.entry("paymentTerms", "deliveries.details.terms.error.paymentTerms"),
                Map.entry("shippingCost", "deliveries.details.terms.error.shippingCost"),
                Map.entry("paymentCost", "deliveries.details.terms.error.paymentCost"),
                Map.entry("vat", "deliveries.details.terms.error.vat"));
    }

    @Test
    void aDropshipDeliveryMayHaveNoDateButNotAMissingVat() {
        // given
        DeliveryTermsForm form = valid();
        form.setEstimatedDeliveryAt("");
        form.setVat(" ");
        form.setPaymentTerms("");

        // when
        Map<String, String> dropship = form.validate(false);
        Map<String, String> warehouse = form.validate(true);

        // then
        assertThat(dropship).containsOnlyKeys("vat", "paymentTerms");
        assertThat(warehouse).containsOnlyKeys("estimatedDeliveryAt", "vat", "paymentTerms");
    }

    @Test
    void aBlankCommentIsStoredAsNone() {
        // given
        DeliveryTermsForm form = valid();
        form.setComment("   ");
        form.setEstimatedDeliveryAt("");

        // when
        Delivery delivery = form.toDelivery("store-1");

        // then
        assertThat(delivery.getComment()).isNull();
        assertThat(delivery.getEstimatedDeliveryAt()).isNull();
    }

    @Test
    void anUnsetVatShowsAsAnEmptyField() {
        // given
        Delivery delivery = new Delivery("store-1", "MH-2", "Manual-Hurt", LocalDate.of(2026, 10, 8), 0, 0, 14, 0.0);

        // when
        DeliveryTermsForm form = DeliveryTermsForm.of(delivery);

        // then
        assertThat(form.getVat()).isEmpty();
        assertThat(DeliveryTermsForm.vatPercent(1.0)).isEqualTo("0");
    }
}
