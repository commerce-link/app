package pl.commercelink.web.activity;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import pl.commercelink.stores.DeactivationReason;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.TrialStatus;
import pl.commercelink.web.activity.AccountStatusView.State;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StoreStatusBannerAdviceTest {

    @Test
    void buildsTheAccountStatusFromWhatTheDashboardGateLeft() {
        // given
        StoreStatusBannerAdvice advice = new StoreStatusBannerAdvice("kontakt@commercelink.pl");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(DashboardReadOnlyInterceptor.STORE_ID_ATTRIBUTE, "store-1");
        request.setAttribute(DashboardReadOnlyInterceptor.TRIAL_STATUS_ATTRIBUTE,
                new TrialStatus(LocalDate.parse("2026-10-12"), 5, false));

        // when
        AccountStatusView view = advice.accountStatus(request);

        // then
        assertThat(view.state()).isEqualTo(State.TRIAL);
        assertThat(view.endDate()).isEqualTo("12.10.2026");
        assertThat(view.contactEmail()).isEqualTo("kontakt@commercelink.pl");
        assertThat(view.dismissKey()).contains("store-1");
    }

    @Test
    void deactivationLeftByTheGateMakesThePanelReadOnly() {
        // given
        StoreStatusBannerAdvice advice = new StoreStatusBannerAdvice("");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(DashboardReadOnlyInterceptor.DEACTIVATION_STATUS_ATTRIBUTE,
                new DeactivationStatus(DeactivationReason.MANUAL, null, null, 0));

        // when
        AccountStatusView view = advice.accountStatus(request);

        // then
        assertThat(view.state()).isEqualTo(State.SWITCHED_OFF);
        assertThat(view.hasContact()).isFalse();
    }

    @Test
    void exposesNothingForStoreWithoutStatus() {
        // given
        StoreStatusBannerAdvice advice = new StoreStatusBannerAdvice("kontakt@commercelink.pl");

        // when / then
        assertThat(advice.accountStatus(new MockHttpServletRequest())).isNull();
    }

    @Test
    void offersTheContactOnlyWhenOneIsConfigured() {
        // when / then
        assertThat(new StoreStatusBannerAdvice("kontakt@commercelink.pl").accountContactEmail())
                .isEqualTo("kontakt@commercelink.pl");
        assertThat(new StoreStatusBannerAdvice(" ").accountContactEmail()).isNull();
    }
}
