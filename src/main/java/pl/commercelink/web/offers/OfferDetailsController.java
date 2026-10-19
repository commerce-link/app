package pl.commercelink.web.offers;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.baskets.ContactDetails;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.OfferController;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsPaths;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The offer details page. Each save writes only what sits next to its button: the table saves the items, the
 * dialogs of the side cards save the terms, the client and the note, each on a fresh read of the record, so one never
 * overwrites another. Everything else on the page (copy, template, recalculation, proforma, adding and removing
 * items) stays in {@link OfferController}.
 */
@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
public class OfferDetailsController {

    private static final String VIEW = "offers/details";
    private static final String ITEM_VIEW = "offers/item-edit";
    private static final Set<String> DIALOGS = Set.of("terms", "client", "note");

    private final BasketsRepository basketsRepository;
    private final OfferDetailsPageFactory pageFactory;
    private final MessageSource messages;

    OfferDetailsController(BasketsRepository basketsRepository, OfferDetailsPageFactory pageFactory,
                           MessageSource messages) {
        this.basketsRepository = basketsRepository;
        this.pageFactory = pageFactory;
        this.messages = messages;
    }

    @GetMapping("/dashboard/offer/{offerId}")
    public String view(@PathVariable String offerId, @RequestParam(required = false) String returnTo,
                @RequestParam(required = false) String open, Model model, Locale locale) {
        Basket offer = requireOffer(offerId);
        fillPage(model, offer, returnTo, locale);
        model.addAttribute("termsForm", OfferTermsForm.from(offer));
        model.addAttribute("clientForm", contactOf(offer));
        model.addAttribute("noteForm", noteOf(offer));
        model.addAttribute("errors", Map.of());
        model.addAttribute("open", dialogToOpen(open, offer));
        return VIEW;
    }

