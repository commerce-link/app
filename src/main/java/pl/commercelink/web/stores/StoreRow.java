package pl.commercelink.web.stores;

/**
 * One store of the list, with the texts already decided; the template only picks messages by key. trialOwnerEmail is the
 * account that deleting a trial store removes (StoreDeletionService), which is not always the owner e-mail shown in the row.
 */
public record StoreRow(String storeId, String shortId, String name, String ownerName, String ownerEmail, String trialOwnerEmail,
                       StoreState state, String noteDate, long noteDays, String created, boolean demo, boolean trial, boolean canConvert,
                       boolean canDeactivate, boolean canActivate, boolean activationBlocked, String deleteDialogHref) {
}
