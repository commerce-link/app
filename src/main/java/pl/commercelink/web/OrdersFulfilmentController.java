package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderIndexEntry;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.fulfilment.*;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.web.fulfilment.FulfilmentQueuePageFactory;
import pl.commercelink.web.fulfilment.FulfilmentSelectPageFactory;
import pl.commercelink.web.fulfilment.SkippedGroups;
import pl.commercelink.web.orders.OrderFlash;

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

    @Autowired
    private FulfilmentSelectPageFactory fulfilmentSelectPageFactory;

    @Autowired
    private MessageSource messages;

    @GetMapping("/dashboard/fulfilment/queue")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public String fulfilmentQueue(@RequestParam(value = "orderIds", required = false) List<String> orderIdsParam,
                                  @RequestParam(value = "skippedGroups", required = false) String skippedGroups,
                                  Model model, Locale locale) {
        SkippedGroups skippedOrders = SkippedGroups.from(orderIdsParam == null ? List.of() : orderIdsParam, skippedGroups);
        List<String> skipped = new ArrayList<>(skippedOrders.orderIds());

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

        model.addAttribute("page", fulfilmentQueuePageFactory.build(isSuperAdmin(), skippedOrders, group, loadedOrders,
                itemsToOrder, LocalDate.now(), locale));
        return "fulfilment-queue";
    }

    @PostMapping("/dashboard/orders/fulfilment")
    @PreAuthorize("hasRole('ADMIN')")
    public String initiateMultiOrderManualFulfilment(
            @RequestParam(value = "selectedOrders", defaultValue = "") List<String> selectedOrders,
            @RequestParam(value = "pathSelector", defaultValue = "default") String pathSelector,
            @RequestParam(value = "onlyWithProfit", defaultValue = "false") boolean onlyWithProfit,
            @RequestParam(value = "onlyMultiOrder", defaultValue = "false") boolean onlyMultiOrder,
            @RequestParam(value = "onlyLocalSuppliers", defaultValue = "false") boolean onlyLocalSuppliers,
            @RequestParam(value = "orderByOrder", defaultValue = "false") boolean orderByOrder,
            @RequestParam(value = "skippedOrderIds", required = false) List<String> skippedOrderIds,
            @RequestParam(value = "skippedGroups", required = false) String skippedGroups,
            Model model, Locale locale) {
        return start(getStoreId(), selectedOrders, pathSelector, onlyWithProfit, onlyMultiOrder, onlyLocalSuppliers,
                orderByOrder, skippedOrderIds, skippedGroups, model, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/fulfilment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String initiateMultiOrderManualFulfilmentForSuperAdmin(
            @PathVariable("storeId") String storeId,
            @RequestParam(value = "selectedOrders", defaultValue = "") List<String> selectedOrders,
            @RequestParam(value = "pathSelector", defaultValue = "default") String pathSelector,
            @RequestParam(value = "onlyWithProfit", defaultValue = "false") boolean onlyWithProfit,
            @RequestParam(value = "onlyMultiOrder", defaultValue = "false") boolean onlyMultiOrder,
            @RequestParam(value = "onlyLocalSuppliers", defaultValue = "false") boolean onlyLocalSuppliers,
            @RequestParam(value = "orderByOrder", defaultValue = "false") boolean orderByOrder,
            @RequestParam(value = "skippedOrderIds", required = false) List<String> skippedOrderIds,
            @RequestParam(value = "skippedGroups", required = false) String skippedGroups,
            Model model, Locale locale) {
        return start(storeId, selectedOrders, pathSelector, onlyWithProfit, onlyMultiOrder, onlyLocalSuppliers,
                orderByOrder, skippedOrderIds, skippedGroups, model, locale);
    }

    private String start(String storeId, List<String> selectedOrders, String pathSelector, boolean onlyWithProfit,
                         boolean onlyMultiOrder, boolean onlyLocalSuppliers, boolean orderByOrder,
                         List<String> skippedOrderIds, String skippedGroups, Model model, Locale locale) {
        SkippedGroups skipped = SkippedGroups.from(skippedOrderIds == null ? List.of() : skippedOrderIds, skippedGroups);
        if (selectedOrders.isEmpty()) {
            // a form sent with nothing ticked (no JavaScript to disable the buttons) would open an empty selection page
            return "redirect:" + skipped.queueHref();
        }
        Selection selection = new Selection(selectedOrders, pathSelector, onlyWithProfit, onlyMultiOrder, onlyLocalSuppliers,
                orderByOrder, Map.of(), skipped, selectedOrders.size());
        return render(storeId, selection, model, locale);
    }

    /** What the selection page needs to be rebuilt: the queue's choice, the earlier steps and the queue to return to. */
    private record Selection(List<String> orders, String pathSelector, boolean onlyWithProfit, boolean onlyMultiOrder,
                             boolean onlyLocalSuppliers, boolean orderByOrder, Map<String, Double> committedSuppliers,
                             SkippedGroups skipped, int orderCountAtStart) {

        static Selection of(FulfilmentForm form) {
            List<String> orders = form.getSelectedOrders() == null ? List.of() : form.getSelectedOrders();
            return new Selection(orders, form.getPathSelector(), form.isOnlyWithProfit(), form.isOnlyMultiOrder(),
                    form.isOnlyLocalSuppliers(), form.isOrderByOrder(),
                    form.getCommittedSuppliers() == null ? Map.of() : form.getCommittedSuppliers(),
                    SkippedGroups.from(form.getSkippedOrderIds() == null ? List.of() : form.getSkippedOrderIds(), form.getSkippedGroups()),
                    form.getOrderCountAtStart() > 0 ? form.getOrderCountAtStart() : orders.size());
        }

        Selection next(List<String> remaining, Map<String, Double> committed) {
            return new Selection(remaining, pathSelector, onlyWithProfit, onlyMultiOrder, onlyLocalSuppliers, true,
                    committed, skipped, orderCountAtStart);
        }
    }

    private String render(String storeId, Selection selection, Model model, Locale locale) {
        List<String> orders = selection.orderByOrder() ? sortByItemsToOrder(selection.orders()) : selection.orders();
        List<String> ordersToFulfil = selection.orderByOrder() && !orders.isEmpty() ? List.of(orders.get(0)) : orders;
        FulfilmentForm form = manualOrderFulfilment.init(storeId, ordersToFulfil, selection.pathSelector(),
                selection.onlyWithProfit(), selection.onlyMultiOrder(), selection.onlyLocalSuppliers());
        form.setSelectedOrders(orders);
        form.setPathSelector(selection.pathSelector());
        form.setOnlyWithProfit(selection.onlyWithProfit());
        form.setOnlyMultiOrder(selection.onlyMultiOrder());
        form.setOnlyLocalSuppliers(selection.onlyLocalSuppliers());
        form.setOrderByOrder(selection.orderByOrder());
        form.setCommittedSuppliers(new LinkedHashMap<>(selection.committedSuppliers()));
        form.setSkippedOrderIds(selection.skipped().orderIds());
        form.setSkippedGroups(selection.skipped().sizesParam());
        form.setOrderCountAtStart(selection.orderCountAtStart());

        SupplierLabelMap labels = supplierLabels.forStoreId(storeId);
        model.addAttribute("form", form);
        model.addAttribute("supplierLabels", labels);
        model.addAttribute("page", fulfilmentSelectPageFactory.forOrders(form, storeId, isSuperAdmin(), labels, LocalDate.now(), locale));
        return "fulfilment";
    }

    @PostMapping("/dashboard/orders/fulfilment/commit")
    @PreAuthorize("hasRole('ADMIN')")
    public String commitFulfilmentForm(@ModelAttribute FulfilmentForm form, Model model, Locale locale, RedirectAttributes redirect) {
        manualOrderFulfilment.commit(getStoreId(), form);
        return nextOrderOrQueue(getStoreId(), form, true, model, locale, redirect);
    }

    @PostMapping("/dashboard/orders/fulfilment/skip")
    @PreAuthorize("hasRole('ADMIN')")
    public String skipFulfilmentOrder(@ModelAttribute FulfilmentForm form, Model model, Locale locale, RedirectAttributes redirect) {
        return nextOrderOrQueue(getStoreId(), form, false, model, locale, redirect);
    }

    @PostMapping("/dashboard/orders/fulfilment/commitAndContinue")
    @PreAuthorize("hasRole('ADMIN')")
    public String commitAndContinueFulfilmentForm(@ModelAttribute FulfilmentForm form, Model model, Locale locale) {
        manualOrderFulfilment.commit(getStoreId(), form);
        return render(getStoreId(), Selection.of(form), model, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/fulfilment/commit")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String commitFulfilmentFormForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute FulfilmentForm form,
                                                    Model model, Locale locale, RedirectAttributes redirect) {
        manualOrderFulfilment.commit(storeId, form);
        return nextOrderOrQueue(storeId, form, true, model, locale, redirect);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/fulfilment/skip")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String skipFulfilmentOrderForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute FulfilmentForm form,
                                                   Model model, Locale locale, RedirectAttributes redirect) {
        return nextOrderOrQueue(storeId, form, false, model, locale, redirect);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/fulfilment/commitAndContinue")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String commitAndContinueFulfilmentFormForSuperAdmin(@PathVariable("storeId") String storeId,
                                                               @ModelAttribute FulfilmentForm form, Model model, Locale locale) {
        manualOrderFulfilment.commit(storeId, form);
        return render(storeId, Selection.of(form), model, locale);
    }

    /**
     * The next order of the one-at-a-time mode, or the queue the operator came from. The address is built here from
     * the skip state: the form's redirectUrl is never followed, so a forged or stale form cannot send the browser
     * elsewhere (spec B1).
     */
    private String nextOrderOrQueue(String storeId, FulfilmentForm form, boolean saved, Model model, Locale locale,
                                    RedirectAttributes redirect) {
        if (form.isOrderByOrder() && form.hasRemainingOrders()) {
            Map<String, Double> committed = new LinkedHashMap<>(form.getCommittedSuppliers() == null ? Map.of() : form.getCommittedSuppliers());
            form.getAcceptedValueByProvider().forEach((provider, value) -> committed.merge(provider, value, Double::sum));
            return render(storeId, Selection.of(form).next(form.getRemainingOrders(), committed), model, locale);
        }
        if (saved) {
            OrderFlash.savedWithLink(redirect, savedText(form, locale), pendingDeliveries(storeId),
                    messages.getMessage("fulfilment.select.saved.link", null, locale));
        }
        return "redirect:" + Selection.of(form).skipped().queueHref();
    }

    private String savedText(FulfilmentForm form, Locale locale) {
        long fromSuppliers = 0;
        long fromWarehouse = 0;
        for (FulfilmentGroup group : form.getEntries()) {
            if (!group.isAccepted() || group.getAllocations() == null) {
                continue;
            }
            if (SupplierRegistry.WAREHOUSE.equals(group.getSource().getProvider())) {
                fromWarehouse += group.getAllocations().size();
            } else {
                fromSuppliers += group.getAllocations().size();
            }
        }
        String key = form.isOrderByOrder() ? "fulfilment.select.saved.last" : "fulfilment.select.saved";
        return messages.getMessage(key, new Object[]{fromSuppliers, fromWarehouse}, locale);
    }

    private String pendingDeliveries(String storeId) {
        return isSuperAdmin() ? "/dashboard/store/" + storeId + "/deliveries/preview" : "/dashboard/deliveries/preview";
    }

    private List<String> sortByItemsToOrder(List<String> selectedOrders) {
        Map<String, Integer> counts = selectedOrders.stream()
                .collect(Collectors.toMap(orderId -> orderId, orderId -> orderItemsRepository.findByOrderIdAndStatus(orderId, FulfilmentStatus.New).size(), (a, b) -> a));
        return selectedOrders.stream()
                .sorted(Comparator.comparing(counts::get).reversed())
                .toList();
    }
}
