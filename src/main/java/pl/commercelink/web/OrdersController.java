package pl.commercelink.web;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.ShipmentCarrierOptions;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.invoicing.InvoiceCreationEventPublisher;
import pl.commercelink.orders.*;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.exceptions.OrderFilterException;

import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFilterField;
import pl.commercelink.orders.filters.services.OrderFiltersService;

import pl.commercelink.orders.filters.ShippingDue;
import pl.commercelink.orders.filters.services.ListOrderFiltersView;
import pl.commercelink.orders.fulfilment.ExternalSupplierBinding;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.orders.imports.BasketOrderImporter;
import pl.commercelink.orders.pos.PosOrderCreator;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.PricelistFinder;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.ShipmentCancelService;
import pl.commercelink.shipping.ShipmentTrackingSubscriber;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.starter.dynamodb.OptimisticLockingExhaustedException;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.GoodsOutEventPublisher;
import pl.commercelink.web.dtos.AddItemsForm;
import pl.commercelink.web.dtos.AddPaymentForm;
import pl.commercelink.web.dtos.RoutedSupplierView;
import pl.commercelink.web.dtos.ClientDataDto;
import pl.commercelink.web.dtos.OrderFilterForm;
import pl.commercelink.web.dtos.OrderItemsForm;
import pl.commercelink.web.dtos.SplitGroupForm;
import pl.commercelink.web.dtos.SplitGroupPreviewDto;
import pl.commercelink.web.orders.OrderListQuery;
import pl.commercelink.web.settings.ConfirmAction;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import pl.commercelink.inventory.deliveries.DropshipItemLookup;
import pl.commercelink.inventory.supplier.SupplierChoice;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Controller
public class OrdersController extends BaseController {

    @Autowired
    private ShipmentCarrierOptions shipmentCarrierOptions;

    @Autowired
    private Inventory inventory;

    @Autowired
    private ProductCatalogRepository productCatalogRepository;

    @Autowired
    private StoreCategories storeCategories;

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private OrderItemsRepository orderItemsRepository;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private SupplierLabels supplierLabels;

    @Autowired
    private SupplierChoice supplierChoice;

    @Autowired
    private BasketsRepository basketsRepository;

    @Autowired
    private OrdersManager ordersManager;

    @Autowired
    private OrderLifecycle orderLifecycle;

    @Autowired
    private PricelistFinder pricelistFinder;

    @Autowired
    private InvoiceCreationEventPublisher invoiceCreationEventPublisher;

    @Autowired
    private BasketOrderImporter basketOrderImporter;

    @Autowired
    private PosOrderCreator posOrderCreator;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private OrderEventsRepository orderEventsRepository;

    @Autowired
    private ShipmentCancelService shipmentCancelService;

    @Autowired
    private GoodsOutEventPublisher goodsOutEventPublisher;

    @Autowired
    private TaxonomyCache taxonomyCache;

    @Autowired
    private OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    @Autowired
    private DropshipItemLookup dropshipItemLookup;
    @Autowired
    private ShipmentTrackingSubscriber shipmentTrackingSubscriber;
    @Value("${app.domain}")
    private String appDomain;

    @Autowired
    private OrderFiltersService orderFilters;

    @Autowired
    private OrderListService orderListService;

    private static final Set<String> LIST_PARAMS = Set.of("status", "filterId", "focus", "q", "sort", "dir", "page");

    @GetMapping("/dashboard/orders")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String orders(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        Optional<String> legacy = OrderListQuery.legacyRedirect(params);
        if (legacy.isPresent()) {
            return "redirect:" + legacy.get();
        }
        OrderListQuery query = OrderListQuery.parse(params);
        ListOrderFiltersView filters = orderFilters.list(actor());
        // A bare entry (the menu link) opens on the user's starred filter; any list parameter — including an
        // empty filterId= from "Wyczyść filtr" — means the user already chose a view (spec §2). Parameters the
        // list does not own (lang=) do not count.
        boolean bareEntry = params.keySet().stream().noneMatch(LIST_PARAMS::contains);
        if (bareEntry && filters.defaultFilter().isPresent()) {
            OrderFilter starred = filters.defaultFilter().get();
            OrderListQuery target = query.withFilterId(starred.getId()).withStatus(statusOf(starred).orElse(null));
            return "redirect:" + target.href();
        }
        addListAttributes(model, query, locale);
        return "orders/list";
    }

    @GetMapping("/dashboard/orders/list")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String ordersList(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addListAttributes(model, OrderListQuery.parse(params), locale);
        return "orders/list :: results";
    }

    private void addListAttributes(Model model, OrderListQuery query, Locale locale) {
        addFilterFormAttributes(model, query.returnTo(), locale);
        model.addAttribute("page", orderListService.page(actor(), query, LocalDate.now(), locale));
    }

    private static Optional<OrderStatus> statusOf(OrderFilter filter) {
        return filter.getConditions().stream()
                .filter(c -> c.getField() == OrderFilterField.Status)
                .map(c -> c.getField().normalize(c.getValue()))
                .flatMap(value -> Arrays.stream(OrderStatus.values()).filter(s -> s.name().equalsIgnoreCase(value)))
                .findFirst();
    }

    private static final String FETCH = "fetch";
    static final String FILTERS_PATH = "/dashboard/orders/filters";
    /** The hidden "dialog" field of the filter subpage's form: a rejection re-renders that page. */
    private static final String PAGE_FORM = "page";

    @PostMapping("/dashboard/orders/filters")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String createOrderFilter(OrderFilterForm form,
                                    @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                    RedirectAttributes redirectAttributes, Model model, Locale locale,
                                    HttpServletResponse response) {
        return filterAction(requestedWith, form.getReturnTo(), redirectAttributes, model, locale, response, form, null, () -> {
            OrderFilter created = orderFilters.create(actor(), form.isSharedWithStore(), form.getLabel(),
                    form.toConditions(), form.isMakeDefault());
            if (!form.isMakeDefault()) {
                return safeReturnTo(form.getReturnTo());
            }
            OrderListQuery target = parseReturnTo(safeReturnTo(form.getReturnTo())).withFilterId(created.getId());
            return statusOf(created).map(target::withStatus).orElse(target).href();
        });
    }

    @PostMapping("/dashboard/orders/filters/update")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateOrderFilter(@RequestParam String filterId, OrderFilterForm form,
                                    @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                    RedirectAttributes redirectAttributes, Model model, Locale locale,
                                    HttpServletResponse response) {
        return filterAction(requestedWith, form.getReturnTo(), redirectAttributes, model, locale, response, form, filterId, () -> {
            orderFilters.update(actor(), filterId, form.isSharedWithStore(), form.getLabel(), form.toConditions());
            return safeReturnTo(form.getReturnTo());
        });
    }

