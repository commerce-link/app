package pl.commercelink.web.deliveries.details;

import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.web.deliveries.details.DeliveryPageModel.*;

import java.util.Objects;
import java.util.Set;

/**
 * Builds the delivery details page model (spec §3.2) from what the controller resolved. A dialog asked for by
 * ?open= (the no-JavaScript opener) is rendered open only when the viewer could open it from the page; "…-all" dialogs
 * come with every waiting allocation checked, as the page's script checks them before opening.
 */
public final class DeliveryPageModelFactory {

    static final Set<String> SELECT_ALL = Set.of("receive-all", "ship-all", "remove-all");

    private DeliveryPageModelFactory() {
    }

    public static DeliveryPageModel build(DeliveryPageData data, DeliveryViewer viewer) {
        Delivery delivery = data.delivery();
        DeliveryLinks links = DeliveryLinks.of(viewer.superAdmin(), delivery.getStoreId(), delivery.getDeliveryId());
        Header header = DeliveryHeaderFactory.header(data, viewer, links);
        ItemsCard items = DeliveryItemsFactory.items(data, viewer, links, data.preselected());
        DocumentsCard documents = DeliveryCardsFactory.documents(delivery, viewer, links);
        PaymentsCard payments = DeliveryCardsFactory.payments(delivery, viewer, links);
        TermsCard terms = DeliveryCardsFactory.terms(delivery, viewer, links, DeliveryItemsFactory.goodsNet(delivery));
        String open = openDialog(data, viewer, header, items, documents, payments, terms);
        if (open != null && SELECT_ALL.contains(open)) {
            items = DeliveryItemsFactory.items(data, viewer, links, DeliveryItemsFactory.pendingIndexes(delivery));
        }
        return new DeliveryPageModel(delivery.getDeliveryId(), delivery.getShortenedDeliveryId(), viewer.superAdmin(),
                delivery.isDropship(), DeliveryBackLink.sanitize(viewer.back()), DeliveryBackLink.labelKey(viewer.superAdmin()),
                links, header, DeliveryHeaderFactory.statusCards(data, viewer, links), items, documents, payments,
                DeliveryCardsFactory.history(delivery), DeliveryCardsFactory.supplier(data),
                DeliveryCardsFactory.consignee(data, links), terms, DeliveryCardsFactory.comment(delivery, viewer, links),
                DeliveryCardsFactory.dialogs(data, items), open);
    }

    /** "ship-all" and "remove-all" open the selection's own dialog (its fields exist once), with every line checked. */
    public static String dialogId(String key) {
        return switch (key) {
            case "ship-all" -> "ship-dialog";
            case "remove-all" -> "remove-dialog";
            default -> key + "-dialog";
        };
    }

    private static String openDialog(DeliveryPageData data, DeliveryViewer viewer, Header header, ItemsCard items,
                                     DocumentsCard documents, PaymentsCard payments, TermsCard terms) {
        String requested = data.openDialog();
        if (requested == null) {
            return null;
        }
        Delivery delivery = data.delivery();
        SelectionBar bar = items.selection();
        boolean anyChecked = items.products().stream().flatMap(product -> product.allocations().stream())
                .anyMatch(AllocationRow::checked);
        boolean repairable = DeliveryRules.purchaseRepairable(viewer, delivery);
        String primaryLabel = header.primary() == null ? null : header.primary().labelKey();
        boolean allowed = switch (requested) {
            case "receive" -> items.selectable() && bar.receive().enabled() && anyChecked;
            case "receive-all" -> "deliveries.details.primary.receiveAll".equals(primaryLabel);
            case "ship" -> items.selectable() && bar.ship().enabled() && anyChecked;
            case "ship-all" -> "deliveries.details.primary.shipAll".equals(primaryLabel);
            case "merge" -> items.selectable() && bar.merge().enabled() && anyChecked;
            case "split" -> items.selectable() && bar.split().enabled() && anyChecked;
            case "remove" -> items.selectable() && bar.remove().enabled() && anyChecked;
            case "remove-all" -> items.selectable() && bar.remove().enabled();
            case "qty" -> items.products().stream()
                    .anyMatch(product -> product.changeQty().enabled() && Objects.equals(product.mfn(), data.openMfn()));
            case "terms", "comment" -> terms.edit().visible();
            case "invoice" -> documents.link().visible();
            case "complete" -> repairable && !delivery.isAwaitingSupplierConfirmation()
                    && (delivery.isOrderFailed() || delivery.isOrderDispatched());
            case "force" -> repairable && delivery.isOrderDispatched() && !delivery.isAwaitingSupplierConfirmation();
            case "reject" -> header.more().reject().visible();
            default -> paymentIndex(requested) >= 0 && payments.editable() && paymentIndex(requested) < payments.rows().size();
        };
        return allowed ? requested : null;
    }

    private static int paymentIndex(String requested) {
        if (!requested.matches("payment-(0|[1-9]\\d{0,2})")) {
            return -1;
        }
        return Integer.parseInt(requested.substring("payment-".length()));
    }
}
