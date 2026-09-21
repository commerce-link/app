package pl.commercelink.web.settings;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.marketplace.MarketplaceOfferSnapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One export run as the operator reads it: every offer with its outcome, how many offers ended each way (the filter),
 * and why an aborted run stopped. The abort is a row of the run file without a product ({@code exportAborted}); here it
 * is the run's message, not an offer.
 */
public record MarketplaceExportRunView(List<Row> rows, Map<Outcome, Integer> counts, String abortMessage) {

    public enum Outcome {
        PUBLISHED("is-ok"), REJECTED("is-warn"), REMOVAL_PENDING("is-neutral"), UNKNOWN("is-neutral");

        private final String tone;

        Outcome(String tone) {
            this.tone = tone;
        }

        public String tone() {
            return tone;
        }

        public String filter() {
            return name().toLowerCase().replace('_', '-');
        }

        static Outcome of(String outcome) {
            String value = StringUtils.trimToNull(outcome);
            // Run files written before outcomes were recorded hold only the published snapshot (pimId, price, quantity).
            if (value == null || MarketplaceOfferSnapshot.OUTCOME_PUBLISHED.equals(value)) {
                return PUBLISHED;
            }
            if (MarketplaceOfferSnapshot.OUTCOME_REMOVAL_PENDING.equals(value)) {
                return REMOVAL_PENDING;
            }
            // A value this version does not know is not claimed to be a rejection.
            return MarketplaceOfferSnapshot.OUTCOME_REJECTED.equals(value) ? REJECTED : UNKNOWN;
        }
    }

    /**
     * @param price whole zloty; null for an offer rejected before it had one
     */
    public record Row(String pimId, Long price, long quantity, Outcome outcome, int removalAttempts, String reasonCode,
                      String message) {

        /** Text the search box matches: the product id and the marketplace's words about it. */
        public String searchText() {
            return String.join(" ", StringUtils.defaultString(pimId), StringUtils.defaultString(reasonCode),
                    StringUtils.defaultString(message)).toLowerCase();
        }
    }

    public static MarketplaceExportRunView of(List<MarketplaceOfferSnapshot> snapshots) {
        List<Row> rows = new ArrayList<>();
        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        for (Outcome outcome : Outcome.values()) {
            counts.put(outcome, 0);
        }
        String abortMessage = null;
        for (MarketplaceOfferSnapshot snapshot : snapshots) {
            if (MarketplaceOfferSnapshot.OUTCOME_EXPORT_ABORTED.equals(snapshot.outcome())) {
                abortMessage = StringUtils.defaultIfBlank(snapshot.message(), "");
                continue;
            }
            Outcome outcome = Outcome.of(snapshot.outcome());
            counts.merge(outcome, 1, Integer::sum);
            rows.add(new Row(snapshot.pimId(), snapshot.price() > 0 ? snapshot.price() : null, snapshot.quantity(), outcome,
                    snapshot.removalAttempts(), StringUtils.trimToNull(snapshot.reasonCode()),
                    StringUtils.trimToNull(snapshot.message())));
        }
        return new MarketplaceExportRunView(rows, counts, abortMessage);
    }

    /** The filter the page opens with: the rejections when there are any, since that is what a run is opened for. */
    public String defaultFilter() {
        return counts.get(Outcome.REJECTED) > 0 ? Outcome.REJECTED.filter() : "all";
    }

    public int count(Outcome outcome) {
        return counts.get(outcome);
    }
}
