package pl.commercelink.web.inventory;

import java.util.List;

/** "Dodaj do katalogu": which manual catalog category the chosen products go to, before "Uzupełnij dane". */
public record AddToCatalogDialog(List<String> eans, String productName, String pimCategoryName, List<Option> matching,
                                 List<Group> others, boolean noManualCategories, String returnTo, String action) {

    public static final String ACTION = "/dashboard/inventory/browse/add";
    public static final String OTHER = "other";

    public int count() {
        return eans.size();
    }

    public boolean anyMatching() {
        return !matching.isEmpty();
    }

    /** {@code preselected}: the radio checked when the dialog opens; always false in {@link Group}. */
    public record Option(String value, String label, int alreadyIn, boolean preselected) {
    }

    public record Group(String catalogName, List<Option> options) {
    }
}
