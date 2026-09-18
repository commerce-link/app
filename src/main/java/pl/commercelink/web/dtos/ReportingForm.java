package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import pl.commercelink.stores.ReportingConfiguration;
import pl.commercelink.stores.Store;

/** Reporting settings. The Google Ads token is never part of the form, so saving cannot change the conversions address. */
@Getter
@Setter
public class ReportingForm {

    private boolean googleAdsEnabled;

    public static ReportingForm from(Store store) {
        ReportingForm form = new ReportingForm();
        form.googleAdsEnabled = store.getReportingConfiguration() != null && store.getReportingConfiguration().isGoogleAdsEnabled();
        return form;
    }

    public void applyTo(Store store) {
        ReportingConfiguration configuration = store.getReportingConfiguration();
        if (configuration == null) {
            configuration = new ReportingConfiguration();
            store.setReportingConfiguration(configuration);
        }
        if (googleAdsEnabled) {
            configuration.enableGoogleAds();
        } else {
            configuration.disableGoogleAds();
        }
    }
}
