package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import pl.commercelink.stores.PosReceiptMode;
import pl.commercelink.stores.ReceiptConfiguration;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings › E-receipts: whether delivered consumer orders get automatic e-receipts, whatever their source, and what
 * point-of-sale orders get as their receipt.
 */
@Getter
@Setter
public class ReceiptSettingsForm {

    private boolean enabled;
    private PosReceiptMode posReceiptMode;

    public static ReceiptSettingsForm from(ReceiptConfiguration configuration) {
        ReceiptSettingsForm form = new ReceiptSettingsForm();
        form.enabled = configuration.isEnabled();
        form.posReceiptMode = configuration.getPosReceiptMode();
        return form;
    }

    public Map<String, String> validate() {
        return new LinkedHashMap<>();
    }

    public void applyTo(Store store, LocalDateTime now) {
        ReceiptConfiguration configuration = store.getReceiptConfiguration();
        boolean firstTime = configuration.getEnabledAt() == null;
        if (posReceiptMode != null) {
            configuration.setPosReceiptMode(posReceiptMode);
        }
        if (enabled) {
            configuration.enable(now);
            if (firstTime) {
                store.enableOrderReceiptEmailNotification();
            }
        } else {
            configuration.disable();
        }
    }
}
