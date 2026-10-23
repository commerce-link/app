package pl.commercelink.web.deliveries.details;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.dtos.DeliveryTermsForm;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.deliveries.details.DeliveryFixtures.*;

class TermsDialogTest {

    @Test
    void theDialogStartsFromTheDeliveryAndPostsToTheViewersAddress() {
        // when
        TermsDialog store = TermsDialog.of(warehouse(), DeliveryLinks.of(false, STORE_ID, DELIVERY_ID));
        TermsDialog superAdmin = TermsDialog.of(dropship(), DeliveryLinks.of(true, STORE_ID, "d-2"));

        // then
        assertThat(store.action()).isEqualTo("/dashboard/deliveries/details");
        assertThat(store.shortId()).isEqualTo("2f9eb794");
        assertThat(store.dateRequired()).isTrue();
        assertThat(store.form().getVat()).isEqualTo("23");
        assertThat(store.errors()).isEmpty();
        assertThat(superAdmin.action()).isEqualTo("/dashboard/store/store-1/deliveries/details");
        assertThat(superAdmin.dateRequired()).isFalse();
    }

    @Test
    void errorsKeepWhatTheOperatorTyped() {
        // given
        DeliveryTermsForm typed = new DeliveryTermsForm();
        typed.setVat("abc");

        // when
        TermsDialog dialog = TermsDialog.of(warehouse(), DeliveryLinks.of(false, STORE_ID, DELIVERY_ID))
                .withErrors(typed, Map.of("vat", "deliveries.details.terms.error.vat"));

        // then
        assertThat(dialog.form().getVat()).isEqualTo("abc");
        assertThat(dialog.errors()).containsEntry("vat", "deliveries.details.terms.error.vat");
        assertThat(dialog.refused(typed, "Zablokowane").refusal()).isEqualTo("Zablokowane");
        assertThat(dialog.saved(typed, "Zapisano").savedMessage()).isEqualTo("Zapisano");
        assertThat(dialog.saved(typed, "Zapisano").errors()).isEmpty();
    }
}
