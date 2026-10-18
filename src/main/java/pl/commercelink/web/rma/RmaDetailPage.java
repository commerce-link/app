package pl.commercelink.web.rma;

import java.util.List;

/**
 * Everything the RMA details page shows, worked out by RmaDetailPageFactory so the templates neither compute statuses
 * nor decide which actions are available.
 *
 * closed: the RMA is Rejected or Completed and is read-only. marketplace: a return imported from a marketplace.
 * openDialog: the dialog rendered open without JavaScript (?open=...), one of next, status, reject, add, shipment-new,
 * split or shipment-{n}, where n is the 1-based number of the shipment in RMA.getShipments() (the same numbering as the
 * shipments list rows); null when the request named none, an unknown one, or one the RMA does not offer right now.
 * split: the item the split dialog opens for (?open=split&item=...), null unless openDialog is "split".
 * marketplaceCard: null on an RMA the store created.
 */
public record RmaDetailPage(String rmaId, String shortId, boolean closed, boolean marketplace, String openDialog,
                            Header header, Items items, Media media, Settings settings, MarketplaceCard marketplaceCard,
                            Address address, History history, CurrentFields current, AddDialog add, SplitTarget split) {

    /**
     * next: the primary action, null when the process has no next step to offer (Approved, WaitingForItems, closed).
     * clientName: the person or company from the RMA's address, else its e-mail, null when neither. createdAt:
     * "dd.MM.yyyy HH:mm" or null. itemCount: "2 pozycje". total: the gross value of the items. clientLink: the client's
     * return page, shown on open and closed RMAs alike. rejectBlockedKey: why "Odrzuć" is greyed out, null when it is
     * not. resendHref: the POST that sends the recorded marketplace decisions again, null without any.
     */
    public record Header(String statusLabel, String statusTone, NextStatus next, String clientName,
                         String source, String orderId, String orderHref, String createdAt, String itemCount,
                         String total, MarketplaceLine marketplaceLine, String clientLink, boolean canChangeStatus,
                         boolean canReject, String rejectBlockedKey, String resendHref,
                         List<StatusOption> statusOptions, String rejectionReason) {
    }

    /**
     * target: the RMAStatus name the action posts. labelKey: the button; titleKey: the dialog's question; effectKey:
     * what the change does. unresolved: items still New or Received when finishing a Processing RMA, 0 otherwise.
     */
    public record NextStatus(String target, String labelKey, String icon, String titleKey, String effectKey,
                             int unresolved) {
    }

    /** "Allegro: zwrot RT-5512-0931" with the marketplace's status and, for an untracked parcel, its carrier. */
    public record MarketplaceLine(String text, String statusLabel, String statusTone, String carrier) {
    }

    /** One status of "Zmień status" with what choosing it does; disabled: ItemsReceived while Processing. */
    public record StatusOption(String value, String label, String effectKey, boolean current, boolean disabled) {
    }

    /** selectable: rows can be selected for bulk actions; canAdd: "Dodaj pozycję" has order items left to add. */
    public record Items(List<Row> rows, boolean selectable, boolean canAdd, String total, boolean marketplace,
                        boolean refundDeliveryDefault) {
    }

    /**
     * index: the row's position in RMAItemsForm (rmaItems[index]). status: the RMAItemStatus name. desired/actual:
     * localized resolutions, null when not set.
     */
    public record Row(int index, String rmaItemId, String shortId, String href, String name, String ean,
                      String serialNo, String reason, String status, String statusLabel, String statusTone,
                      String desired, String actual, int qty, String unitPrice, String total, double totalValue) {
    }

    public record Media(List<Tile> tiles, boolean editable) {
    }

    /** key: the stored media key, posted back as existingMedia. kind: image, video, pdf or file. */
    public record Tile(String key, String fileName, String href, String kind) {
    }

    /** insurance: the form value ("4247.00"); insuranceLabel: the same amount for reading ("4 247,00 PLN"). */
    public record Settings(String email, boolean emailRequired, boolean notifications, String insurance,
                           String insuranceLabel) {
    }

    /** statusLabel: null before the marketplace reported a status. resendHref: null without recorded decisions. */
    public record MarketplaceCard(String name, String statusLabel, String statusTone, String reference,
                                  List<Decision> decisions, String resendHref) {
    }

    /** commandShort: "5b1d…e07a", the idempotency key the marketplace dedupes on, in full in commandId. */
    public record Decision(String typeLabel, String recordedAt, String commandShort, String commandId) {
    }

    /**
     * missing: no address at all; incomplete: an address "Ship to client" would refuse. dialogPage: the address form
     * shown in the dialog, null on a closed RMA, where the address is read-only.
     */
    public record Address(List<String> lines, String phone, String email, boolean missing, boolean incomplete,
                          String pageHref, RmaAddressPage dialogPage) {
    }

    /** events: newest first, ending with the RMA's creation; visible: how many show before "Pokaż starsze". */
    public record History(List<Event> events, int visible) {
    }

    public record Event(String at, String title) {
    }

    /**
     * The stored values every form posting to /dashboard/rma/{id} carries for the fields it does not edit: that save
     * overwrites all of them. status: the RMAStatus name (New when unset); insurance as a form value; media: the keys.
     */
    public record CurrentFields(String status, String email, String rejectionReason, boolean notifications,
                                String insurance, List<String> media) {
    }

    public record AddDialog(List<AddOption> options) {
    }

    /** One order item left to add: maxQty is its quantity in the order, unitPrice the gross price per piece. */
    public record AddOption(String itemId, String label, int maxQty, double unitPrice) {
    }

    /** action: the split POST of the item. */
    public record SplitTarget(String rmaItemId, String name, int qty, String action) {
    }
}
