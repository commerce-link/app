package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import pl.commercelink.stores.ReportingConfiguration;
import pl.commercelink.stores.Store;

/** Reporting settings. The Google Ads token is never part of the form, so saving cannot change the conversions address. */
@Getter
@Setter
public class ReportingForm {

    // Boxed: a post without the field (a tab opened before this form existed) must not read as "switched off".
    private Boolean googleAdsEnabled;

    public static ReportingForm from(Store store) {
        ReportingForm form = new ReportingForm();
        form.googleAdsEnabled = store.getReportingConfiguration() != null && store.getReportingConfiguration().isGoogleAdsEnabled();
        return form;
    }

    public boolean isGoogleAdsEnabled() {
        return Boolean.TRUE.equals(googleAdsEnabled);
    }

    /** Whether the switch was posted at all. */
    public boolean submitted() {
        return googleAdsEnabled != null;
    }

    public void applyTo(Store store) {
        ReportingConfiguration configuration = store.getReportingConfiguration();
        if (configuration == null) {
            configuration = new ReportingConfiguration();
            store.setReportingConfiguration(configuration);
        }
        if (isGoogleAdsEnabled()) {
            configuration.enableGoogleAds();
        } else {
            configuration.disableGoogleAds();
        }
    }
}
