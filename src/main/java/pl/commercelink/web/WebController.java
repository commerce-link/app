package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }
}
