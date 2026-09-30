package pl.commercelink.web.activity;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import pl.commercelink.stores.DeactivationReason;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.TrialStatus;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class StoreStatusBannerAdviceTest {

    @Test
    void exposesWhatTheDashboardGateLeft() {
        // given
        StoreStatusBannerAdvice advice = new StoreStatusBannerAdvice("");
        MockHttpServletRequest request = new MockHttpServletRequest();
        TrialStatus trial = new TrialStatus(LocalDate.parse("2026-10-12"), 5, false);
        DeactivationStatus deactivation = new DeactivationStatus(DeactivationReason.MANUAL, null, null, 0);
        request.setAttribute(DashboardReadOnlyInterceptor.TRIAL_STATUS_ATTRIBUTE, trial);
        request.setAttribute(DashboardReadOnlyInterceptor.DEACTIVATION_STATUS_ATTRIBUTE, deactivation);

        // when / then
        assertSame(trial, advice.trialStatus(request));
        assertSame(deactivation, advice.deactivationStatus(request));
    }

    @Test
    void exposesNothingForStoreWithoutStatus() {
        // given
        StoreStatusBannerAdvice advice = new StoreStatusBannerAdvice("");
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when / then
        assertNull(advice.trialStatus(request));
        assertNull(advice.deactivationStatus(request));
    }

    @Test
    void offersTheContactOnlyWhenOneIsConfigured() {
        // when / then
        assertEquals("kontakt@commercelink.pl", new StoreStatusBannerAdvice("kontakt@commercelink.pl").accountContactEmail());
        assertNull(new StoreStatusBannerAdvice(" ").accountContactEmail());
    }
}
