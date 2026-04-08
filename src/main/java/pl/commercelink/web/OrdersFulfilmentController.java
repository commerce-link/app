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
import java.util.LinkedHashSet;
import java.util.List;
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

        Predicate<Order> fulfilmentCriteria = order -> !orderItemsRepository.findByOrderIdAndStatus(order.getOrderId(), FulfilmentStatus.New).isEmpty();
        List<OrderIndexEntry> ordersPagination = isSuperAdmin()
                ? fulfilmentQueue.pickFulfilmentGroup(orderIds, fulfilmentCriteria)
                : fulfilmentQueue.pickFulfilmentGroup(getStoreId(), orderIds, fulfilmentCriteria);

        List<String> newOrders = ordersPagination.stream()
                .map(OrderIndexEntry::getOrderId)
                .collect(Collectors.toList());

        Set<String> mergedOrderIds = new LinkedHashSet<>(orderIds);
        mergedOrderIds.addAll(newOrders);

        model.addAttribute("orders", ordersPagination);
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
            Model model) {
        return renderManualFulfilmentPage(getStoreId(), selectedOrders, pathSelector, model);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/fulfilment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String initiateMultiOrderManualFulfilmentForSuperAdmin(
            @PathVariable("storeId") String storeId,
            @RequestParam(value = "selectedOrders", defaultValue = "") List<String> selectedOrders,
            @RequestParam(value = "pathSelector", defaultValue = "false") String pathSelector,
            Model model) {
        return renderManualFulfilmentPage(storeId, selectedOrders, pathSelector, model);
    }

    private String renderManualFulfilmentPage(String storeId, List<String> selectedOrders, String pathSelector, Model model) {
        FulfilmentForm fulfilmentForm = manualOrderFulfilment.init(storeId, selectedOrders, createPathSelector(pathSelector), isSuperAdmin());

        model.addAttribute("form", fulfilmentForm);
        if (isSuperAdmin()) {
            model.addAttribute("storeId", storeId);
            model.addAttribute("pathSelector", pathSelector);
            model.addAttribute("isSuperAdmin", true);
        }

        return "fulfilment";
    }

    private FulfilmentPathSelector createPathSelector(String pathSelector) {
        switch (pathSelector) {
            case "cheapest":
                return new CheapestFulfilmentPathSelector();
            case "cheapest-min":
                return new ShortestAndCheapestPathSelector();
            case "cheapest-local-min":
                return new ShortestAndCheapestLocalPathSelector();
            case "cheapest-local-avg":
                return new SecondShortestAndCheapestLocalPathSelector();
            default:
                return new DefaultPathSelector();
        }
    }

    @PostMapping("/dashboard/orders/fulfilment/commit")
    @PreAuthorize("hasRole('ADMIN')")
    public String commitFulfilmentForm(@ModelAttribute FulfilmentForm form) {
        manualOrderFulfilment.commit(getStoreId(), form);
        return form.getRedirectUrl();
    }

    @PostMapping("/dashboard/orders/fulfilment/commitAndContinue")
    @PreAuthorize("hasRole('ADMIN')")
    public String commitAndContinueFulfilmentForm(@ModelAttribute FulfilmentForm form, Model model) {
        manualOrderFulfilment.commit(getStoreId(), form);
        return renderManualFulfilmentPage(getStoreId(), form.getSelectedOrders(), "default", model);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/fulfilment/commit")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String commitFulfilmentFormForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute FulfilmentForm form) {
        manualOrderFulfilment.commit(storeId, form);
        return form.getRedirectUrl();
    }

    @PostMapping("/dashboard/store/{storeId}/orders/fulfilment/commitAndContinue")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String commitFulfilmentFormForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute FulfilmentForm form, Model model) {
        manualOrderFulfilment.commit(storeId, form);
        return renderManualFulfilmentPage(storeId, form.getSelectedOrders(), "default", model);
    }
}
