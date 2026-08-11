package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.manual.ManualSupplierInfos;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.*;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.PaginationUtil;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.web.dtos.InventoryItemView;
import pl.commercelink.web.dtos.StoreSupplierView;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Controller
public class WebController {

    @Autowired
    private Inventory inventory;

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private DeliveriesRepository deliveriesRepository;

    @Autowired
    private TaxonomyCache taxonomyCache;

    @Autowired
    private PimCatalog pimCatalog;

    @Autowired
    private SupplierRegistry supplierRegistry;

    private static final int CLIENTS_PAGE_SIZE = 25;

    @GetMapping("/dashboard")
    public String index() {
        if (CustomSecurityContext.hasRole("SUPER_ADMIN")) {
            return "redirect:/dashboard/stores";
        }
        return "redirect:/dashboard/orders";
    }

    @GetMapping("/dashboard/clients")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String clients(@RequestParam(required = false) String orderId,
                          @RequestParam(required = false) String email,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderedAtStart,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderedAtEnd,
                          @RequestParam(required = false, defaultValue = "1") int page,
                          Model model) {
        List<OrderIndexEntry> pastOrders = Collections.emptyList();
        boolean hasSearchParams = isNotBlank(orderId) || isNotBlank(email) || orderedAtStart != null || orderedAtEnd != null;
        if (hasSearchParams) {
            PastOrderFilter filter = new PastOrderFilter(orderId, email, orderedAtStart, orderedAtEnd);
            pastOrders = ordersRepository.searchPastOrders(getStoreId(), filter);
        }

        List<OrderIndexEntry> paginatedPastOrders = PaginationUtil.paginate(pastOrders, page, CLIENTS_PAGE_SIZE, model);

        List<Order> pastOrderDetails = paginatedPastOrders.stream()
                .map(entry -> ordersRepository.findById(entry.getStoreId(), entry.getOrderId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("orderId", orderId);
        searchParams.put("email", email);
        searchParams.put("orderedAtStart", orderedAtStart);
        searchParams.put("orderedAtEnd", orderedAtEnd);

        model.addAttribute("pastOrders", pastOrderDetails);
        model.addAttribute("searchParams", searchParams);

        return "clients";
    }

    @GetMapping("/dashboard/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public String payments(Model model) {

        List<Delivery> unpaidDeliveries = deliveriesRepository.findUnpaidDeliveries(getStoreId());

        double unpaidDeliveriesAmountNet = unpaidDeliveries.stream()
                .mapToDouble(Delivery::getUnpaidAmountNet)
                .sum();
        double unpaidDeliveriesAmountGross = unpaidDeliveries.stream()
                .mapToDouble(Delivery::getUnpaidAmountGross)
                .sum();

        List<Order> unpaidOrders = ordersRepository.findAllActiveOrders(CustomSecurityContext.getStoreId())
                .stream()
                .filter(o -> !o.isFullyPaid())
                .sorted(Comparator.comparing(Order::getEstimatedShippingAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        double unpaidOrdersAmountNet = unpaidOrders.stream()
                .mapToDouble(Order::getUnpaidAmountNet)
                .sum();
        double unpaidOrdersAmountGross = unpaidOrders.stream()
                .mapToDouble(Order::getUnpaidAmountGross)
                .sum();

        model.addAttribute("unpaidOrders", unpaidOrders);
        model.addAttribute("unpaidOrdersAmountNet", unpaidOrdersAmountNet);
        model.addAttribute("unpaidOrdersAmountGross", unpaidOrdersAmountGross);
        model.addAttribute("unpaidDeliveries", unpaidDeliveries);
        model.addAttribute("unpaidDeliveriesAmountNet", unpaidDeliveriesAmountNet);
        model.addAttribute("paymentSources", PaymentSource.values());
        model.addAttribute("unpaidDeliveriesAmountGross", unpaidDeliveriesAmountGross);

        return "payments";
    }

    @GetMapping("/dashboard/inventory")
    public String inventory(Model model) {
        return mapInventory(model, inventory);
    }

    @GetMapping("/dashboard/inventory/check-price")
    public String checkProductPrice(
            @RequestParam(value = "mfn", required = false) String productCode,
            @RequestParam(value = "ean", required = false) String ean,
            @RequestParam(value = "pimId", required = false) String pimId,
            Model model) {

        InventoryView inventoryView = inventory.withEnabledSuppliersAndWarehouseData(getStoreId());

        MatchedInventory matchedInventory = null;
        if (pimId != null && !pimId.isEmpty()) {
            Optional<PimEntry> pimEntry = pimCatalog.findByPimId(pimId);
            if (pimEntry.isPresent()) {
                matchedInventory = inventoryView.findByInventoryKey(InventoryKey.fromPimEntry(pimEntry.get()));
            }
        } else if (productCode != null && !productCode.isEmpty()) {
            matchedInventory = inventoryView.findByProductCode(productCode);
        } else if (ean != null && !ean.isEmpty()) {
            matchedInventory = inventoryView.findByEan(ean);
        }

        mapProductPrice(model, matchedInventory);
        return mapInventory(model, inventory);
    }

    private String mapInventory(Model model, Inventory _inventory) {
        Store store = getStoreId() != null ? storesRepository.findById(getStoreId()) : null;
        List<String> enabledSuppliers = store != null ?
                new ArrayList<>(store.getEnabledProviders()) :
                new ArrayList<>(supplierRegistry.getAllSupplierNames());
        enabledSuppliers.add(SupplierRegistry.WAREHOUSE);

        List<StoreSupplierView> storeSuppliers = store != null ?
                store.getSupplierConnections()
                        .stream()
                        .filter(StoreSupplierConnection::isEnabled)
                        .map(StoreSupplierView::from)
                        .toList() :
                List.of();

        model.addAttribute("enabledSuppliers", enabledSuppliers);
        model.addAttribute("storeSuppliers", storeSuppliers);
        model.addAttribute("inventorySize", _inventory.size());
        model.addAttribute("taxonomyFileName", taxonomyCache.getFileName());
        model.addAttribute("taxonomySize", taxonomyCache.size());
        model.addAttribute("pimIndexSize", pimCatalog.findAll().size());

        return "inventory";
    }

    private void mapProductPrice(Model model, MatchedInventory matchedInventory) {
        if (matchedInventory != null && matchedInventory.hasAnyOffers()) {
            Taxonomy taxonomy = matchedInventory.getTaxonomy();

            model.addAttribute("ean", taxonomy.ean());
            model.addAttribute("mfn", taxonomy.mfn());
            model.addAttribute("name", taxonomy.name());
            model.addAttribute("brand", taxonomy.brand());
            model.addAttribute("lowestGrossPrice", matchedInventory.getLowestPrice().grossValue());
            model.addAttribute("medianGrossPrice", matchedInventory.getMedianPrice().grossValue());
            model.addAttribute("totalAvailableQty", matchedInventory.getTotalAvailableQty());
            model.addAttribute("inventoryItems", matchedInventory.getInventoryItems().stream()
                    .map(i -> new InventoryItemView(i.supplier(), ManualSupplierInfos.label(i.supplier()), i.ean(), i.mfn(), Price.fromNet(i.netPrice()).grossValue(), i.qty()))
                    .toList());
        } else {
            model.addAttribute("error", "Product not found");
        }
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }
}
