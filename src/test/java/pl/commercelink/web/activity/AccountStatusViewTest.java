package pl.commercelink.web.activity;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.DeactivationReason;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.TrialStatus;
import pl.commercelink.web.activity.AccountStatusView.State;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AccountStatusViewTest {

    private static final String STORE_ID = "store-1";
    private static final String CONTACT = "kontakt@commercelink.pl";

    private static TrialStatus trialEndingIn(long days) {
        return new TrialStatus(LocalDate.parse("2026-10-16").plusDays(days - 2), days, false);
    }

    @Test
    void fullAccountInOperationShowsNothing() {
        // when
        AccountStatusView view = AccountStatusView.of(STORE_ID, null, null, CONTACT);

        // then
        assertThat(view).isNull();
    }

    @Test
    void trialWithFourDaysLeftShowsOnlyTheInfoPill() {
        // when
        AccountStatusView view = AccountStatusView.of(STORE_ID, trialEndingIn(4), null, CONTACT);

        // then
        assertThat(view.state()).isEqualTo(State.TRIAL);
        assertThat(view.toneClass()).isEqualTo("is-info");
        assertThat(view.hasAlert()).isFalse();
        assertThat(view.dismissible()).isFalse();
        assertThat(view.daysLeft()).isEqualTo(4);
        assertThat(view.endDate()).isEqualTo("18.10.2026");
    }

    @Test
    void trialWithThreeDaysLeftWarnsInThePillAndAboveThePage() {
        // when
        AccountStatusView view = AccountStatusView.of(STORE_ID, trialEndingIn(AccountStatusView.ENDING_SOON_DAYS), null,
                CONTACT);

        // then
        assertThat(view.state()).isEqualTo(State.TRIAL_ENDING);
        assertThat(view.toneClass()).isEqualTo("is-warn");
        assertThat(view.hasAlert()).isTrue();
        assertThat(view.dismissible()).isTrue();
        assertThat(view.endDate()).isEqualTo("17.10.2026");
    }

    @Test
    void trialEndingTodayStillWarns() {
        // when
        AccountStatusView view = AccountStatusView.of(STORE_ID, trialEndingIn(0), null, CONTACT);

        // then
        assertThat(view.state()).isEqualTo(State.TRIAL_ENDING);
        assertThat(view.daysLeft()).isZero();
        assertThat(view.endDate()).isEqualTo("14.10.2026");
    }

    @Test
    void endedTrialIsReadOnlyWithTheDeletionDate() {
        // given
        DeactivationStatus deactivation = new DeactivationStatus(DeactivationReason.TRIAL_ENDED,
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-15"), 13);

        // when
        AccountStatusView view = AccountStatusView.of(STORE_ID, null, deactivation, CONTACT);

        // then
        assertThat(view.state()).isEqualTo(State.TRIAL_ENDED);
        assertThat(view.toneClass()).isEqualTo("is-bad");
        assertThat(view.readOnly()).isTrue();
        assertThat(view.hasAlert()).isTrue();
        assertThat(view.dismissible()).isFalse();
        assertThat(view.endDate()).isEqualTo("01.10.2026");
        assertThat(view.deletionDate()).isEqualTo("15.10.2026");
        assertThat(view.daysUntilDeletion()).isEqualTo(13);
    }

    @Test
    void storeSwitchedOffByHandIsReadOnlyWithoutDeletionDate() {
        // given
        DeactivationStatus deactivation = new DeactivationStatus(DeactivationReason.MANUAL,
                LocalDate.parse("2026-10-01"), null, 0);

        // when
        AccountStatusView view = AccountStatusView.of(STORE_ID, null, deactivation, CONTACT);

        // then
        assertThat(view.state()).isEqualTo(State.SWITCHED_OFF);
        assertThat(view.toneClass()).isEqualTo("is-bad");
        assertThat(view.hasAlert()).isTrue();
        assertThat(view.dismissible()).isFalse();
        assertThat(view.deletionDate()).isNull();
    }

    @Test
    void deactivationWinsOverATrialStatus() {
        // given
        DeactivationStatus deactivation = new DeactivationStatus(DeactivationReason.MANUAL, null, null, 0);

        // when
        AccountStatusView view = AccountStatusView.of(STORE_ID, trialEndingIn(10), deactivation, CONTACT);

        // then
        assertThat(view.state()).isEqualTo(State.SWITCHED_OFF);
    }

    @Test
    void offersContactOnlyWhenOneIsConfigured() {
        // when
        AccountStatusView withContact = AccountStatusView.of(STORE_ID, trialEndingIn(2), null, CONTACT);
        AccountStatusView withoutContact = AccountStatusView.of(STORE_ID, trialEndingIn(2), null, null);

        // then
        assertThat(withContact.hasContact()).isTrue();
        assertThat(withoutContact.hasContact()).isFalse();
    }

    @Test
    void dismissalIsRememberedPerStoreAndState() {
        // when
        AccountStatusView ending = AccountStatusView.of(STORE_ID, trialEndingIn(2), null, CONTACT);
        AccountStatusView otherStore = AccountStatusView.of("store-2", trialEndingIn(2), null, CONTACT);
        AccountStatusView ended = AccountStatusView.of(STORE_ID, null, new DeactivationStatus(
                DeactivationReason.TRIAL_ENDED, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-15"), 13), CONTACT);

        // then
        assertThat(ending.dismissKey()).isEqualTo("cl.accountStatus.dismissed.store-1.TRIAL_ENDING");
        assertThat(otherStore.dismissKey()).isNotEqualTo(ending.dismissKey());
        assertThat(ended.dismissKey()).isNotEqualTo(ending.dismissKey());
    }
}
