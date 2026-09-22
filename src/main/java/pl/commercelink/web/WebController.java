package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.*;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.PaginationUtil;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Controller
public class WebController {

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private DeliveriesRepository deliveriesRepository;

    @Autowired
    private SupplierLabels supplierLabels;

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
        model.addAttribute("supplierLabels", supplierLabels.forStoreId(getStoreId()));

        return "payments";
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }
}
