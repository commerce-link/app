package pl.commercelink.web.activity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.TrialStatus;

/** Read from the request, where the dashboard gate left it, so the banner costs no second read of the store. */
@ControllerAdvice
public class StoreStatusBannerAdvice {

    private final String contactEmail;

    public StoreStatusBannerAdvice(@Value("${app.registration.trial-contact-email:}") String contactEmail) {
        this.contactEmail = contactEmail;
    }

    @ModelAttribute("trialStatus")
    public TrialStatus trialStatus(HttpServletRequest request) {
        return (TrialStatus) request.getAttribute(DashboardReadOnlyInterceptor.TRIAL_STATUS_ATTRIBUTE);
    }

    @ModelAttribute("deactivationStatus")
    public DeactivationStatus deactivationStatus(HttpServletRequest request) {
        return (DeactivationStatus) request.getAttribute(DashboardReadOnlyInterceptor.DEACTIVATION_STATUS_ATTRIBUTE);
    }

    @ModelAttribute("accountContactEmail")
    public String accountContactEmail() {
        return contactEmail.isBlank() ? null : contactEmail;
    }
}
