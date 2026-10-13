package pl.commercelink.web.stores;

/** One store of the list, with the texts already decided; the template only picks messages by key. */
public record StoreRow(String storeId, String shortId, String name, String ownerName, String ownerEmail, StoreState state,
                       String noteDate, long noteDays, String created, boolean demo, boolean trial, boolean canConvert,
                       boolean canDeactivate, boolean canActivate, boolean activationBlocked, String deleteDialogHref) {
}
