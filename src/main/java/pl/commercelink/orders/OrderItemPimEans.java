package pl.commercelink.orders;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.externaloffers.OfferEans;
import pl.commercelink.externaloffers.ResolvedOffer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which codes of new order items are worth asking the PIM for. The request goes out with the highest priority, the PIM
 * approves it by itself, nobody reviews it, and the sweeper does not cover orders, so a wrong code would stay in the
 * PIM for good. The rule is therefore strict: only a code known to be a product's EAN passes, and one that merely
 * looks like it (wrong check digit, one repeated digit) does not.
 * <p>
 * A marketplace line is judged by {@code ean}, never by {@code sku}: a recognised product without an EAN has its
 * manufacturer code in the sku, and an unrecognised line has the marketplace's raw code there.
 */
final class OrderItemPimEans {

    private OrderItemPimEans() {
    }

    /** @return the distinct checked EANs, in the order of the items; never null */
    static List<String> of(Collection<OrderItem> items) {
        Set<String> eans = new LinkedHashSet<>();
        for (OrderItem item : items) {
            String ean = OfferEans.unifyChecked(candidate(item));
            if (ean != null) {
                eans.add(ean);
            }
        }
        return new ArrayList<>(eans);
    }

    private static String candidate(OrderItem item) {
        if (item.isService()) {
            return null;
        }
        if (isMarketplaceLine(item)) {
            return isResolved(item.getExternalOfferSource()) ? item.getEan() : null;
        }
        return StringUtils.substringBefore(item.getSku(), ":");
    }

    private static boolean isMarketplaceLine(OrderItem item) {
        return item.getExternalItemId() != null || item.getExternalOfferKey() != null;
    }

    private static boolean isResolved(ResolvedOffer.Source source) {
        return source == ResolvedOffer.Source.LINK || source == ResolvedOffer.Source.OWN_OFFER;
    }
}
