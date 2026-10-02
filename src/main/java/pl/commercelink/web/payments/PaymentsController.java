package pl.commercelink.web.payments;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderShipmentForm;

import java.time.LocalDate;
import java.util.Locale;

/** The Payments page and its results block, which list-page.js swaps in place (spec §6.3). */
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PaymentsController {

    private final PaymentsModelFactory factory;

    @GetMapping(PaymentsQuery.PATH)
    public String payments(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addPage(params, locale, model);
        return "payments";
    }

    @GetMapping(PaymentsQuery.PATH + "/fragment")
    public String fragment(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addPage(params, locale, model);
        return "payments :: results";
    }

    private void addPage(MultiValueMap<String, String> params, Locale locale, Model model) {
        model.addAttribute("page", factory.page(CustomSecurityContext.getStoreId(), PaymentsQuery.parse(params),
                LocalDate.now(OrderShipmentForm.OPERATOR_ZONE), locale));
        model.addAttribute("paymentSources", OrderLabels.Option.of(PaymentSource.values(), OrderLabels::paymentSource));
    }
}