    /** Only the items of the table; every other field stays as stored. */
    @PostMapping("/dashboard/offer/{offerId}")
    public String saveItems(@PathVariable String offerId, @ModelAttribute Basket form,
                     @RequestParam(required = false) String returnTo, RedirectAttributes redirectAttributes,
                     Locale locale) {
        Basket offer = requireOffer(offerId);
        List<BasketItem> posted = form.getBasketItems() == null ? List.of() : form.getBasketItems();
        offer.setBasketItems(OfferController.normalizeBlankCategories(posted.stream()
                .filter(BasketItem::isComplete)
                .collect(Collectors.toList())));
        save(offer);
        return savedRedirect(offer, "offers.details.saved.items", returnTo, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/offer/{offerId}/terms")
    public String saveTerms(@PathVariable String offerId, @ModelAttribute("termsForm") OfferTermsForm form,
                     @RequestParam(required = false) String returnTo,
                     @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                     Model model, Locale locale, RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Basket offer = requireOffer(offerId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(offer, "terms", errors, "offers/details :: termsForm", SettingsPaths.isAsync(requestedWith),
                    returnTo, model, locale, response, form, null, null);
        }
        form.applyTo(offer);
        save(offer);
        return saved(offer, "terms", "offers/details :: termsForm", "offers.details.saved.terms",
                SettingsPaths.isAsync(requestedWith), returnTo, model, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/offer/{offerId}/client")
    public String saveClient(@PathVariable String offerId, @ModelAttribute("clientForm") ContactDetails form,
                      @RequestParam(required = false) String returnTo,
                      @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                      Model model, Locale locale, RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Basket offer = requireOffer(offerId);
        if (offer.hasType(BasketType.OfferTemplate)) {
            // a template has no customer
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (!form.isProperlyFilled()) {
            return rejected(offer, "client", Map.of("contact", "offers.contact.invalid"), "offers/details :: clientForm",
                    SettingsPaths.isAsync(requestedWith), returnTo, model, locale, response, null, form, null);
        }
        offer.setContactDetails(form);
        save(offer);
        return saved(offer, "client", "offers/details :: clientForm", "offers.details.saved.client",
                SettingsPaths.isAsync(requestedWith), returnTo, model, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/offer/{offerId}/note")
    public String saveNote(@PathVariable String offerId, @ModelAttribute("noteForm") OfferNoteForm form,
                    @RequestParam(required = false) String returnTo,
                    @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                    Model model, Locale locale, RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Basket offer = requireOffer(offerId);
        offer.setComment(form.getComment());
        save(offer);
        return saved(offer, "note", "offers/details :: noteForm", "offers.details.saved.note",
                SettingsPaths.isAsync(requestedWith), returnTo, model, locale, redirectAttributes);
    }

    /** Without JavaScript the pencil of a row opens this page; with it the row has its own dialog. */
    @GetMapping("/dashboard/offer/{offerId}/items/{index}")
    public String showItem(@PathVariable String offerId, @PathVariable int index,
                    @RequestParam(required = false) String returnTo, Model model, Locale locale) {
        Basket offer = requireOffer(offerId);
        BasketItem item = requireItem(offer, index);
        renderItem(model, offer, index, OfferItemForm.from(item), Map.of(), returnTo, locale);
        return ITEM_VIEW;
    }

    @PostMapping("/dashboard/offer/{offerId}/items/{index}")
    public String saveItem(@PathVariable String offerId, @PathVariable int index, @ModelAttribute("form") OfferItemForm form,
                    @RequestParam(required = false) String returnTo, Model model, Locale locale,
                    RedirectAttributes redirectAttributes) {
        Basket offer = requireOffer(offerId);
        BasketItem item = requireItem(offer, index);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            renderItem(model, offer, index, form, errors, returnTo, locale);
            return ITEM_VIEW;
        }
        form.applyTo(item);
        // re-sets the list so the manufacturer code is unified and the variant groups stay joined, as in a table save
        offer.setBasketItems(new ArrayList<>(offer.getBasketItems()));
        save(offer);
        return savedRedirect(offer, "offers.details.saved.item", returnTo, redirectAttributes, locale);
    }

    /** Without JavaScript "Create order" opens this page; with it, confirm-dialog.js posts to the same address. */
    @GetMapping("/dashboard/offer/{offerId}/order")
    public String confirmOrder(@PathVariable String offerId, @RequestParam(required = false) String returnTo,
                        Model model, Locale locale) {
        Basket offer = requireOffer(offerId);
        model.addAttribute("confirm", new ConfirmAction(
                messages.getMessage("offers.details.order.confirm.title", new Object[]{displayName(offer, locale)}, locale),
                messages.getMessage("offers.details.order.confirm.message", null, locale),
                messages.getMessage("offers.details.order.confirm.action", null, locale),
                "/dashboard/offer/" + offerId + "/order", pageHref(offerId, returnTo), false));
        model.addAttribute("backLabel", displayName(offer, locale));
        return "settings-confirm";
    }

    /** Hands over to the order form; the order is made there from the saved offer. */
    @PostMapping("/dashboard/offer/{offerId}/order")
    public String order(@PathVariable String offerId) {
        requireOffer(offerId);
        return "redirect:/dashboard/orders/new/from-basket?basketId=" + offerId;
    }

    @GetMapping("/dashboard/offer/{offerId}/recalculate")
    public String confirmRecalculate(@PathVariable String offerId, @RequestParam(required = false) String returnTo,
                              Model model, Locale locale) {
        Basket offer = requireOffer(offerId);
        model.addAttribute("confirm", new ConfirmAction(
                messages.getMessage("offers.details.recalculate.confirm.title", new Object[]{displayName(offer, locale)}, locale),
                messages.getMessage("offers.details.recalculate.confirm.message", null, locale),
                messages.getMessage("offers.details.recalculate.confirm.action", null, locale),
                "/dashboard/offer/" + offerId + "/recalculate", pageHref(offerId, returnTo), false));
        model.addAttribute("backLabel", displayName(offer, locale));
        return "settings-confirm";
    }

    private String rejected(Basket offer, String dialog, Map<String, String> errors, String fragment, boolean async,
                            String returnTo, Model model, Locale locale, HttpServletResponse response,
                            OfferTermsForm terms, ContactDetails client, OfferNoteForm note) {
        fillPage(model, offer, returnTo, locale);
        model.addAttribute("termsForm", terms != null ? terms : OfferTermsForm.from(offer));
        model.addAttribute("clientForm", client != null ? client : contactOf(offer));
        model.addAttribute("noteForm", note != null ? note : noteOf(offer));
        model.addAttribute("errors", errors);
        model.addAttribute("open", dialog);
        if (async) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            return fragment;
        }
        return VIEW;
    }

    private String saved(Basket offer, String dialog, String fragment, String messageKey, boolean async,
                         String returnTo, Model model, Locale locale, RedirectAttributes redirectAttributes) {
        if (async) {
            // the page is built from the record just saved: the fragment and the refreshed card and header come from it
            fillPage(model, offer, returnTo, locale);
            model.addAttribute("termsForm", OfferTermsForm.from(offer));
            model.addAttribute("clientForm", contactOf(offer));
            model.addAttribute("noteForm", noteOf(offer));
            model.addAttribute("errors", Map.of());
            model.addAttribute("open", dialog);
            model.addAttribute("saved", true);
            return fragment;
        }
        return savedRedirect(offer, messageKey, returnTo, redirectAttributes, locale);
    }

    private String savedRedirect(Basket offer, String messageKey, String returnTo, RedirectAttributes redirectAttributes,
                                 Locale locale) {
        redirectAttributes.addFlashAttribute("offerNotice", messages.getMessage(messageKey, null, locale));
        return "redirect:" + pageHref(offer.getBasketId(), returnTo);
    }

    private void fillPage(Model model, Basket offer, String returnTo, Locale locale) {
        model.addAttribute("page", pageFactory.build(offer, returnTo, locale, LocalDateTime.now()));
        model.addAttribute("returnTo", safeReturnTo(returnTo));
    }

    private void renderItem(Model model, Basket offer, int index, OfferItemForm form, Map<String, String> errors,
                            String returnTo, Locale locale) {
        fillPage(model, offer, returnTo, locale);
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("index", index);
        model.addAttribute("formAction", "/dashboard/offer/" + offer.getBasketId() + "/items/" + index);
        model.addAttribute("pageHref", pageHref(offer.getBasketId(), returnTo));
    }

    private static String dialogToOpen(String open, Basket offer) {
        if (open == null || !DIALOGS.contains(open) || (open.equals("client") && offer.hasType(BasketType.OfferTemplate))) {
            return null;
        }
        return open;
    }

    private static ContactDetails contactOf(Basket offer) {
        return offer.getContactDetails() != null ? offer.getContactDetails() : new ContactDetails();
    }

    private static OfferNoteForm noteOf(Basket offer) {
        OfferNoteForm form = new OfferNoteForm();
        form.setComment(offer.getComment());
        return form;
    }

    /** The list address the page was opened from, or null; anything else would be an open redirect. */
    private static String safeReturnTo(String returnTo) {
        return returnTo != null && OfferController.safeReturnTo(returnTo).equals(returnTo) ? returnTo : null;
    }

    private static String pageHref(String offerId, String returnTo) {
        String safe = safeReturnTo(returnTo);
        return "/dashboard/offer/" + offerId
                + (safe == null ? "" : "?returnTo=" + URLEncoder.encode(safe, StandardCharsets.UTF_8));
    }

    private String displayName(Basket offer, Locale locale) {
        if (StringUtils.isNotBlank(offer.getName())) {
            return offer.getName();
        }
        String key = offer.hasType(BasketType.OfferTemplate) ? "offers.list.untitledTemplate" : "offers.list.untitled";
        return messages.getMessage(key, new Object[]{offer.getShortenedBasketId()}, locale);
    }

    private Basket requireOffer(String offerId) {
        return basketsRepository.findById(CustomSecurityContext.getStoreId(), offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static BasketItem requireItem(Basket offer, int index) {
        List<BasketItem> items = offer.getBasketItems();
        if (index < 0 || index >= items.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return items.get(index);
    }

    /** Same stamp as OfferController.save: an offer without a source gets the user who last saved it. */
    private void save(Basket offer) {
        if (offer.getType() == BasketType.Offer && offer.getSource() == null) {
            offer.setSource(new OrderSource(CustomSecurityContext.getLoggedInUserName(), OrderSourceType.CallCenter));
        }
        basketsRepository.save(offer);
    }
}
