package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.orders.ItemMargin;
import pl.commercelink.web.orders.OrderFlash;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * The low-margin threshold of the store, set from the items card of an order page. The threshold is the store's (every
 * order and every user of the store see the same marks), the order only says where to come back to.
 */
@Controller
@RequiredArgsConstructor
public class OrderLowMarginController extends BaseController {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final OrdersRepository ordersRepository;
    private final StoresRepository storesRepository;
    private final MessageSource messageSource;

    @PostMapping("/dashboard/orders/{orderId}/lowMarginThreshold")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String save(@PathVariable String orderId, @RequestParam(required = false) String threshold,
                       RedirectAttributes redirectAttributes, Locale locale) {
        requireOrder(ordersRepository, getStoreId(), orderId);
        String back = "redirect:/dashboard/orders/" + orderId + "#pozycje";
        BigDecimal value;
        try {
            value = parse(threshold);
        } catch (NumberFormatException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("order.items.margin.threshold.invalid", null, locale));
            return back;
        }
        Store store = storesRepository.findById(getStoreId());
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        store.setLowMarginThreshold(value == null ? null : value.doubleValue());
        storesRepository.save(store);
        OrderFlash.saved(redirectAttributes, value == null
                ? messageSource.getMessage("order.items.margin.threshold.cleared", null, locale)
                : messageSource.getMessage("order.items.margin.threshold.saved",
                        new Object[]{ItemMargin.thresholdText(value.doubleValue())}, locale));
        return back;
    }

    /**
     * The typed percent: "12,5", "12.5" or "12,5 %"; blank clears the threshold. Above 0 and below 100, at most two
     * decimals — anything else is refused rather than guessed.
     */
    static BigDecimal parse(String threshold) {
        String text = StringUtils.trimToEmpty(threshold).replace("%", "").replace(',', '.').strip();
        if (text.isEmpty()) {
            return null;
        }
        BigDecimal value = new BigDecimal(text);
        if (value.signum() <= 0 || value.compareTo(HUNDRED) >= 0 || value.stripTrailingZeros().scale() > 2) {
            throw new NumberFormatException(text);
        }
        return value.stripTrailingZeros();
    }
}
