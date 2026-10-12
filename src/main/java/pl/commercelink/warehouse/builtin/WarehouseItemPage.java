package pl.commercelink.warehouse.builtin;

import pl.commercelink.products.StoreCategories;

import java.util.List;
import java.util.Map;

/** Everything the item page prints, already resolved to text; the template never computes. */
/** {@code categoryOutsideCatalog} is the stored category when no catalog offers it (kept as an option), otherwise null. */
public record WarehouseItemPage(String itemId, String name, String statusLabel, String statusTone, String ean, String mfn,
                                String shortId, String backHref, String backLabel, Mode mode, String destroyedTitle,
                                List<Fact> facts, String totalNet, Origin origin, WarehouseItemEditForm form,
                                Map<String, String> errors, String errorMessage, List<StoreCategories.Group> categoryGroups,
                                String categoryOutsideCatalog, List<Choice> conditions, List<Choice> statuses,
                                Choice readonlyCondition, Delete delete) {
    public enum Mode { EDIT, NEW, DESTROYED }
    public record Fact(String label, String value, boolean numeric) { }
    public record Link(String text, String href) { }
    public record Origin(String deliveryHref, String deliveryText, String supplier, String documentsHref, List<Link> serials) { }
    public record Choice(String value, String label, String description, String tone) { }
    /** The delete confirmation (dialog with JavaScript, the confirm page without). */
    public record Delete(String href, String title, String message, String action) { }
}
