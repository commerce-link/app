package pl.commercelink.web.fulfilment;

/** One order of the fulfilment queue's group, every text already resolved so the template only prints. */
public record FulfilmentQueueRow(String orderId, String href, String number, String sourceText, String externalId,
                                 String clientName, String clientCity, String email, String orderedAtText,
                                 String dueText, String dueNote, String dueTone, int itemsToOrder) {
}
