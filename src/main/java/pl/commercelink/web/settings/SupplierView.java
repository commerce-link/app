package pl.commercelink.web.settings;

import org.springframework.web.util.UriUtils;
import pl.commercelink.inventory.supplier.SupplierConnectionView;
import pl.commercelink.scheduling.PollingScheduleDescription;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * A supplier on the suppliers page: where its offer comes from (an integration with its own or the global access
 * details, or a price list uploaded by hand), what the store uses it for and what stopped it from working.
 *
 * @param source       how the offer reaches the store
 * @param state        what the row flags, ACTIVE when nothing
 * @param type         the integration's type name, null for a price list
 * @param editHref     the supplier's page, null when there is nothing to edit (integration gone)
 * @param removeHref   disconnecting (integration) or deleting (price list), confirmed first
 */
public record SupplierView(String identity, String title, Source source, State state, String type,
                           boolean includeInPricing, boolean includeInFulfilment, LocalDateTime feedLastModified,
                           PollingScheduleDescription schedule, String externalSupplierId,
                           String editHref, String removeHref) {

    public enum Source {
        GLOBAL, OWN, CSV
    }

    public enum State {
        ACTIVE, DISABLED, INCOMPLETE, UNAVAILABLE
    }

    public static SupplierView of(SupplierConnectionView connection, boolean incomplete, String suppliersPath) {
        Source source = connection.isManual() ? Source.CSV : connection.isGlobal() ? Source.GLOBAL : Source.OWN;
        State state;
        if (!connection.knownProvider()) {
            state = State.UNAVAILABLE;
        } else if (incomplete) {
            state = State.INCOMPLETE;
        } else if (!connection.enabled()) {
            state = State.DISABLED;
        } else {
            state = State.ACTIVE;
        }
        // A legacy price list is keyed "manual:<name>", so the identity carries whatever the operator typed.
        String href = suppliersPath + "/" + UriUtils.encodePathSegment(connection.identity(), StandardCharsets.UTF_8);
        return new SupplierView(connection.identity(), connection.label(), source, state, connection.providerName(),
                connection.includeInPricing(), connection.includeInFulfilment(), connection.feedLastModified(),
                source == Source.OWN ? connection.scheduleDescription() : null,
                connection.hasExternalSupplierId() ? connection.externalSupplierId() : null,
                state == State.UNAVAILABLE ? null : href,
                href + (source == Source.CSV ? "/delete" : "/disconnect"));
    }

    public boolean csv() {
        return source == Source.CSV;
    }

    /** Message key of what the store uses the supplier for; an unused supplier is flagged. */
    public String usageKey() {
        if (includeInPricing && includeInFulfilment) {
            return "store.suppliers.usage.both";
        }
        if (includeInPricing) {
            return "store.suppliers.usage.pricing";
        }
        return includeInFulfilment ? "store.suppliers.usage.fulfilment" : "store.suppliers.usage.none";
    }

    public boolean unused() {
        return !includeInPricing && !includeInFulfilment;
    }
}
