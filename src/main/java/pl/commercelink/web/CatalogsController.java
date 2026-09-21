package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.scheduling.PollingScheduleDescription;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.catalog.CatalogRow;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Product catalogs of the store: the list, one catalog (its categories) and the catalog settings. */
@Controller
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CatalogsController {

    private final ProductCatalogRepository catalogRepository;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/catalogs")
    public String catalogs(Model model, Locale locale) {
        List<CatalogRow> rows = catalogRepository.findAll(CustomSecurityContext.getStoreId()).stream()
                .sorted(Comparator.comparing(ProductCatalog::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(catalog -> CatalogRow.of(catalog, scheduleText(catalog.getPricelistSchedule(), locale)))
                .toList();
        model.addAttribute("catalogs", rows);
        return "catalog/catalogs";
    }

    /** "co 30 min" / "codziennie o 06:00" from the saved expression; the default schedule has its own sentence. */
    String scheduleText(String expression, Locale locale) {
        if (expression == null || expression.isBlank()) {
            return messageSource.getMessage("catalog.schedule.default", null, locale);
        }
        PollingScheduleDescription description = PollingScheduleDescription.of(expression);
        return messageSource.getMessage(description.code(), description.messageArgs(), locale);
    }
}
