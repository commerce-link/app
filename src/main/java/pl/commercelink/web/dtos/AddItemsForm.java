package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.commercelink.inventory.InventoryKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Entries picked in the "add items" dialog of an offer or an order, posted as indexed fields
 * ({@code items[0].source}, {@code items[0].pimId}, ...). An entry comes either from a pricelist
 * (catalog + pimId) or from the inventory (EAN and/or manufacturer code).
 */
@Getter
@Setter
@NoArgsConstructor
public class AddItemsForm {

    public static final String SOURCE_PRICELIST = "pricelist";
    public static final String SOURCE_INVENTORY = "inventory";

    private List<Entry> items = new ArrayList<>();

    public List<Entry> entries() {
        return items.stream().filter(Entry::isComplete).toList();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Entry {

        private String source = SOURCE_PRICELIST;
        private String catalogId;
        private String pimId;
        private String ean = "";
        private String mfn = "";
        private int qty = 1;

        public static Entry fromPricelist(String catalogId, String pimId, int qty) {
            Entry entry = new Entry();
            entry.source = SOURCE_PRICELIST;
            entry.catalogId = catalogId;
            entry.pimId = pimId;
            entry.qty = qty;
            return entry;
        }

        public static Entry fromInventory(String ean, String mfn, int qty) {
            Entry entry = new Entry();
            entry.source = SOURCE_INVENTORY;
            entry.ean = ean;
            entry.mfn = mfn;
            entry.qty = qty;
            return entry;
        }

        public boolean isFromPricelist() {
            return SOURCE_PRICELIST.equals(source);
        }

        public boolean isFromInventory() {
            return SOURCE_INVENTORY.equals(source);
        }

        public int getQty() {
            return Math.max(1, qty);
        }

        public InventoryKey inventoryKey() {
            return new InventoryKey(trimmed(ean), trimmed(mfn));
        }

        boolean isComplete() {
            if (isFromPricelist()) {
                return notBlank(catalogId) && notBlank(pimId);
            }
            return isFromInventory() && (notBlank(ean) || notBlank(mfn));
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }

        private static String trimmed(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
