package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.orders.OrderArchiveService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.orders.OrderArchiveQuery;

import java.time.LocalDate;
import java.util.Locale;

/** The orders archive: closed orders of the session's store (spec 2026-10-09). */
@Controller
@RequiredArgsConstructor
public class OrdersArchiveController {

    private final OrderArchiveService archive;

    @GetMapping(OrderArchiveQuery.PATH)
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String archive(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        model.addAttribute("page", archive.page(CustomSecurityContext.getStoreId(), OrderArchiveQuery.parse(params), LocalDate.now(), locale));
        return "orders/archive";
    }

    @GetMapping(OrderArchiveQuery.FRAGMENT_PATH)
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String archiveResults(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        model.addAttribute("page", archive.page(CustomSecurityContext.getStoreId(), OrderArchiveQuery.parse(params), LocalDate.now(), locale));
        return "orders/archive :: results";
    }
}
