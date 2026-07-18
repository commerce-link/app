package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderIndexEntry;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.fulfilment.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Controller
class FulfilmentController extends BaseController {

    @Autowired
    private OrderItemsRepository orderItemsRepository;

    @Autowired
    private FulfilmentQueue fulfilmentQueue;

    @Autowired
    private ManualOrderFulfilment manualOrderFulfilment;

    @GetMapping("/dashboard/fulfilment/queue")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public String fulfilmentQueue(@RequestParam(value = "orderIds", required = false) List<String> orderIdsParam, Model model) {
        List<String> orderIds = (orderIdsParam != null && !orderIdsParam.isEmpty()) ? orderIdsParam : new ArrayList<>();

        Map<String, Integer> itemsToOrder = new HashMap<>();
        Predicate<Order> fulfilmentCriteria = order -> {
            int count = orderItemsRepository.findByOrderIdAndStatus(order.getOrderId(), FulfilmentStatus.New).size();
            itemsToOrder.put(order.getOrderId(), count);
            return count > 0;
        };
        List<OrderIndexEntry> ordersPagination = isSuperAdmin()
                ? fulfilmentQueue.pickFulfilmentGroup(orderIds, fulfilmentCriteria)
                : fulfilmentQueue.pickFulfilmentGroup(getStoreId(), orderIds, fulfilmentCriteria);

        List<String> newOrders = ordersPagination.stream()
                .map(OrderIndexEntry::getOrderId)
                .toList();

        Set<String> mergedOrderIds = new LinkedHashSet<>(orderIds);
        mergedOrderIds.addAll(newOrders);

        model.addAttribute("orders", ordersPagination);
        model.addAttribute("itemsToOrder", itemsToOrder);
        model.addAttribute("orderIds", mergedOrderIds);
        model.addAttribute("hasNext", !newOrders.isEmpty());
        model.addAttribute("isSuperAdmin", isSuperAdmin());

        return "fulfilment-queue";
    }

