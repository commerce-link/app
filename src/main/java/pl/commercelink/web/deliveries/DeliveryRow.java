package pl.commercelink.web.deliveries;

import java.util.List;

/** One row of the deliveries list, every text already resolved (spec §3.1). */
public record DeliveryRow(String href, String number, String storeId, boolean dropship, String supplierLabel,
                          String externalId, boolean externalIdPending, String counterparty, String orderedText,
                          String dateText, String dateLabelKey, String dueNote, String dueTone, String stateLabel,
                          String stateTone, String grossText, String netText, List<Mark> marks) {

    public record Mark(String code, boolean done, String label) {
    }

    public boolean hasTodo() {
        return marks.stream().anyMatch(mark -> !mark.done());
    }
}
