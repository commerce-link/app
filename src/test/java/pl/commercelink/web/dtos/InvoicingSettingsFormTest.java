package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.Store;

import static org.assertj.core.api.Assertions.assertThat;

class InvoicingSettingsFormTest {

    private static InvoicingSettingsForm form(String paymentTerms) {
        InvoicingSettingsForm form = new InvoicingSettingsForm();
        form.setPaymentTerms(paymentTerms);
        return form;
    }

    /** An empty or negative term used to fail the binding of an int field with an error page. */
    @Test
    void thePaymentTermIsRequiredAndAWholeNumberOfDaysUpToAYear() {
        // when / then
        assertThat(form("").validate()).containsEntry("paymentTerms", "store.invoicing.paymentTerms.required");
        assertThat(form("-3").validate()).containsEntry("paymentTerms", "store.invoicing.paymentTerms.invalid");
        assertThat(form("366").validate()).containsEntry("paymentTerms", "store.invoicing.paymentTerms.invalid");
        assertThat(form("14").validate()).isEmpty();
        assertThat(form("0").validate()).isEmpty();
    }

    @Test
    void thePrefixIsRequiredOnlyWhileConsolidationIsOn() {
        // given
        InvoicingSettingsForm form = form("7");

        // when
        form.setPositionsConsolidation(true);

        // then
        assertThat(form.validate()).containsEntry("positionsConsolidationPrefix", "store.invoicing.consolidationPrefix.required");
        form.setPositionsConsolidation(false);
        assertThat(form.validate()).isEmpty();
    }

    @Test
    void appliesEveryValueToTheStore() {
        // given
        InvoicingSettingsForm form = form(" 21 ");
        form.setSendInvoicesAsAttachment(true);
        form.setSplitPaymentsEnabled(true);
        form.setPositionsConsolidation(true);
        form.setPositionsConsolidationPrefix(" Zestaw ");
        Store store = new Store();

        // when
        form.applyTo(store);

        // then
        assertThat(store.getInvoicingConfiguration().getPaymentTerms()).isEqualTo(21);
        assertThat(store.getInvoicingConfiguration().isSendInvoicesAsAttachment()).isTrue();
        assertThat(store.getInvoicingConfiguration().isSplitPaymentsEnabled()).isTrue();
        assertThat(store.getInvoicingConfiguration().isPositionsConsolidation()).isTrue();
        assertThat(store.getInvoicingConfiguration().getPositionsConsolidationPrefix()).isEqualTo("Zestaw");
    }
}
