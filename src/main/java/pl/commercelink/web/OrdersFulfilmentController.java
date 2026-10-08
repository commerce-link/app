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
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.web.fulfilment.FulfilmentQueuePageFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    @Autowired
    private SupplierLabels supplierLabels;

    @Autowired
    private FulfilmentQueuePageFactory fulfilmentQueuePageFactory;

    @GetMapping("/dashboard/fulfilment/queue")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public String fulfilmentQueue(@RequestParam(value = "orderIds", required = false) List<String> orderIdsParam, Model model, Locale locale) {
        List<String> skipped = orderIdsParam == null ? new ArrayList<>() : new ArrayList<>(orderIdsParam);

        Map<String, Integer> itemsToOrder = new HashMap<>();
        // the criteria already loads every candidate order; keeping them lets the rows be built without a second read
        Map<String, Order> loadedOrders = new HashMap<>();
        Predicate<Order> fulfilmentCriteria = order -> {
            int count = orderItemsRepository.findByOrderIdAndStatus(order.getOrderId(), FulfilmentStatus.New).size();
            itemsToOrder.put(order.getOrderId(), count);
            loadedOrders.put(order.getOrderId(), order);
            return count > 0;
        };
        List<OrderIndexEntry> group = isSuperAdmin()
                ? fulfilmentQueue.pickFulfilmentGroup(skipped, fulfilmentCriteria)
                : fulfilmentQueue.pickFulfilmentGroup(getStoreId(), skipped, fulfilmentCriteria);

        model.addAttribute("page", fulfilmentQueuePageFactory.build(isSuperAdmin(), skipped, group, loadedOrders,
                itemsToOrder, LocalDate.now(), locale));
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
        if (selectedOrders.isEmpty()) {
            // a form sent with nothing ticked (no JavaScript to disable the buttons) would open an empty selection page
            return "redirect:/dashboard/fulfilment/queue";
        }
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
        if (selectedOrders.isEmpty()) {
            // a form sent with nothing ticked (no JavaScript to disable the buttons) would open an empty selection page
            return "redirect:/dashboard/fulfilment/queue";
        }
        return renderManualFulfilmentPage(storeId, selectedOrders, pathSelector, onlyWithProfit, onlyMultiOrder, onlyLocalSuppliers, orderByOrder, model);
    }

    private String renderManualFulfilmentPage(String storeId, List<String> selectedOrders, String pathSelector, boolean onlyWithProfit, boolean onlyMultiOrder, boolean onlyLocalSuppliers, boolean orderByOrder, Model model) {
        return renderManualFulfilmentPage(storeId, selectedOrders, pathSelector, onlyWithProfit, onlyMultiOrder, onlyLocalSuppliers, orderByOrder, Map.of(), model);
    }

    private String renderManualFulfilmentPage(String storeId, List<String> selectedOrders, String pathSelector, boolean onlyWithProfit, boolean onlyMultiOrder, boolean onlyLocalSuppliers, boolean orderByOrder, Map<String, Double> committedSuppliers, Model model) {
        List<String> orders = orderByOrder ? sortByItemsToOrder(selectedOrders) : selectedOrders;
        List<String> ordersToFulfil = orderByOrder && !orders.isEmpty() ? List.of(orders.get(0)) : orders;
        FulfilmentForm fulfilmentForm = manualOrderFulfilment.init(storeId, ordersToFulfil, pathSelector, onlyWithProfit, onlyMultiOrder, onlyLocalSuppliers);
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
        model.addAttribute("supplierLabels", supplierLabels.forStoreId(storeId));

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
        return renderManualFulfilmentPage(getStoreId(), form.getSelectedOrders(), form.getPathSelector(), form.isOnlyWithProfit(), form.isOnlyMultiOrder(), form.isOnlyLocalSuppliers(), form.isOrderByOrder(), model);
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
        return renderManualFulfilmentPage(storeId, form.getSelectedOrders(), form.getPathSelector(), form.isOnlyWithProfit(), form.isOnlyMultiOrder(), form.isOnlyLocalSuppliers(), form.isOrderByOrder(), model);
    }
}
