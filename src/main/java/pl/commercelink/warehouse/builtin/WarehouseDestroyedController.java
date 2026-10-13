package pl.commercelink.warehouse.builtin;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WarehouseDestroyedController {

    // the tiles' months are the store's calendar months, not the server's
    static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private final DestroyedListService destroyedListService;

    @GetMapping("/dashboard/warehouse/items/destroyed")
    String page(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addPage(model, params, locale);
        return "warehouse-destroyed";
    }

    @GetMapping("/dashboard/warehouse/items/destroyed/list")
    String results(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addPage(model, params, locale);
        return "warehouse-destroyed :: results";
    }

    private void addPage(Model model, MultiValueMap<String, String> params, Locale locale) {
        model.addAttribute("page", destroyedListService.page(CustomSecurityContext.getStoreId(), CustomSecurityContext.hasRole("ADMIN"),
                DestroyedListQuery.parse(params), LocalDate.now(ZONE), locale));
    }
}
