package pl.commercelink.stores;

import java.time.LocalDate;

public record TrialStatus(LocalDate endsOn, long daysLeft, boolean expired) {
}