    @PostMapping("/dashboard/orders/filters/delete")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String deleteOrderFilter(@RequestParam String filterId, @RequestParam(required = false) String returnTo,
                                    @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                    RedirectAttributes redirectAttributes, Model model, Locale locale,
                                    HttpServletResponse response) {
        return filterAction(requestedWith, returnTo, redirectAttributes, model, locale, response, () -> {
            orderFilters.delete(actor(), filterId);
            String target = safeReturnTo(returnTo);
            OrderListQuery list = parseReturnTo(listOf(target));
            String listBack = filterId.equals(list.filterId()) ? list.withFilterId(null).href() : list.href();
            return target.startsWith(FILTERS_PATH) ? filtersPage(listBack) : listBack;
        });
    }

    /** The no-JS confirmation page for the delete link (data-cl-confirm opens the shared dialog with JavaScript). */
    @GetMapping("/dashboard/orders/filters/delete")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmDeleteOrderFilter(@RequestParam String filterId, @RequestParam(required = false) String returnTo,
                                           Locale locale, Model model) {
        OrderFilter filter = orderFilters.list(actor()).byId(filterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String back = safeReturnTo(returnTo);
        String actionPath = "/dashboard/orders/filters/delete?filterId=" + URLEncoder.encode(filter.getId(), StandardCharsets.UTF_8)
                + "&returnTo=" + URLEncoder.encode(back, StandardCharsets.UTF_8);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("orders.filters.delete.title", new Object[]{filter.getLabel()}, locale),
                messageSource.getMessage("orders.filters.delete.message", null, locale),
                messageSource.getMessage("orders.filters.delete.action", null, locale),
                actionPath, back));
        // the way back names where "Anuluj" goes: the management page, or the list for an older link
        model.addAttribute("backLabel", messageSource.getMessage(back.startsWith(FILTERS_PATH) ? "orders.filters.edit.back"
                : "orders.filters.page.back", null, locale));
        return "settings-confirm";
    }

    @PostMapping("/dashboard/orders/filters/{filterId}/default")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String setDefaultFilter(@PathVariable String filterId, @RequestParam(required = false) String returnTo,
                                   @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                   RedirectAttributes redirectAttributes, Model model, Locale locale,
                                   HttpServletResponse response) {
        return filterAction(requestedWith, returnTo, redirectAttributes, model, locale, response, () -> {
            orderFilters.setDefault(actor(), filterId);
            return safeReturnTo(returnTo);
        });
    }

    @PostMapping("/dashboard/orders/filters/default/clear")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String clearDefaultFilter(@RequestParam(required = false) String returnTo,
                                     @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                     RedirectAttributes redirectAttributes, Model model, Locale locale,
                                     HttpServletResponse response) {
        return filterAction(requestedWith, returnTo, redirectAttributes, model, locale, response, () -> {
            orderFilters.clearDefault(actor());
            return safeReturnTo(returnTo);
        });
    }

    /**
     * Filter management as its own page (design system: a list of records in a card, editing on a subpage). {@code returnTo}
     * is the list address the user came from; "‹ Zamówienia" goes back to it, and every form on this page and its subpages
     * returns here, to {@link #filtersPage(String)}.
     */
    @GetMapping(FILTERS_PATH)
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String orderFiltersPage(@RequestParam(required = false) String returnTo, Locale locale, Model model) {
        String list = safeListReturnTo(returnTo);
        addFilterFormAttributes(model, filtersPage(list), locale);
        model.addAttribute("listHref", list);
        return "orders/filters";
    }

    /** "Nowy filtr" from the management page: the empty form as a subpage. */
    @GetMapping(FILTERS_PATH + "/add")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addOrderFilterPage(@RequestParam(required = false) String returnTo, Locale locale, Model model) {
        addFilterEditAttributes(model, safeListReturnTo(returnTo), null, new OrderFilterForm(), locale);
        return "orders/filter-edit";
    }

    /** "Edytuj" from the management page: the filter's form as a subpage; a filter the user cannot see is a 404. */
    @GetMapping(FILTERS_PATH + "/{filterId}/edit")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String editOrderFilterPage(@PathVariable String filterId, @RequestParam(required = false) String returnTo,
                                      Locale locale, Model model) {
        ListOrderFiltersView filters = orderFilters.list(actor());
        OrderFilter filter = filters.byId(filterId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean shared = filters.sharedWithStore().stream().anyMatch(f -> f.getId().equals(filterId));
        if (shared && !isAdmin()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        addFilterEditAttributes(model, safeListReturnTo(returnTo), filterId, formOf(filter, shared), locale);
        return "orders/filter-edit";
    }

    private void addFilterEditAttributes(Model model, String list, String filterId, OrderFilterForm form, Locale locale) {
        addFilterFormAttributes(model, filtersPage(list), locale);
        model.addAttribute("listHref", list);
        model.addAttribute("filterId", filterId);
        model.addAttribute("filterForm", form);
        model.addAttribute("formAction", filterId == null ? FILTERS_PATH : FILTERS_PATH + "/update");
        model.addAttribute("pageTitle", filterId == null
                ? messageSource.getMessage("orders.filters.new", null, locale)
                : messageSource.getMessage("orders.filters.edit.title", new Object[]{form.getLabel()}, locale));
    }

    private static OrderFilterForm formOf(OrderFilter filter, boolean shared) {
        Map<String, String> byField = filter.getConditionsByField();
        OrderFilterForm form = new OrderFilterForm();
        form.setLabel(filter.getLabel());
        form.setSharedWithStore(shared);
        form.setStatus(byField.get(OrderFilterField.Status.name()));
        form.setShipmentType(byField.get(OrderFilterField.ShipmentType.name()));
        form.setPaymentSource(byField.get(OrderFilterField.PaymentSource.name()));
        form.setShippingDue(byField.get(OrderFilterField.ShippingDue.name()));
        form.setSourceName(byField.get(OrderFilterField.SourceName.name()));
        form.setShippingPostalCode(byField.get(OrderFilterField.ShippingPostalCode.name()));
        return form;
    }

    /** "Save this view" as a page, for browsers without JavaScript. */
    @GetMapping("/dashboard/orders/filters/new")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String newOrderFilterPage(@RequestParam(required = false) String returnTo, Locale locale, Model model) {
        String back = safeReturnTo(returnTo);
        addFilterFormAttributes(model, back, locale);
        model.addAttribute("page", orderListService.page(actor(), parseReturnTo(back), LocalDate.now(), locale));
        return "orders/filter-new";
    }

    /**
     * Runs a filter change and answers the way the caller can use: a fetch gets the dialog body (200, or 422 with the
     * rejection), a plain form gets a redirect with the rejection as a flash for the list page.
     */
    private String filterAction(String requestedWith, String returnTo, RedirectAttributes redirectAttributes, Model model,
                                Locale locale, HttpServletResponse response, Supplier<String> action) {
        return filterAction(requestedWith, returnTo, redirectAttributes, model, locale, response, null, null, action);
    }

    /**
     * Same as above, but for the create/update endpoints: on a rejection the submitted {@code form} (and, for an
     * update, the {@code filterId} being edited) go back into what the user was looking at, so nothing typed is lost —
     * the filter subpage (hidden {@code dialog=page}, a 422 page) or the "save this view" dialog (fetch, a 422 body).
     */
    private String filterAction(String requestedWith, String returnTo, RedirectAttributes redirectAttributes, Model model,
                                Locale locale, HttpServletResponse response, OrderFilterForm form, String filterId,
                                Supplier<String> action) {
        String rejection = null;
        String target = safeReturnTo(returnTo);
        try {
            target = action.get();
        } catch (OrderFilterException rejected) {
            rejection = messageSource.getMessage(rejected.getMessageKey(), rejected.getMessageArguments(), locale);
        } catch (OptimisticLockingExhaustedException e) {
            rejection = messageSource.getMessage("orders.filters.error.conflict", null, locale);
        }
        if (rejection != null && form != null && PAGE_FORM.equals(form.getDialog())) {
            // the filter subpage: show the rejection above the form and keep what the user typed
            addFilterEditAttributes(model, listOf(safeReturnTo(returnTo)), filterId, form, locale);
            model.addAttribute("filterError", rejection);
            response.setStatus(422);
            return "orders/filter-edit";
        }
        if (FETCH.equals(requestedWith)) {
            addFilterFormAttributes(model, target, locale);
            if (rejection != null) {
                model.addAttribute("filterError", rejection);
                response.setStatus(422);
                if (form != null) {
                    // the only form still posted with fetch is the "save this view" dialog
                    model.addAttribute("filterForm", form);
                    model.addAttribute("page", orderListService.page(actor(), parseReturnTo(target), LocalDate.now(), locale));
                    return "orders/filters :: saveViewBody";
                }
            } else {
                model.addAttribute("redirectTo", target);
            }
            return "orders/filters :: redirect";
        }
        if (rejection != null) {
            redirectAttributes.addFlashAttribute("filterError", rejection);
        }
        return "redirect:" + target;
    }

    private void addFilterFormAttributes(Model model, String returnTo, Locale locale) {
        model.addAttribute("filters", orderFilters.list(actor()));
        model.addAttribute("canManageStoreFilters", isAdmin());
        model.addAttribute("statuses", Arrays.stream(OrderStatus.values()).toList());
        model.addAttribute("shipmentTypes", ShipmentType.values());
        model.addAttribute("paymentSources", PaymentSource.values());
        model.addAttribute("shippingDueOptions", ShippingDue.values());
        model.addAttribute("marketplaces", connectedMarketplaceNames());
        model.addAttribute("returnTo", returnTo);
    }

    /**
     * Only the list's own address, or the filter management page carrying one, may be returned to (spec §7.2);
     * anything else falls back to the bare list.
     */
    static String safeReturnTo(String returnTo) {
        if (returnTo != null && (returnTo.equals(FILTERS_PATH) || returnTo.startsWith(FILTERS_PATH + "?"))) {
            return filtersPage(listOf(returnTo));
        }
        return safeListReturnTo(returnTo);
    }

    /** The list address only: the management page's own back link and returnTo parameter. */
    static String safeListReturnTo(String returnTo) {
        if (returnTo == null || returnTo.length() > 300) {
            return OrderListQuery.PATH;
        }
        boolean ownPath = returnTo.equals(OrderListQuery.PATH) || returnTo.startsWith(OrderListQuery.PATH + "?");
        return ownPath && !returnTo.contains("//") ? returnTo : OrderListQuery.PATH;
    }

    /** The management page that returns to the given list address. */
    static String filtersPage(String listReturnTo) {
        return FILTERS_PATH + "?returnTo=" + URLEncoder.encode(safeListReturnTo(listReturnTo), StandardCharsets.UTF_8);
    }

    /** The list address a return address leads back to: itself, or the management page's returnTo. */
    static String listOf(String returnTo) {
        if (returnTo != null && (returnTo.equals(FILTERS_PATH) || returnTo.startsWith(FILTERS_PATH + "?"))) {
            List<String> inner = queryOf(returnTo).get("returnTo");
            return safeListReturnTo(inner == null || inner.isEmpty() ? null : inner.get(0));
        }
        return safeListReturnTo(returnTo);
    }

    private static MultiValueMap<String, String> queryOf(String href) {
        return UriComponentsBuilder.fromUriString(href).build().getQueryParams().entrySet().stream()
                .collect(LinkedMultiValueMap::new,
                        (map, e) -> e.getValue().forEach(v -> map.add(e.getKey(), v == null ? "" : URLDecoder.decode(v, StandardCharsets.UTF_8))),
                        LinkedMultiValueMap::addAll);
    }

    /** A malformed address (an unbalanced "%" from a truncated returnTo) falls back to the bare list rather than
     * throwing out of a filter action. */
    private static OrderListQuery parseReturnTo(String href) {
        try {
            return OrderListQuery.parse(queryOf(href));
        } catch (IllegalArgumentException e) {
            return OrderListQuery.parse(queryOf(OrderListQuery.PATH));
        }
    }

    private FilterActor actor() {
        return new FilterActor(getStoreId(), getUserId(), isAdmin());
    }

    private List<String> connectedMarketplaceNames() {
        Store store = storesRepository.findById(getStoreId());
        if (store == null) {
            return List.of();
        }
        return store.getMarketplaces().stream()
                .map(MarketplaceIntegration::getName)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    @GetMapping("/dashboard/orders/new/from-basket")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String submitOrderBasedOnBasket(@RequestParam("basketId") String basketId, Model model) {
        Optional<Basket> basketOpt = basketsRepository.findById(getStoreId(), basketId);
        if (!basketOpt.isPresent()) {
            model.addAttribute("error", "Basket not found");
            return "error";
        }

        Basket basket = basketOpt.get();
        BillingDetails billingDetails = Optional.ofNullable(basket.getBillingDetails())
                .orElseGet(BillingDetails::_default);
        ShippingDetails shippingDetails = Optional.ofNullable(basket.getShippingDetails())
                .orElseGet(ShippingDetails::_default);

        Store store = storesRepository.findById(getStoreId());
        ShipmentType shipmentType = basket.resolveDeliveryOption(store)
                .map(DeliveryOption::getType)
                .orElse(ShipmentType.Courier);

        ClientDataDto form = new ClientDataDto();
        form.setOrderReference(basketId);
        form.setShipmentType(shipmentType);
        form.setBillingDetails(billingDetails);
        form.setShippingDetails(shippingDetails);

        model.addAttribute("form", form);
        model.addAttribute("shipmentTypes", ShipmentType.values());
        model.addAttribute("orderSourceTypes", OrderSourceType.values());
        model.addAttribute("paymentSources", PaymentSource.values());

        return "newOrder_clientDataCollection";
    }

    @PostMapping("/dashboard/orders/new/fulfilment")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String submitOrder(@ModelAttribute ClientDataDto dto) {
        Order order = basketOrderImporter._import(getStoreId(), dto);
        return "redirect:/dashboard/orders/" + order.getOrderId();
    }

    @PostMapping("/dashboard/orders/new/pos")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String createPosOrder(Locale locale, RedirectAttributes redirectAttributes) {
        OperationResult<Order> result = posOrderCreator.create(getStoreId(), locale);
        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(result.getMessage(), null, locale));
            return "redirect:/dashboard/orders";
        }
        return "redirect:/dashboard/orders/" + result.getPayload().getOrderId();
    }

    @GetMapping("/dashboard/orders/{orderId}")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String getOrderDetails(@PathVariable("orderId") String orderId, Model model) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);
        return showOrderDetails(existingOrder, model);
    }

    @GetMapping("/dashboard/store/{storeId}/orders/{orderId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String getOrderDetailsForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId, Model model) {
        Order existingOrder = ordersRepository.findById(storeId, orderId);
        return showOrderDetails(existingOrder, model);
    }

    @PostMapping("/dashboard/orders/{orderId}/add-items")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addOrderItems(@PathVariable String orderId, @ModelAttribute AddItemsForm form) {
        Store store = storesRepository.findById(getStoreId());
        Order order = ordersRepository.findById(getStoreId(), orderId);
        InventoryView inventoryView = inventory.withEnabledSuppliersOnly(getStoreId());

        // every entry is resolved before anything is saved, so an unknown pricelist row rejects the whole batch
        List<OrderItemDraft> drafts = form.entries().stream()
                .map(entry -> entry.isFromPricelist()
                        ? OrderItemDraft.of(pricelistEntry(entry), entry.getQty())
                        : OrderItemDraft.of(inventoryView.findByInventoryKey(entry.inventoryKey()), entry.getQty()))
                .toList();

        ordersManager.addOrderItems(store, order, drafts);
        return "redirect:/dashboard/orders/" + orderId;
    }

    private AvailabilityAndPrice pricelistEntry(AddItemsForm.Entry entry) {
        return pricelistFinder.findByPimId(getStoreId(), entry.getCatalogId(), entry.getPimId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String showOrderDetails(Order order, Model model) {
        return showOrderDetails(order, orderItemsRepository.findByOrderId(order.getOrderId()), model);
    }

    private String resolveTaxonomyName(String mfn) {
        Taxonomy taxonomy = taxonomyCache.findByMfn(mfn);
        return taxonomy != null && taxonomy.name() != null ? taxonomy.name() : "";
    }

    private String showOrderDetails(Order order, List<OrderItem> orderItems, Model model) {
        List<ProductCatalog> catalogs = productCatalogRepository.findAll(order.getStoreId());

        Store store = storesRepository.findById(order.getStoreId());

        List<DocumentType> manualDocumentTypes = order.isB2B()
                ? Arrays.asList(DocumentType.InvoiceVat, DocumentType.InvoiceAdvance, DocumentType.InvoiceFinal)
                : Arrays.asList(DocumentType.Receipt, DocumentType.InvoicePersonal);

        List<OrderItem> serialUpdateItems = orderItems.stream()
                .filter(i -> i.hasOneOfTheStatuses(FulfilmentStatus.Delivered))
                .filter(OrderItem::isProduct)
                .collect(Collectors.toList());

        Map<String, SplitGroupPreviewDto> splitGroupPreviews = orderItems.stream()
                .filter(OrderItem::isNew)
                .filter(OrderItem::isGroup)
                .collect(Collectors.toMap(OrderItem::getItemId, i -> SplitGroupPreviewDto.from(i, this::resolveTaxonomyName)));

        model.addAttribute("order", order);
        model.addAttribute("clientOrderUrl", store.isClientOrderPageEnabled() && !order.hasStatus(OrderStatus.Completed)
                ? order.createClientOrderUrl(appDomain) : null);
        model.addAttribute("routedSupplier", RoutedSupplierView.from(order, store));
        model.addAttribute("orderEvents", orderEventsRepository.findByOrderId(order.getOrderId()));
        model.addAttribute("orderItemsForm", new OrderItemsForm(orderItems));
        model.addAttribute("serialUpdateItems", serialUpdateItems);
        model.addAttribute("splitGroupPreviews", splitGroupPreviews);
        model.addAttribute("orderFinancials", new OrderFinancials(order, orderItems));
        model.addAttribute("orderStatuses", Arrays.stream(OrderStatus.values())
                .filter(status -> (status != OrderStatus.Completed || order.getStatus() == OrderStatus.Completed)
                        && (status != OrderStatus.Cancelled || order.getStatus() == OrderStatus.Cancelled))
                .collect(Collectors.toList()));
        model.addAttribute("orderReviewStatuses", OrderReviewStatus.values());
        model.addAttribute("receiptTypes", manualDocumentTypes);
        model.addAttribute("paymentSources", PaymentSource.values());
        model.addAttribute("pendingPayment", order.getPayments().stream()
                .filter(Payment::isUnsettled)
                .findFirst()
                .orElse(null));
        model.addAttribute("shipmentTypes", ShipmentType.values());
        model.addAttribute("carrierOptions", shipmentCarrierOptions.forOrder(order, store));
        model.addAttribute("fulfilmentStatuses", FulfilmentStatus.values());
        model.addAttribute("fulfilmentTypes", FulfilmentType.values());
        model.addAttribute("isCompletedOrder", order.hasOneOfStatuses(OrderStatus.Completed, OrderStatus.Cancelled) || isSuperAdmin());
        model.addAttribute("isNewOrder", order.getStatus() == OrderStatus.New);
        model.addAttribute("canOrderShipment", !order.getStatus().isOneOf(OrderStatus.New, OrderStatus.Blocked, OrderStatus.Assembly));
        model.addAttribute("canDeleteOrder", order.hasStatus(OrderStatus.New) && orderItems.isEmpty() && !order.isInvoiced());
        model.addAttribute("canCancelOrder", order.canBeCancelled(orderItems));
        boolean canSplitOrder = order.canBeSplit() && !orderItems.isEmpty();
        model.addAttribute("canSplitOrder", canSplitOrder);
        model.addAttribute("fulfilmentTypeLocked", !order.canChangeFulfilmentType(orderItems));
        model.addAttribute("hasWarehouseDocument", order.getDocumentByType(DocumentType.GoodsIssue).isPresent());
        Set<String> dropshipItemIds = dropshipItemLookup.itemIdsInDropshipDeliveries(order.getStoreId(), orderItems);
        boolean hasDropshipItems = !dropshipItemIds.isEmpty();
        model.addAttribute("hasDropshipItems", hasDropshipItems);
        model.addAttribute("hasAvailableItemActions", canSplitOrder || !hasDropshipItems);
        model.addAttribute("hasWarehouseItems", orderItems.stream()
                .filter(OrderItem::isProduct)
                .anyMatch(item -> !dropshipItemIds.contains(item.getItemId())));
        model.addAttribute("hasWarehouseDocumentsEnabled", store.hasDocumentsGenerationEnabled());
        model.addAttribute("isInvoiced", order.isInvoiced());
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        model.addAttribute("isAdmin", isAdmin());

        model.addAttribute("catalogs", catalogs);

        DocumentType nextDocumentToIssue = order.getNextDocumentToIssue().orElse(null);
        model.addAttribute("nextInvoiceToIssue", nextDocumentToIssue);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("canAddDocumentManually", manualDocumentTypes.contains(nextDocumentToIssue));
        model.addAttribute("issuableDocumentTypes", order.getIssuableDocumentTypes());

        SupplierLabelMap labels = supplierLabels.forStore(store);
        model.addAttribute("supplierLabels", labels);
        model.addAttribute("assignableSuppliers", labels.options());

        return "orderDetails";
    }

    @GetMapping("/dashboard/orders/{orderId}/collection")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String getOrderCollectionProtocol(@PathVariable("orderId") String orderId, Model model) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        return renderOrderCollectionProtocol(order, model);
    }

    @GetMapping("/dashboard/store/{storeId}/orders/{orderId}/collection")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String getOrderCollectionProtocolForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId, Model model) {
        Order order = ordersRepository.findById(storeId, orderId);
        return renderOrderCollectionProtocol(order, model);
    }

    private String renderOrderCollectionProtocol(Order order, Model model) {
        Store store = storesRepository.findById(order.getStoreId());
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());

        model.addAttribute("store", store);
        model.addAttribute("orderId", order.getOrderId());
        model.addAttribute("collectedAt", LocalDate.now());
        model.addAttribute("location", "Kraków, PL");
        model.addAttribute("orderItems", orderItems);

        return "orderPersonalCollection";
    }

    @GetMapping("/dashboard/orders/{orderId}/card")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String getOrderCard(@PathVariable("orderId") String orderId, Model model) {
        model.addAttribute("order", ordersRepository.findById(getStoreId(), orderId));
        model.addAttribute("orderItems", orderItemsRepository.findByOrderId(orderId));

        return "orderCard";
    }

    @GetMapping("/dashboard/store/{storeId}/orders/{orderId}/card")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String getOrderCardForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId, Model model) {
        model.addAttribute("order", ordersRepository.findById(storeId, orderId));
        model.addAttribute("orderItems", orderItemsRepository.findByOrderId(orderId));

        return "orderCard";
    }

    @PostMapping("/dashboard/orders/{orderId}/invoicing")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String createInvoice(@PathVariable String orderId, @RequestParam DocumentType documentType, @RequestParam(defaultValue = "false") boolean send, Locale locale, RedirectAttributes redirectAttributes) {
        Order order = ordersRepository.findById(getStoreId(), orderId);

        if (!order.getIssuableDocumentTypes().contains(documentType)) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.no.eligible.invoice.to.create", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        invoiceCreationEventPublisher.publish(order, documentType, send);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("invoice.generation.started", null, locale));

        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/goods-out")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String issueGoodsOut(@PathVariable String orderId, Locale locale, RedirectAttributes redirectAttributes) {
        Order order = ordersRepository.findById(getStoreId(), orderId);

        if (order.getDocumentByType(DocumentType.GoodsIssue).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.goods.issue.already.exists", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        String createdBy = CustomSecurityContext.getLoggedInUser()
                .map(CustomUser::getName)
                .orElse("System");
        goodsOutEventPublisher.publish(order, createdBy);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("goods.issue.generation.started", null, locale));

        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/updateOrderInfo")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateOrderInfo(@PathVariable String orderId, @ModelAttribute("order") Order updatedOrder, RedirectAttributes redirectAttributes, Locale locale) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);

        if (!existingOrder.canTransitionToDelivered(updatedOrder.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.delivered.requires.shipment.data", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        if (updatedOrder.getStatus() == OrderStatus.Completed) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.completed.cannot.be.set.manually", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        if (updatedOrder.getStatus() == OrderStatus.Cancelled && existingOrder.getStatus() != OrderStatus.Cancelled) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.cancelled.cannot.be.set.manually", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        FulfilmentType requestedFulfilmentType = updatedOrder.getFulfilmentType();
        boolean fulfilmentTypeChanged = requestedFulfilmentType != null
                && requestedFulfilmentType != existingOrder.getFulfilmentType();
        if (fulfilmentTypeChanged
                && !existingOrder.canChangeFulfilmentType(orderItemsRepository.findByOrderId(orderId))) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("order.fulfilment.type.locked", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        existingOrder.setStatus(updatedOrder.getStatus());
        existingOrder.setEmailNotificationsEnabled(updatedOrder.isEmailNotificationsEnabled());
        existingOrder.setEstimatedAssemblyAt(updatedOrder.getEstimatedAssemblyAt());
        existingOrder.setEstimatedShippingAt(updatedOrder.getEstimatedShippingAt());
        existingOrder.setPreferredShippingAt(updatedOrder.getPreferredShippingAt());
        existingOrder.setAffiliateId(updatedOrder.getAffiliateId());
        existingOrder.setGclid(updatedOrder.getGclid());
        existingOrder.setComment(updatedOrder.getComment());
        if (fulfilmentTypeChanged) {
            existingOrder.setFulfilmentType(requestedFulfilmentType);
        }
        return save(existingOrder);
    }

    @GetMapping("/dashboard/orders/{orderId}/items/{itemId}")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String getOrderItem(@PathVariable String orderId, @PathVariable String itemId, Model model) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        OrderItem orderItem = orderItemsRepository.findById(orderId, itemId);
        return showOrderItemDetails(order, orderItem, model);
    }

    @PostMapping("/dashboard/orders/{orderId}/items/{itemId}/save")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String saveOrderItem(@PathVariable String orderId, @PathVariable String itemId, @ModelAttribute OrderItem updatedItem, Model model) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(orderId);

        Optional<OrderItem> op = orderItems.stream()
                .filter(i -> i.getItemId().equals(itemId))
                .findFirst();

        if (op.isPresent()) {
            OrderItem orderItem = op.get();

            boolean wasService = orderItem.isService();
            boolean serviceFlagLocked = orderItem.hasSupplierAllocation();
            boolean priceLocked = !order.getDocuments().isEmpty();

            String postedDeliveryId = StringUtils.trimToNull(updatedItem.getDeliveryId());
            boolean deliveryIdChanged = postedDeliveryId != null && !postedDeliveryId.equals(orderItem.getDeliveryId());
            if (deliveryIdChanged) {
                Store store = storesRepository.findById(getStoreId());
                // Same rules as the "assign supplier" modal: a connection identity or a typed name.
                SupplierChoice.Resolution resolution = supplierChoice.resolve(store, postedDeliveryId, null);
                if (!resolution.accepted()) {
                    model.addAttribute("errorMessage", messageSource.getMessage(
                            resolution.errorCode(), resolution.errorArgs(), LocaleContextHolder.getLocale()));
                    return showOrderItemDetails(order, orderItem, model);
                }
                postedDeliveryId = resolution.identity();
                updatedItem.setDeliveryId(postedDeliveryId);
                if (!ExternalSupplierBinding.of(store, List.of(order)).permits(orderId, postedDeliveryId)) {
                    model.addAttribute("errorMessage",
                            messageSource.getMessage("order.item.assign.supplier.routed", null, LocaleContextHolder.getLocale()));
                    return showOrderItemDetails(order, orderItem, model);
                }
            }

            if (StringUtils.isBlank(updatedItem.getCategory())) {
                updatedItem.setCategory(null);
            }
            if (serviceFlagLocked) {
                updatedItem.setService(orderItem.isService());
            }
            if (priceLocked) {
                updatedItem.setPrice(orderItem.getPrice());
            }
            orderItem.update(updatedItem);

            if (!wasService && orderItem.isService()) {
                orderItem.markAsWarehouseFulfilled();
                if (orderItem.getPosition() < PositionGroup.SERVICE_GROUP_START) {
                    orderItem.setPosition(PositionGroup.SERVICE_GROUP_START + orderItem.getPosition());
                }
            } else if (wasService && !orderItem.isService()) {
                if (OrderItem.GENERIC_WAREHOUSE_ORDER_NO.equals(orderItem.getDeliveryId())) {
                    orderItem.setDeliveryId(null);
                    orderItem.setStatus(FulfilmentStatus.New);
                }
                if (orderItem.getPosition() >= PositionGroup.SERVICE_GROUP_START && orderItem.getPosition() < PositionGroup.DELIVERY_POSITION) {
                    orderItem.setPosition(orderItem.getPosition() - PositionGroup.SERVICE_GROUP_START);
                }
            }

            orderItemsRepository.save(orderItem);

            order.setTotalPrice(new OrderFinancials(order, orderItems).getTotalPrice());
            orderLifecycle.update(order, orderItems);
        }

        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/assign-supplier")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String assignSupplier(@PathVariable String orderId, @RequestParam String itemId,
                                 @RequestParam String manufacturerCode, @RequestParam double cost,
                                 @RequestParam String supplier, @RequestParam(required = false) String customSupplier,
                                 Model model, RedirectAttributes redirectAttributes, Locale locale) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        OrderItem orderItem = orderItemsRepository.findById(orderId, itemId);

        if (!orderItem.isReleasable()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("order.item.assign.supplier.blocked", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        Store store = storesRepository.findById(getStoreId());
        SupplierChoice.Resolution resolution = supplierChoice.resolve(store, supplier, customSupplier);
        if (!resolution.accepted()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(resolution.errorCode(), resolution.errorArgs(), locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        supplier = resolution.identity();

        if (!ExternalSupplierBinding.of(store, List.of(order)).permits(orderId, supplier)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("order.item.assign.supplier.routed", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        orderItem.setManufacturerCode(manufacturerCode);
        orderItem.setCost(cost);
        orderItem.setDeliveryId(supplier);

        Taxonomy taxonomy = taxonomyCache.findByMfn(orderItem.getManufacturerCode());
        String resolvedEan = taxonomy != null ? taxonomy.ean() : null;

        if (Strings.isBlank(resolvedEan)) {
            model.addAttribute("errorMessage", messageSource.getMessage("order.item.ean.not.found", null, locale));
            return showOrderItemDetails(order, orderItem, model);
        }

        orderItem.setEan(resolvedEan);
        orderItem.markAsInAllocation();
        orderItemsRepository.save(orderItem);

        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/clear-supplier")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String clearSupplier(@PathVariable String orderId, @RequestParam String itemId,
                                RedirectAttributes redirectAttributes, Locale locale) {
        OrderItem orderItem = orderItemsRepository.findById(orderId, itemId);
        if (!orderItem.isReleasable()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("order.item.clear.assign.blocked", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        orderItem.removeFulfilment();
        orderItemsRepository.save(orderItem);
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/assign-sku")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String assignSku(@PathVariable String orderId, @RequestParam String itemId, @RequestParam String sku) {
        OrderItem orderItem = orderItemsRepository.findById(orderId, itemId);
        orderItem.setSku(sku);
        orderItemsRepository.save(orderItem);
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/assign-warehouse")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String assignFromWarehouse(@PathVariable String orderId, @RequestParam String itemId,
                                      @RequestParam String warehouseItemId,
                                      RedirectAttributes redirectAttributes, Locale locale) {
        try {
            ordersManager.assignFromWarehouse(getStoreId(), orderId, itemId, warehouseItemId);
        } catch (IllegalStateException e) {
            String code = "error.message." + e.getMessage();
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(code, null, locale));
        }
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/split-group")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String splitGroupItem(@PathVariable String orderId, @ModelAttribute SplitGroupForm form,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        try {
            ordersManager.splitGroupItem(orderId, form.getItemId(), form.toComponents());
        } catch (IllegalStateException e) {
            String code = "error.message." + e.getMessage();
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(code, null, locale));
        }
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/toggle-consolidation")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String toggleConsolidation(@PathVariable String orderId, @RequestParam String itemId) {
        OrderItem orderItem = orderItemsRepository.findById(orderId, itemId);
        orderItem.toggleConsolidation();
        orderItemsRepository.save(orderItem);
        return "redirect:/dashboard/orders/" + orderId;
    }

    private String showOrderItemDetails(Order order, OrderItem orderItem, Model model) {
        model.addAttribute("orderId", order.getOrderId());
        model.addAttribute("orderItem", orderItem);
        model.addAttribute("categories", storeCategories.namesFor(order.getStoreId()));
        model.addAttribute("categoryGroups", storeCategories.groupsFor(order.getStoreId()));
        model.addAttribute("fulfilmentStatuses", FulfilmentStatus.values());
        model.addAttribute("isCompletedOrder", order.hasOneOfStatuses(OrderStatus.Completed, OrderStatus.Cancelled));
        model.addAttribute("serviceFlagLocked", orderItem.hasSupplierAllocation());
        model.addAttribute("priceLocked", !order.getDocuments().isEmpty());

        return "orderItem";
    }

    @PostMapping("/dashboard/orders/{orderId}/delete")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String deleteOrder(@PathVariable String orderId) {
        ordersManager.deleteOrder(getStoreId(), orderId);
        return "redirect:/dashboard/orders";
    }

    @PostMapping("/dashboard/orders/{orderId}/cancel")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String cancelOrder(@PathVariable String orderId, RedirectAttributes redirectAttributes, Locale locale) {
        try {
            ordersManager.cancelOrder(getStoreId(), orderId);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.order.cannot.be.cancelled", null, locale));
        }
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/removeSelectedItemsFromOrder")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String removeSelectedItemsFromOrder(@PathVariable String orderId, @ModelAttribute OrderItemsForm form) {
        ordersManager.removeFromOrder(getStoreId(), orderId, form.getSelectedOrderItemIds());
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/moveSelectedItemsToAllocation")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String moveSelectedItemsToAllocation(@PathVariable String orderId, @ModelAttribute OrderItemsForm form) {
        ordersManager.moveItemsToAllocation(getStoreId(), orderId, form.getSelectedOrderItemIds());
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/moveSelectedItemsToTheWarehouse")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String moveSelectedItemsToTheWarehouse(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                                                  RedirectAttributes redirectAttributes, Locale locale) {
        OrdersManager.Result result = ordersManager.moveOrderItemsToTheWarehouse(getStoreId(), orderId, form.getSelectedOrderItemIds());
        flashSkippedDropshipItems(result, redirectAttributes, locale);
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/moveSelectedItemsToTheWarehouseForRMA")
    public String moveSelectedItemsToTheWarehouseForRMA(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                                                        RedirectAttributes redirectAttributes, Locale locale) {
        OrdersManager.Result result = ordersManager.moveOrderItemsToTheWarehouseForRMA(getStoreId(), orderId, form.getSelectedOrderItemIds());
        flashSkippedDropshipItems(result, redirectAttributes, locale);
        return "redirect:/dashboard/orders/" + orderId;
    }

    private void flashSkippedDropshipItems(OrdersManager.Result result, RedirectAttributes redirectAttributes, Locale locale) {
        if (result.getSkippedDropshipItems() > 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("order.items.action.move.warehouse.dropship.error", null, locale));
        }
    }

    @PostMapping("/dashboard/orders/{orderId}/splitOrder")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String splitOrder(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                             RedirectAttributes redirectAttributes, Locale locale) {
        try {
            Order newOrder = ordersManager.splitOrder(getStoreId(), orderId, form.getSelectedOrderItemIds());
            return "redirect:/dashboard/orders/" + newOrder.getOrderId();
        } catch (IllegalStateException e) {
            String code = "error.message." + e.getMessage();
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(code, null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
    }

    @PostMapping("/dashboard/orders/{orderId}/moveItemsToOrder")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String moveItemsToOrder(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                                   @RequestParam(required = false) String targetOrderId,
                                   RedirectAttributes redirectAttributes, Locale locale) {
        try {
            Order target = ordersManager.moveOrderItemsToOrder(getStoreId(), orderId, targetOrderId, form.getSelectedOrderItemIds());
            return "redirect:/dashboard/orders/" + target.getOrderId();
        } catch (IllegalStateException e) {
            String code = "error.message." + e.getMessage();
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(code, null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
    }

    @PostMapping("/dashboard/orders/{orderId}/updateSerialNumbers")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateSerialNumbers(@PathVariable String orderId, @ModelAttribute OrderItemsForm form) {
        Map<String, String> serialByItemId = form.getOrderItems().stream()
                .filter(i -> Strings.isNotBlank(i.getSerialNo()))
                .collect(Collectors.toMap(
                        OrderItem::getItemId,
                        OrderItem::getSerialNo
                ));

        for (OrderItem item : orderItemsRepository.findByOrderId(orderId)) {
            if (item.isProduct()) {
                item.setSerialNo(serialByItemId.get(item.getItemId()));
                orderItemsRepository.save(item);
            }
        }

        return "redirect:/dashboard/orders/" + orderId;
    }

    @GetMapping("/dashboard/orders/{orderId}/address")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String showAddressDetails(@PathVariable String orderId, @RequestParam String type, Model model) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        model.addAttribute("order", order);
        model.addAttribute("type", type);
        return "orderAddressDetails";
    }

    @PostMapping("/dashboard/orders/{orderId}/updateAddressDetails")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateAddressDetails(@PathVariable String orderId, @RequestParam String type, @ModelAttribute("order") Order updatedOrder, RedirectAttributes redirectAttributes, Locale locale) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);
        if ("billing".equals(type) && updatedOrder.getBillingDetails() != null) {
            if (existingOrder.isInvoiced()) {
                redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.billing.details.locked", null, locale));
                return "redirect:/dashboard/orders/" + orderId;
            }
            existingOrder.setBillingDetails(updatedOrder.getBillingDetails());
        }
        if ("shipping".equals(type) && updatedOrder.getShippingDetails() != null) {
            existingOrder.setShippingDetails(updatedOrder.getShippingDetails());
        }
        ordersRepository.save(existingOrder);
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/updateReview")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateReview(@PathVariable String orderId, @ModelAttribute("order") Order updatedOrder, Model model) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);
        if (updatedOrder.getReview() != null) {
            existingOrder.setReview(updatedOrder.getReview());
        }
        return save(existingOrder);
    }

    @PostMapping("/dashboard/orders/{orderId}/updatePayments")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updatePayments(@PathVariable String orderId, @ModelAttribute("order") Order updatedOrder, Model model) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);
        if (updatedOrder.getPayments() != null) {
            List<Payment> payments = updatedOrder.getPayments().stream()
                    .filter(Payment::isComplete)
                    .collect(Collectors.toList());

            if (payments.isEmpty()) {
                payments.add(updatedOrder.getPayments().get(0));
            }

            existingOrder.setPayments(payments);
        }
        return save(existingOrder);
    }

    @PostMapping("/dashboard/orders/{orderId}/addPayment")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addPayment(@PathVariable String orderId,
                             @ModelAttribute AddPaymentForm form,
                             RedirectAttributes redirectAttributes,
                             Locale locale) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);

        if (form.getBankAmount() == 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("error.message.payment.amount.invalid", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        if (form.getProcessingFee() < 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("error.message.payment.fee.invalid", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        double amount = form.isFeeIncluded()
                ? form.getBankAmount()
                : form.getBankAmount() + form.getProcessingFee();

        Payment target = existingOrder.getPayments().stream()
                .filter(Payment::isUnsettled)
                .findFirst()
                .orElseGet(() -> {
                    Payment p = new Payment();
                    existingOrder.addPayment(p);
                    return p;
                });

        target.setSource(form.getSource());
        target.setDirection(form.getDirection() != null ? form.getDirection() : PaymentDirection.Incoming);
        target.setReferenceNo(form.getReferenceNo());
        target.setName(form.getName());
        target.setAmount(amount);
        target.setFee(form.getProcessingFee());
        target.setBankTransactionNo(form.getBankTransactionNo());
        target.setBankTransactionDate(form.getBankTransactionDate());

        return save(existingOrder);
    }

    @PostMapping("/dashboard/orders/{orderId}/updateShipments")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateShipments(@PathVariable String orderId, @ModelAttribute("order") Order updatedOrder, Model model) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);
        // OrderLifecycle.update never persists cancelled orders, so publishing here
        // would announce a shipment change that was never saved
        if (existingOrder.getStatus() == OrderStatus.Cancelled) {
            return "redirect:/dashboard/orders/" + orderId;
        }
        List<String> shipmentDataBeforeUpdate = shipmentDataSnapshot(existingOrder);
        if (updatedOrder.getShipments() != null) {
            List<Shipment> shipments = updatedOrder.getShipments().stream()
                    .filter(s -> s.hasShippingData() || s.hasCollectionData() || s.isDeliveredToCollectionPoint())
                    .collect(Collectors.toList());

            if (shipments.isEmpty()) {
                shipments.add(updatedOrder.getShipments().get(0));
            }

            List<Shipment> previousShipments = existingOrder.getShipments();
            shipments.forEach(shipment -> previousShipments.stream()
                    .filter(previous -> previous.hasTrackingNo(shipment.getTrackingNo()))
                    .findFirst()
                    .ifPresent(shipment::inheritTrackingSubscriptionFrom));
            // The operator's edit is authoritative: replaceShipments would re-inherit the previous collection
            // point and force the type back to PickupPoint, making a change of delivery type impossible.
            shipments.forEach(shipment -> shipment.setCollectionPointCode(StringUtils.trimToNull(shipment.getCollectionPointCode())));
            existingOrder.setShipments(shipments);
        }
        shipmentTrackingSubscriber.subscribe(getStoreId(), existingOrder);
        String view = save(existingOrder);
        boolean hasNotifiableShipmentData = existingOrder.getShipments().stream()
                .anyMatch(s -> s.hasShippingData() || s.hasCollectionData());
        boolean shipmentDataChanged = !shipmentDataBeforeUpdate.equals(shipmentDataSnapshot(existingOrder));
        if (hasNotifiableShipmentData && shipmentDataChanged) {
            orderLifecycleEventPublisher.publish(existingOrder, OrderLifecycleEventType.ShipmentCreated);
        }
        return view;
    }

    private List<String> shipmentDataSnapshot(Order order) {
        return order.getShipments().stream()
                .map(s -> String.join("|",
                        String.valueOf(s.getType()),
                        Objects.toString(s.getCarrier(), ""),
                        Objects.toString(s.getTrackingNo(), ""),
                        Objects.toString(s.getTrackingUrl(), ""),
                        // the shipments form round-trips shippedAt at minute precision,
                        // so sub-minute digits must not count as a data change
                        s.getShippedAt() == null ? "" : s.getShippedAt().truncatedTo(ChronoUnit.MINUTES).toString()))
                .collect(Collectors.toList());
    }

    @PostMapping("/dashboard/orders/{orderId}/addReceipt")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addReceipt(@PathVariable String orderId, @ModelAttribute Document document) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        order.addDocument(document);
        return save(order);
    }

    @PostMapping("/dashboard/orders/{orderId}/removeDocument")
    @PreAuthorize("hasRole('ADMIN')")
    public String removeDocument(@PathVariable String orderId, @RequestParam DocumentType type,
                                 @RequestParam(required = false) String number,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        Order order = ordersRepository.findById(getStoreId(), orderId);

        if (order.hasOneOfStatuses(OrderStatus.Completed, OrderStatus.Cancelled) || !order.removeDocument(type, number)) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.document.cannot.be.removed", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        // saving via OrderLifecycle would re-trigger automatic invoice generation for delivered orders
        ordersRepository.save(order);
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/cancelShipment")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String cancelShipment(@PathVariable String orderId,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        try {
            shipmentCancelService.cancelShipping(orderId, getStoreId());
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("shipment.cancel.success", null, locale));
        } catch (HttpClientException ex) {
            return handleHttpClientException(ex, orderId, redirectAttributes);
        } catch (ShippingException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/dashboard/orders/" + orderId;
    }

    private String handleHttpClientException(HttpClientException ex, String orderId,
                                             RedirectAttributes redirectAttributes) {
        String error = ex.getResponseBody();
        error = Strings.isBlank(error) ? ex.getMessage() : error;
        redirectAttributes.addFlashAttribute("errorMessage", error);
        return "redirect:/dashboard/orders/" + orderId;
    }

    public String save(Order order) {
        orderLifecycle.update(order);
        return "redirect:/dashboard/orders/" + order.getOrderId();
    }

}
