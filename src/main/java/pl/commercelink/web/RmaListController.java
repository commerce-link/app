package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.orders.rma.RmaListService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.rma.RmaListQuery;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/** The RMA list of the session's store (spec 2026-10-13). */
@Controller
@RequiredArgsConstructor
public class RmaListController {

    private final RmaListService rmaList;

    @GetMapping(RmaListQuery.PATH)
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String list(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        Optional<String> legacy = RmaListQuery.legacyHref(params);
        if (legacy.isPresent()) {
            return "redirect:" + legacy.get();
        }
        model.addAttribute("page", rmaList.page(CustomSecurityContext.getStoreId(), RmaListQuery.parse(params), LocalDate.now(), locale));
        return "rma";
    }

    @GetMapping(RmaListQuery.FRAGMENT_PATH)
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String results(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        model.addAttribute("page", rmaList.page(CustomSecurityContext.getStoreId(), RmaListQuery.parse(params), LocalDate.now(), locale));
        return "rma :: results";
    }
}
