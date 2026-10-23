package pl.commercelink.stores;

import java.time.LocalDate;

/** An inactive store as its users see it. Only a store whose trial ended has a deletion date. */
public record DeactivationStatus(DeactivationReason reason, LocalDate deactivatedOn, LocalDate deletionOn,
                                 long daysUntilDeletion) {
}
