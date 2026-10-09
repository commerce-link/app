package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
class FulfilmentController extends BaseController {

    private final OrderItemsRepository orderItemsRepository;

    private final FulfilmentQueue fulfilmentQueue;

    private final ManualOrderFulfilment manualOrderFulfilment;

    private final SupplierLabels supplierLabels;

    private final FulfilmentQueuePageFactory fulfilmentQueuePageFactory;

    private final FulfilmentSelectPageFactory fulfilmentSelectPageFactory;

    private final MessageSource messages;

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
        FulfilmentCommit saved = manualOrderFulfilment.commit(getStoreId(), form);
        return nextOrderOrQueue(getStoreId(), form, saved, model, locale, redirect);
    }

    @PostMapping("/dashboard/orders/fulfilment/skip")
    @PreAuthorize("hasRole('ADMIN')")
    public String skipFulfilmentOrder(@ModelAttribute FulfilmentForm form, Model model, Locale locale, RedirectAttributes redirect) {
        return nextOrderOrQueue(getStoreId(), form, null, model, locale, redirect);
    }

    @PostMapping("/dashboard/orders/fulfilment/commitAndContinue")
    @PreAuthorize("hasRole('ADMIN')")
    public String commitAndContinueFulfilmentForm(@ModelAttribute FulfilmentForm form, Model model, Locale locale,
                                                  RedirectAttributes redirect) {
        FulfilmentCommit saved = manualOrderFulfilment.commit(getStoreId(), form);
        return continueOrFinish(getStoreId(), form, saved, model, locale, redirect);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/fulfilment/commit")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String commitFulfilmentFormForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute FulfilmentForm form,
                                                    Model model, Locale locale, RedirectAttributes redirect) {
        FulfilmentCommit saved = manualOrderFulfilment.commit(storeId, form);
        return nextOrderOrQueue(storeId, form, saved, model, locale, redirect);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/fulfilment/skip")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String skipFulfilmentOrderForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute FulfilmentForm form,
                                                   Model model, Locale locale, RedirectAttributes redirect) {
        return nextOrderOrQueue(storeId, form, null, model, locale, redirect);
    }

    @PostMapping("/dashboard/store/{storeId}/orders/fulfilment/commitAndContinue")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String commitAndContinueFulfilmentFormForSuperAdmin(@PathVariable("storeId") String storeId,
                                                               @ModelAttribute FulfilmentForm form, Model model, Locale locale,
                                                               RedirectAttributes redirect) {
        FulfilmentCommit saved = manualOrderFulfilment.commit(storeId, form);
        return continueOrFinish(storeId, form, saved, model, locale, redirect);
    }

    /**
     * "Commit and pick the rest": when that commit left nothing to pick, the operator who just saved should get the
     * saved notice and the queue, not a page claiming someone else finished the selection.
     */
    private String continueOrFinish(String storeId, FulfilmentForm committed, FulfilmentCommit saved, Model model, Locale locale,
                                    RedirectAttributes redirect) {
        String view = render(storeId, Selection.of(committed), model, locale);
        FulfilmentForm rest = (FulfilmentForm) model.getAttribute("form");
        if (rest.getEntries().isEmpty() && rest.getUnmatched().isEmpty()) {
            return nextOrderOrQueue(storeId, committed, saved, model, locale, redirect);
        }
        return view;
    }

    /**
     * The next order of the one-at-a-time mode, or the queue the operator came from. The address is built here from
     * the skip state: the form's redirectUrl is never followed, so a forged or stale form cannot send the browser
     * elsewhere (spec B1). {@code saved} is null after a skip.
     */
    private String nextOrderOrQueue(String storeId, FulfilmentForm form, FulfilmentCommit saved, Model model, Locale locale,
                                    RedirectAttributes redirect) {
        if (form.isOrderByOrder() && form.hasRemainingOrders()) {
            Map<String, Double> committed = new LinkedHashMap<>(form.getCommittedSuppliers() == null ? Map.of() : form.getCommittedSuppliers());
            form.getAcceptedValueByProvider().forEach((provider, value) -> committed.merge(provider, value, Double::sum));
            return render(storeId, Selection.of(form).next(form.getRemainingOrders(), committed), model, locale);
        }
        if (saved != null && saved.isEmpty()) {
            OrderFlash.warning(redirect, messages.getMessage("fulfilment.select.saved.none", null, locale));
        } else if (saved != null) {
            OrderFlash.savedWithLink(redirect, savedText(saved, form.isOrderByOrder(), locale), pendingDeliveries(storeId),
                    messages.getMessage("fulfilment.select.saved.link", null, locale));
        }
        return "redirect:" + Selection.of(form).skipped().queueHref();
    }

    private String savedText(FulfilmentCommit saved, boolean orderByOrder, Locale locale) {
        String key = orderByOrder ? "fulfilment.select.saved.last" : "fulfilment.select.saved";
        return messages.getMessage(key, new Object[]{saved.fromSuppliers(), saved.fromWarehouse()}, locale);
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
