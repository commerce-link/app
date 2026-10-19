package pl.commercelink.web.activity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.TrialStatus;

/** Read from the request, where the dashboard gate left it, so the account status costs no second read of the store. */
@ControllerAdvice
public class StoreStatusBannerAdvice {

    private final String contactEmail;

    public StoreStatusBannerAdvice(@Value("${app.registration.trial-contact-email:}") String contactEmail) {
        this.contactEmail = contactEmail;
    }

    @ModelAttribute("accountStatus")
    public AccountStatusView accountStatus(HttpServletRequest request) {
        return AccountStatusView.of(
                (String) request.getAttribute(DashboardReadOnlyInterceptor.STORE_ID_ATTRIBUTE),
                (TrialStatus) request.getAttribute(DashboardReadOnlyInterceptor.TRIAL_STATUS_ATTRIBUTE),
                (DeactivationStatus) request.getAttribute(DashboardReadOnlyInterceptor.DEACTIVATION_STATUS_ATTRIBUTE),
                accountContactEmail());
    }

    String accountContactEmail() {
        return contactEmail.isBlank() ? null : contactEmail;
    }
}