    @PostMapping("/dashboard/orders/fulfilment")
    @PreAuthorize("hasRole('ADMIN')")
    public String initiateMultiOrderManualFulfilment(
            @RequestParam(value = "selectedOrders", defaultValue = "") List<String> selectedOrders,
            @RequestParam(value = "pathSelector", defaultValue = "false") String pathSelector,
            @RequestParam(value = "onlyWithProfit", defaultValue = "false") boolean onlyWithProfit,
            @RequestParam(value = "onlyMultiOrder", defaultValue = "false") boolean onlyMultiOrder,
            @RequestParam(value = "onlyLocalSuppliers", defaultValue = "false") boolean onlyLocalSuppliers,
            @RequestParam(value = "orderByOrder", defaultValue = "false") boolean orderByOrder,
            Model model) {
        return renderManualFulfilmentPage(getStoreId(), selectedOrders, pathSelector, onlyWithProfit, onlyMultiOrder, onlyLocalSuppliers, orderByOrder, model);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/fulfilment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String initiateMultiOrderManualFulfilmentForSuperAdmin(
            @PathVariable("storeId") String storeId,
            @RequestParam(value = "selectedOrders", defaultValue = "") List<String> selectedOrders,
            @RequestParam(value = "pathSelector", defaultValue = "false") String pathSelector,
            @RequestParam(value = "onlyWithProfit", defaultValue = "false") boolean onlyWithProfit,
            @RequestParam(value = "onlyMultiOrder", defaultValue = "false") boolean onlyMultiOrder,
            @RequestParam(value = "onlyLocalSuppliers", defaultValue = "false") boolean onlyLocalSuppliers,
            @RequestParam(value = "orderByOrder", defaultValue = "false") boolean orderByOrder,
            Model model) {
        return renderManualFulfilmentPage(storeId, selectedOrders, pathSelector, onlyWithProfit, onlyMultiOrder, onlyLocalSuppliers, orderByOrder, model);
    }

    private String renderManualFulfilmentPage(String storeId, List<String> selectedOrders, String pathSelector, boolean onlyWithProfit, boolean onlyMultiOrder, boolean onlyLocalSuppliers, boolean orderByOrder, Model model) {
        return renderManualFulfilmentPage(storeId, selectedOrders, pathSelector, onlyWithProfit, onlyMultiOrder, onlyLocalSuppliers, orderByOrder, Map.of(), model);
    }

    private String renderManualFulfilmentPage(String storeId, List<String> selectedOrders, String pathSelector, boolean onlyWithProfit, boolean onlyMultiOrder, boolean onlyLocalSuppliers, boolean orderByOrder, Map<String, Double> committedSuppliers, Model model) {
        List<String> orders = orderByOrder ? sortByItemsToOrder(selectedOrders) : selectedOrders;
        List<String> ordersToFulfil = orderByOrder && !orders.isEmpty() ? List.of(orders.get(0)) : orders;
        FulfilmentForm fulfilmentForm = manualOrderFulfilment.init(storeId, ordersToFulfil, pathSelector, isSuperAdmin(), onlyWithProfit, onlyMultiOrder, onlyLocalSuppliers);
        fulfilmentForm.setSelectedOrders(orders);
        fulfilmentForm.setPathSelector(pathSelector);
        fulfilmentForm.setOnlyWithProfit(onlyWithProfit);
        fulfilmentForm.setOnlyMultiOrder(onlyMultiOrder);
        fulfilmentForm.setOnlyLocalSuppliers(onlyLocalSuppliers);
        fulfilmentForm.setOrderByOrder(orderByOrder);
        fulfilmentForm.setCommittedSuppliers(committedSuppliers);

        model.addAttribute("form", fulfilmentForm);
        if (isSuperAdmin()) {
            model.addAttribute("storeId", storeId);
            model.addAttribute("pathSelector", pathSelector);
            model.addAttribute("isSuperAdmin", true);
        }

        return "fulfilment";
    }

    @PostMapping("/dashboard/orders/fulfilment/commit")
    @PreAuthorize("hasRole('ADMIN')")
    public String commitFulfilmentForm(@ModelAttribute FulfilmentForm form, Model model) {
        manualOrderFulfilment.commit(getStoreId(), form);
        return continueWithNextOrderOrRedirect(getStoreId(), form, model);
    }

    @PostMapping("/dashboard/orders/fulfilment/skip")
    @PreAuthorize("hasRole('ADMIN')")
    public String skipFulfilmentOrder(@ModelAttribute FulfilmentForm form, Model model) {
        return continueWithNextOrderOrRedirect(getStoreId(), form, model);
    }

    @PostMapping("/dashboard/orders/fulfilment/commitAndContinue")
    @PreAuthorize("hasRole('ADMIN')")
    public String commitAndContinueFulfilmentForm(@ModelAttribute FulfilmentForm form, Model model) {
        manualOrderFulfilment.commit(getStoreId(), form);
        return renderManualFulfilmentPage(getStoreId(), form.getSelectedOrders(), "default", false, false, false, false, model);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/fulfilment/commit")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String commitFulfilmentFormForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute FulfilmentForm form, Model model) {
        manualOrderFulfilment.commit(storeId, form);
        return continueWithNextOrderOrRedirect(storeId, form, model);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/fulfilment/skip")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String skipFulfilmentOrderForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute FulfilmentForm form, Model model) {
        return continueWithNextOrderOrRedirect(storeId, form, model);
    }

    private String continueWithNextOrderOrRedirect(String storeId, FulfilmentForm form, Model model) {
        if (form.isOrderByOrder() && form.hasRemainingOrders()) {
            Map<String, Double> committedSuppliers = new LinkedHashMap<>(form.getCommittedSuppliers());
            form.getAcceptedValueByProvider().forEach((provider, value) -> committedSuppliers.merge(provider, value, Double::sum));
            return renderManualFulfilmentPage(storeId, form.getRemainingOrders(), form.getPathSelector(), form.isOnlyWithProfit(), form.isOnlyMultiOrder(), form.isOnlyLocalSuppliers(), true, committedSuppliers, model);
        }
        return form.getRedirectUrl();
    }

    private List<String> sortByItemsToOrder(List<String> selectedOrders) {
        Map<String, Integer> counts = selectedOrders.stream()
                .collect(Collectors.toMap(orderId -> orderId, orderId -> orderItemsRepository.findByOrderIdAndStatus(orderId, FulfilmentStatus.New).size()));
        return selectedOrders.stream()
                .sorted(Comparator.comparing(counts::get).reversed())
                .toList();
    }

    @PostMapping("/dashboard/store/{storeId}/orders/fulfilment/commitAndContinue")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String commitAndContinueFulfilmentFormForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute FulfilmentForm form, Model model) {
        manualOrderFulfilment.commit(storeId, form);
        return renderManualFulfilmentPage(storeId, form.getSelectedOrders(), "default", false, false, false, false, model);
    }
}
