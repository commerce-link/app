package pl.commercelink.web.activity;

import pl.commercelink.stores.DeactivationReason;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.TrialStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * The account status of the signed-in store as the dashboard frame shows it: a pill in the top bar for every state,
 * and an alert above the page once the state asks for attention (the last days of the trial, a read-only panel).
 */
public record AccountStatusView(State state, long daysLeft, String endDate, String deletionDate,
                                long daysUntilDeletion, String contactEmail, String dismissKey) {

    /** From this many days before its end (and on the last day) the trial is announced above the page as well. */
    public static final int ENDING_SOON_DAYS = 3;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String DISMISS_KEY_PREFIX = "cl.accountStatus.dismissed.";

    public enum State {
        TRIAL("is-info"),
        TRIAL_ENDING("is-warn"),
        TRIAL_ENDED("is-bad"),
        SWITCHED_OFF("is-bad");

        private final String toneClass;

        State(String toneClass) {
            this.toneClass = toneClass;
        }
    }

    /** {@code null} for a full account in operation, which shows nothing. */
    public static AccountStatusView of(String storeId, TrialStatus trial, DeactivationStatus deactivation,
                                       String contactEmail) {
        if (deactivation != null) {
            return deactivated(storeId, deactivation, contactEmail);
        }
        if (trial != null) {
            State state = trial.daysLeft() <= ENDING_SOON_DAYS ? State.TRIAL_ENDING : State.TRIAL;
            return new AccountStatusView(state, trial.daysLeft(), format(trial.endsOn()), null, 0, contactEmail,
                    dismissKey(storeId, state));
        }
        return null;
    }

    private static AccountStatusView deactivated(String storeId, DeactivationStatus deactivation, String contactEmail) {
        if (deactivation.reason() == DeactivationReason.TRIAL_ENDED && deactivation.deletionOn() != null) {
            return new AccountStatusView(State.TRIAL_ENDED, 0, format(deactivation.deactivatedOn()),
                    format(deactivation.deletionOn()), deactivation.daysUntilDeletion(), contactEmail,
                    dismissKey(storeId, State.TRIAL_ENDED));
        }
        return new AccountStatusView(State.SWITCHED_OFF, 0, null, null, 0, contactEmail,
                dismissKey(storeId, State.SWITCHED_OFF));
    }

    /** Scoped to the store and the state, so a hidden alert comes back as soon as the state changes. */
    private static String dismissKey(String storeId, State state) {
        return DISMISS_KEY_PREFIX + (storeId == null ? "" : storeId + ".") + state.name();
    }

    private static String format(LocalDate date) {
        return date == null ? null : date.format(DATE);
    }

    public String toneClass() {
        return state.toneClass;
    }

    public boolean onTrial() {
        return state == State.TRIAL || state == State.TRIAL_ENDING;
    }

    public boolean trialEnding() {
        return state == State.TRIAL_ENDING;
    }

    public boolean trialEnded() {
        return state == State.TRIAL_ENDED;
    }

    public boolean switchedOff() {
        return state == State.SWITCHED_OFF;
    }

    public boolean readOnly() {
        return trialEnded() || switchedOff();
    }

    public boolean hasAlert() {
        return state != State.TRIAL;
    }

    /** Only the trial's last days may be hidden: a read-only panel has to keep saying why nothing is saved. */
    public boolean dismissible() {
        return trialEnding();
    }

    public boolean hasContact() {
        return contactEmail != null;
    }
}
