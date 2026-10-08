package pl.commercelink.web.warehousedocuments;

import java.util.List;

/** One warehouse document as its details page: every text is resolved, the template only renders. */
public record WarehouseDocumentPage(String documentId, String backHref, String backLabel, String number, String typeName,
        boolean incoming, String reason, String createdText, String author, String totalNet,
        String itemsTitle, List<ItemLine> items, boolean anyItemMenu, String summaryQty, String summaryTotal,
        String linksTitle, List<Link> links, Address counterparty, Address deliveryAddress, Address issuer,
        PrintAction print) {

    public record ItemLine(String name, String ean, String mfn, String qty, String unitCost, String value,
                           String historyHref, String menuLabel) { }

    /** A related record; a null href renders as plain text, wide spans the whole card row. */
    public record Link(String label, String text, String href, boolean wide) { }

    public record Address(String name, List<String> lines, String taxId) { }

    public record PrintAction(String endpoint, int labels, String labelsText, List<Printer> printers) { }

    public record Printer(String name, String deviceId) { }
}
