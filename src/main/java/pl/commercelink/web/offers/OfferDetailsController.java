package pl.commercelink.web.offers;

import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
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
import pl.commercelink.baskets.OfferItemReloader;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.OfferController;
import pl.commercelink.web.dtos.AddItemsForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsPaths;

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
 * overwrites another. The table is a draft until its save: adding items renders it from the posted rows without saving
 * (draftItems), and the save is refused over a record changed since the page was opened. Everything else on the page
 * (copy, template, recalculation, proforma, and adding and removing items without JavaScript) stays in
 * {@link OfferController}.
 */
@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class OfferDetailsController {

    private static final String VIEW = "offers/details";
    private static final String ITEM_VIEW = "offers/item-edit";
    private static final Set<String> DIALOGS = Set.of("terms", "client", "note");

    private final BasketsRepository basketsRepository;
    private final OfferDetailsPageFactory pageFactory;
    private final MessageSource messages;
    private final OfferItemReloader offerItemReloader;
    private final OfferItemsDraft offerItemsDraft;

    /** What a dialog answer needs besides the record: the list to return to, whether the script asked, the model. */
    private record Answer(String returnTo, boolean async, Model model, Locale locale) {
        Answer(String returnTo, String requestedWith, Model model, Locale locale) {
            this(returnTo, SettingsPaths.isAsync(requestedWith), model, locale);
        }
    }

    /** The three dialog forms rendered with the page: from the record, or one of them as posted. */
    private record DialogForms(OfferTermsForm terms, ContactDetails client, OfferNoteForm note) {
        static DialogForms of(Basket offer) {
            return new DialogForms(OfferTermsForm.from(offer), contactOf(offer), noteOf(offer));
        }
    }

    @GetMapping("/dashboard/offer/{offerId}")
    public String view(@PathVariable String offerId, @RequestParam(required = false) String returnTo,
                @RequestParam(required = false) String open, Model model, Locale locale) {
        Basket offer = requireOffer(offerId);
        fillPage(model, offer, returnTo, locale);
        fillDialogs(model, DialogForms.of(offer), Map.of(), dialogToOpen(open, offer));
        return VIEW;
    }

    /**
     * Only the items of the table; every other field stays as stored. version is the record's version the page was
     * opened at: when the record changed since (another operator, the client choosing a variant on their page), nothing
     * is saved and the page comes back, 409, with the posted items and the choice to overwrite. No version (a page from
     * before this check) saves as before.
     */
    @PostMapping("/dashboard/offer/{offerId}")
    public String saveItems(@PathVariable String offerId, @ModelAttribute Basket form,
                     @RequestParam(required = false) Long version, @RequestParam(defaultValue = "false") boolean overwrite,
                     @RequestParam(required = false) String returnTo, Model model, HttpServletResponse response,
                     RedirectAttributes redirectAttributes, Locale locale) {
        Basket offer = requireOffer(offerId);
        boolean changedSince = version != null && !version.equals(offer.getVersion());
        setPostedItems(offer, form);
        if (changedSince && !overwrite) {
            // the page is built from the stored record with the posted items, so its hidden version is the stored one
            // and "Overwrite" posts that
            model.addAttribute("page", pageFactory.buildDraft(offer, returnTo, locale, LocalDateTime.now()));
            model.addAttribute("returnTo", safeReturnTo(returnTo));
            fillDialogs(model, DialogForms.of(offer), Map.of(), null);
            model.addAttribute("conflict", true);
            response.setStatus(HttpStatus.CONFLICT.value());
            return VIEW;
        }
        save(offer);
        return savedRedirect(offer, "offers.details.saved.items", returnTo, redirectAttributes, locale);
    }

    /**
     * The table as posted plus the entries picked in the "add items" dialog, rendered as the items card and never saved:
     * the table stays a draft until "Zapisz zmiany". The meta template says how many entries were skipped (no longer in
     * the price list or at the suppliers).
     */
    @PostMapping("/dashboard/offer/{offerId}/items/draft")
    public String draftItems(@PathVariable String offerId, @ModelAttribute Basket form,
                      @ModelAttribute("addItemsForm") AddItemsForm added, @RequestParam(required = false) String returnTo,
                      Model model, Locale locale) {
        Basket offer = requireOffer(offerId);
        setPostedItems(offer, form);
        int skipped = offerItemsDraft.add(offer, added);
        model.addAttribute("page", pageFactory.buildDraft(offer, returnTo, locale, LocalDateTime.now()));
        model.addAttribute("returnTo", safeReturnTo(returnTo));
        model.addAttribute("skipped", skipped);
        model.addAttribute("requested", added.entries().size());
        return VIEW + " :: itemsDraft";
    }

    /** Only the items are taken from the bound form: its other fields (the version among them) never reach the record. */
    private static void setPostedItems(Basket offer, Basket form) {
        List<BasketItem> posted = form.getBasketItems() == null ? List.of() : form.getBasketItems();
        offer.setBasketItems(OfferController.normalizeBlankCategories(posted.stream()
                .filter(BasketItem::isComplete)
                .collect(Collectors.toList())));
    }

    @PostMapping("/dashboard/offer/{offerId}/terms")
    public String saveTerms(@PathVariable String offerId, @ModelAttribute("termsForm") OfferTermsForm form,
                     @RequestParam(required = false) String returnTo,
                     @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                     Model model, Locale locale, RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Basket offer = requireOffer(offerId);
        Answer answer = new Answer(returnTo, requestedWith, model, locale);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            DialogForms posted = DialogForms.of(offer);
            return rejected(offer, "terms", errors, new DialogForms(form, posted.client(), posted.note()), answer, response);
        }
        form.applyTo(offer);
        save(offer);
        return saved(offer, "terms", "offers.details.saved.terms", answer, redirectAttributes);
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
        Answer answer = new Answer(returnTo, requestedWith, model, locale);
        if (!form.isProperlyFilled()) {
            DialogForms stored = DialogForms.of(offer);
            return rejected(offer, "client", Map.of("contact", "offers.contact.invalid"),
                    new DialogForms(stored.terms(), form, stored.note()), answer, response);
        }
        offer.setContactDetails(withoutBlanks(form));
        save(offer);
        return saved(offer, "client", "offers.details.saved.client", answer, redirectAttributes);
    }

    @PostMapping("/dashboard/offer/{offerId}/note")
    public String saveNote(@PathVariable String offerId, @ModelAttribute("noteForm") OfferNoteForm form,
                    @RequestParam(required = false) String returnTo,
                    @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                    Model model, Locale locale, RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Basket offer = requireOffer(offerId);
        offer.setComment(form.getComment());
        save(offer);
        return saved(offer, "note", "offers.details.saved.note", new Answer(returnTo, requestedWith, model, locale),
                redirectAttributes);
    }

    /** Without JavaScript the pencil of a row opens this page; with it the row has its own dialog. */
    @GetMapping("/dashboard/offer/{offerId}/items/{index}")
    public String showItem(@PathVariable String offerId, @PathVariable int index,
                    @RequestParam(required = false) String returnTo, Model model, Locale locale) {
        Basket offer = requireOffer(offerId);
        BasketItem item = requireItem(recalculatedTemplate(offer), index);
        renderItem(model, offer, index, OfferItemForm.from(item), Map.of(), returnTo, locale);
        return ITEM_VIEW;
    }

    @PostMapping("/dashboard/offer/{offerId}/items/{index}")
    public String saveItem(@PathVariable String offerId, @PathVariable int index, @ModelAttribute("form") OfferItemForm form,
                    @RequestParam(required = false) String returnTo, Model model, Locale locale,
                    RedirectAttributes redirectAttributes) {
        Basket offer = requireOffer(offerId);
        BasketItem item = requireItem(recalculatedTemplate(offer), index);
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
                OfferController.offerHref(offerId, "/recalculate", returnTo), pageHref(offerId, returnTo), false));
        model.addAttribute("backLabel", displayName(offer, locale));
        return "settings-confirm";
    }

    private String rejected(Basket offer, String dialog, Map<String, String> errors, DialogForms forms, Answer answer,
                            HttpServletResponse response) {
        fillPage(answer.model(), offer, answer.returnTo(), answer.locale());
        fillDialogs(answer.model(), forms, errors, dialog);
        if (answer.async()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            return dialogFragment(dialog);
        }
        return VIEW;
    }

    private String saved(Basket offer, String dialog, String messageKey, Answer answer,
                         RedirectAttributes redirectAttributes) {
        if (answer.async()) {
            // the page is built from the record just saved: the fragment and the refreshed card and header come from it
            fillPage(answer.model(), offer, answer.returnTo(), answer.locale());
            fillDialogs(answer.model(), DialogForms.of(offer), Map.of(), dialog);
            answer.model().addAttribute("saved", true);
            return dialogFragment(dialog);
        }
        return savedRedirect(offer, messageKey, answer.returnTo(), redirectAttributes, answer.locale());
    }

    private static String dialogFragment(String dialog) {
        return VIEW + " :: " + dialog + "Form";
    }

    private static void fillDialogs(Model model, DialogForms forms, Map<String, String> errors, String open) {
        model.addAttribute("termsForm", forms.terms());
        model.addAttribute("clientForm", forms.client());
        model.addAttribute("noteForm", forms.note());
        model.addAttribute("errors", errors);
        model.addAttribute("open", open);
    }

    /**
     * A template shows its prices recalculated on open, and a save keeps what it shows (as the old form posted the whole
     * recalculated table): the page of one item reads and saves the template the same way. An offer is left as stored.
     */
    private Basket recalculatedTemplate(Basket offer) {
        if (offer.hasType(BasketType.OfferTemplate)) {
            offerItemReloader.recalculate(offer);
        }
        return offer;
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

    /** DynamoDB stores "" as null; the async refresh renders the record before any reload, so it has to match. */
    private static ContactDetails withoutBlanks(ContactDetails form) {
        form.setName(StringUtils.defaultIfBlank(form.getName(), null));
        form.setSurname(StringUtils.defaultIfBlank(form.getSurname(), null));
        form.setCompanyName(StringUtils.defaultIfBlank(form.getCompanyName(), null));
        form.setEmail(StringUtils.defaultIfBlank(form.getEmail(), null));
        form.setPhone(StringUtils.defaultIfBlank(form.getPhone(), null));
        return form;
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
        return OfferController.offerPageHref(offerId, returnTo);
    }

    private String displayName(Basket offer, Locale locale) {
        return OfferController.displayName(offer, messages, locale);
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

    private void save(Basket offer) {
        OfferController.stampSource(offer);
        basketsRepository.save(offer);
    }
}
