package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.stores.InvoicingConfiguration;
import pl.commercelink.stores.Store;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings › Invoicing › Invoices. The payment term is read as text: an empty or non-numeric value used to fail the
 * binding of the old {@code int} field with an error page instead of a message at the field. The consolidation prefix
 * is required while consolidation is on, because it becomes the name of the merged position on the invoice and in the
 * payment; it is kept when consolidation is switched off.
 */
@Getter
@Setter
public class InvoicingSettingsForm {

    static final int MAX_PAYMENT_TERM_DAYS = 365;

    private String paymentTerms;
    private boolean sendInvoicesAsAttachment;
    private boolean splitPaymentsEnabled;
    private boolean positionsConsolidation;
    private String positionsConsolidationPrefix;

    public static InvoicingSettingsForm from(InvoicingConfiguration configuration) {
        InvoicingSettingsForm form = new InvoicingSettingsForm();
        InvoicingConfiguration source = configuration != null ? configuration : new InvoicingConfiguration();
        form.paymentTerms = String.valueOf(source.getPaymentTerms());
        form.sendInvoicesAsAttachment = source.isSendInvoicesAsAttachment();
        form.splitPaymentsEnabled = source.isSplitPaymentsEnabled();
        form.positionsConsolidation = source.isPositionsConsolidation();
        form.positionsConsolidationPrefix = source.getPositionsConsolidationPrefix();
        return form;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (FormRules.requireText(errors, "paymentTerms", paymentTerms, "store.invoicing.paymentTerms.required")
                && paymentTermDays() == null) {
            errors.put("paymentTerms", "store.invoicing.paymentTerms.invalid");
        }
        if (positionsConsolidation) {
            FormRules.requireText(errors, "positionsConsolidationPrefix", positionsConsolidationPrefix,
                    "store.invoicing.consolidationPrefix.required");
        }
        return errors;
    }

    public void applyTo(Store store) {
        InvoicingConfiguration configuration = store.getInvoicingConfiguration() != null
                ? store.getInvoicingConfiguration()
                : new InvoicingConfiguration();
        configuration.setPaymentTerms(paymentTermDays());
        configuration.setSendInvoicesAsAttachment(sendInvoicesAsAttachment);
        configuration.setSplitPaymentsEnabled(splitPaymentsEnabled);
        configuration.setPositionsConsolidation(positionsConsolidation);
        configuration.setPositionsConsolidationPrefix(StringUtils.trimToNull(positionsConsolidationPrefix));
        store.setInvoicingConfiguration(configuration);
    }

    private Integer paymentTermDays() {
        String value = StringUtils.trimToEmpty(paymentTerms);
        if (!value.matches("\\d{1,3}")) {
            return null;
        }
        int days = Integer.parseInt(value);
        return days <= MAX_PAYMENT_TERM_DAYS ? days : null;
    }
}
