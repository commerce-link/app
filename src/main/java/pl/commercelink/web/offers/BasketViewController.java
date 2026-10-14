package pl.commercelink.web.offers;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.settings.ConfirmAction;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;

/** The store basket preview. Read only: nothing here saves or deletes a basket; the order is made by the existing flow. */
@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
public class BasketViewController {

    private final BasketsRepository basketsRepository;
    private final BasketViewPageFactory pageFactory;
    private final MessageSource messages;

    BasketViewController(BasketsRepository basketsRepository, BasketViewPageFactory pageFactory, MessageSource messages) {
        this.basketsRepository = basketsRepository;
        this.pageFactory = pageFactory;
        this.messages = messages;
    }

    @GetMapping("/dashboard/basket/view/{basketId}")
    public String view(@PathVariable String basketId, @RequestParam(required = false) String returnTo, Model model, Locale locale) {
        model.addAttribute("page", pageFactory.build(storeBasket(basketId), returnTo, locale, LocalDateTime.now()));
        return "offers/basket";
    }

    /** Without JavaScript the "Create order" link opens this page; with it, confirm-dialog.js posts to the same address. */
    @GetMapping("/dashboard/basket/view/{basketId}/order")
    public String confirmOrder(@PathVariable String basketId, @RequestParam(required = false) String returnTo,
                               Model model, Locale locale) {
        Basket basket = storeBasket(basketId);
        String page = pageHref(basketId, returnTo);
        if (basket.getEffectiveBasketItems().isEmpty()) {
            return "redirect:" + page;
        }
        String shortId = basket.getShortenedBasketId();
        String back = BasketViewPageFactory.backHref(returnTo);
        String action = "/dashboard/basket/view/" + basketId + "/order"
                + (back.equals(BasketViewPageFactory.BASKETS_PATH) ? "" : "?returnTo=" + URLEncoder.encode(back, StandardCharsets.UTF_8));
        model.addAttribute("confirm", new ConfirmAction(
                messages.getMessage("offers.basket.order.confirm.title", new Object[]{shortId}, locale),
                messages.getMessage("offers.basket.order.confirm.message", null, locale),
                messages.getMessage("offers.basket.order.confirm.action", null, locale),
                action, page, false));
        model.addAttribute("backLabel", messages.getMessage("offers.basket.title", new Object[]{shortId}, locale));
        return "settings-confirm";
    }

    /** Only hands over to the order form; BasketOrderImporter creates the order and deletes the basket there. */
    @PostMapping("/dashboard/basket/view/{basketId}/order")
    public String order(@PathVariable String basketId, @RequestParam(required = false) String returnTo) {
        Basket basket = storeBasket(basketId);
        if (basket.getEffectiveBasketItems().isEmpty()) {
            return "redirect:" + pageHref(basketId, returnTo);
        }
        return "redirect:/dashboard/orders/new/from-basket?basketId=" + basketId;
    }

    private String pageHref(String basketId, String returnTo) {
        String back = BasketViewPageFactory.backHref(returnTo);
        return "/dashboard/basket/view/" + basketId
                + (back.equals(BasketViewPageFactory.BASKETS_PATH) ? "" : "?returnTo=" + URLEncoder.encode(back, StandardCharsets.UTF_8));
    }

    /** Only the store's own shop baskets; an offer or a template has its own page, another store's id does not exist here. */
    private Basket storeBasket(String basketId) {
        return basketsRepository.findById(CustomSecurityContext.getStoreId(), basketId)
                .filter(b -> b.hasType(BasketType.Basket))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
