package pl.commercelink.web.offers;

import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.web.orders.AddressBlock;
import pl.commercelink.web.orders.ItemMargin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The offer details page (/dashboard/offer/{id}): every text resolved, amounts formatted without the currency (the
 * template adds it through general.currency.amount). storeId, recordName and fulfilmentType are the raw record values
 * for the hidden fields of the items form; catalogs feed the shared add-items dialog. version = the record's optimistic
 * lock version the page was built from (null for a record never saved with one): the table's save names it, so a save
 * over a record changed since is refused.
 */
public record OfferDetailsPage(String offerId, String storeId, String shortId, boolean template, String title, String backHref,
                               String backLabelKey, Pill validity, Meta meta, List<Line> lines,
                               List<StoreCategories.Group> categoryGroups, List<ProductCatalog> catalogs, Summary summary, Terms terms,
                               Customer customer, String note, String clientUrl, boolean proformaAvailable,
                               boolean copyWithContactAvailable, String recordName, String fulfilmentType,
                               Long version) {

    /** The lines of the products section, in the record's order: every item not a service, with their group heads. */
    public List<Line> productLines() {
        return section(false);
    }

    /** The lines of the services section ("Usługi"), in the record's order. */
    public List<Line> serviceLines() {
        return section(true);
    }

    // A variant group belongs to the section of its first item: an old record mixing products and services in one
    // group shows it whole there (decided with the client), so a group is never split across the sections.
    private List<Line> section(boolean services) {
        Map<String, Boolean> groupIsService = new HashMap<>();
        for (Line line : lines) {
            if (line instanceof Item item && item.variantGroupId() != null) {
                groupIsService.putIfAbsent(item.variantGroupId(), item.service());
            }
        }
        List<Line> section = new ArrayList<>();
        for (Line line : lines) {
            boolean service = switch (line) {
                case GroupHead head -> groupIsService.getOrDefault(head.name(), false);
                case Item item -> item.variantGroupId() != null ? groupIsService.get(item.variantGroupId()) : item.service();
            };
            if (service == services) {
                section.add(line);
            }
        }
        return section;
    }

    /** A category of none of the catalogs (an old item, an import) stays selectable as an extra option. */
    public boolean knownCategory(String category) {
        return categoryGroups.stream().anyMatch(group -> group.names().contains(category));
    }

    /** A message key, its one argument (null when the key has none) and the pill tone. */
    public record Pill(String key, Object arg, String tone) {
    }

    /** client and dueNote are null when unknown; total is the price for the customer. */
    public record Meta(String client, String dueNote, String createdAt, String author, String total, int itemsCount) {
    }

    /** One table row in the order of the record: a variant group head, then its items. */
    public sealed interface Line permits GroupHead, Item {
    }

    public record GroupHead(String name, int size) implements Line {
    }

    /**
     * market null = a service; margin null = a service. index is the position in the record's item list (the form
     * binding). incomplete = the table save would drop it (BasketItem.isComplete on what the row posts).
     * costForMargin = the item's own cost, else estimatedCost (costEstimated); 0 when neither is known.
     * estimatedCost = halfway between the suppliers' lowest and median gross cost, 0 when there is none (a service, no
     * offers). Shown only: the stored unitCost never takes the estimate.
     */
    public record Item(int index, String id, String catalogId, String name, String category, String mfn, long qty,
                       String unitPriceInput, String unitCostInput, double unitPrice, double unitCost,
                       double costForMargin, boolean costEstimated, double estimatedCost, boolean consolidated, String variantGroupId, boolean variantSelected, boolean service,
                       Integer estimatedDeliveryDays, boolean incomplete, ItemMargin margin, Market market,
                       String medianProviders, String otherProviders, long lowestQty, long medianQty) implements Line {
    }

    /** Supplier cost now (lowest → median gross per unit at the enabled suppliers); missing = no supplier offers it. */
    public record Market(boolean missing, String low, String median, int suppliers, long stock) {
        static Market none() {
            return new Market(true, null, null, 0, 0);
        }
    }

    /** deliveryRemoved = the chosen option is no longer in the store's configuration (counted as 0, like the list). */
    public record Summary(String itemsTotal, String deliveryName, boolean deliveryRemoved, String deliveryPrice,
                          double deliveryPriceValue, String total, String net, String cost, String profitGross,
                          String profitNet, boolean variantsNote, boolean costEstimated) {
    }

    /** expiresAtText and expiresAtInput are null for an offer without a deadline. */
    public record Terms(String expiresAtText, String expiresAtInput, String deliveryOptionId,
                        List<DeliveryChoice> deliveryChoices, boolean showPrices, String fulfilmentLabelKey,
                        String gclid) {
    }

    public record DeliveryChoice(String id, String label) {
    }

    /** billing == null: the customer has not sent the invoice details yet; shipping == null: no separate address. */
    public record Customer(String name, String surname, String company, String email, String phone,
                           boolean contactFilled, AddressBlock billing, AddressBlock shipping,
                           boolean shippingSameAsBilling) {
    }
}
