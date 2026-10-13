package pl.commercelink.orders.rma;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.web.dtos.RmaCreateForm;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Creates an RMA from the new RMA page: every rule is checked here, the page only helps. */
@Service
@RequiredArgsConstructor
public class RmaCreateService {

    public static final String ORDER_GONE = "rma.new.error.orderGone";
    public static final List<FulfilmentStatus> REPORTABLE = List.of(FulfilmentStatus.Delivered, FulfilmentStatus.Reserved);

    private final OrdersRepository orders;
    private final OrderItemsRepository orderItems;
    private final RMARepository rmas;
    private final RMAItemsRepository rmaItems;
    private final OpenRmaCoverage coverage;

    public record Outcome(String rmaId, String pageErrorKey, List<RmaCreateForm.Error> errors) {
        public boolean created() {
            return rmaId != null;
        }
    }

    public Outcome create(String storeId, RmaCreateForm form) {
        Order order = form.getOrderId() == null ? null : orders.findById(storeId, form.getOrderId());
        if (order == null || !order.isEligibleForRMACreation()) {
            return new Outcome(null, ORDER_GONE, List.of());
        }
        Map<String, OrderItem> reportable = new LinkedHashMap<>();
        orderItems.findByOrderIdAndStatuses(order.getOrderId(), REPORTABLE).forEach(i -> reportable.put(i.getItemId(), i));
        Map<String, String> claims = coverage.claims(storeId, List.copyOf(reportable.keySet()));
        List<RmaCreateForm.Error> errors = form.validate(reportable, claims);
        if (!errors.isEmpty()) {
            RmaCreateForm.Error first = errors.get(0);
            String pageKey = first.field() == null ? first.key() : null;
            return new Outcome(null, pageKey, pageKey == null ? errors : List.of());
        }

        RMA rma = new RMA(storeId);
        rma.setOrderId(order.getOrderId());
        rma.setEmail(form.getEmail().trim());
        rma.setCreatedAt(LocalDateTime.now());
        rma.setEmailNotificationsEnabled(true);
        List<RMAItem> items = form.selectedItems().stream().map(selected -> {
            RMAItem source = new RMAItem();
            source.setItemId(selected.getItemId());
            source.setQty(form.qtyOf(selected));
            source.setDesiredResolution(selected.getResolution());
            source.setReason(selected.getReason() == null ? null : selected.getReason().trim());
            return new RMAItem(rma.getRmaId(), reportable.get(selected.getItemId()), source);
        }).toList();
        rma.setShippingInsurance(RMAItem.computeTotalPrice(items));
        // same order as before the redesign: the RMA first, so its items never point at a missing RMA
        rmas.save(rma);
        rmaItems.batchSave(items);
        return new Outcome(rma.getRmaId(), null, List.of());
    }
}
