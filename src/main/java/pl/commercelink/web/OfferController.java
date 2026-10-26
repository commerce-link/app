package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import pl.commercelink.baskets.*;
import pl.commercelink.invoicing.InvoicingService;
import pl.commercelink.offer.imports.OfferImporter;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.AddItemsForm;
import pl.commercelink.web.dtos.OfferCreationDto;
import pl.commercelink.web.offers.OfferItemsDraft;
import pl.commercelink.web.settings.ConfirmAction;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
public class OfferController {

    private static final int MAX_SOURCE_ID_LENGTH = 200;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private BasketsRepository basketsRepository;

    @Autowired
    private OfferItemsDraft offerItemsDraft;

    @Autowired
    private OfferItemReloader offerItemReloader;

    @Autowired
    private InvoicingService invoicingService;

    @Autowired
    private List<OfferImporter> offerImporters;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private MultipartProperties multipartProperties;

    @GetMapping("/dashboard/offer/new/csv")
    public String showOfferImportSelection(Model model) {
        OfferCreationDto form = new OfferCreationDto();
        form.setType("CSV");
        model.addAttribute("form", form);
        model.addAttribute("uploadLimit", multipartProperties.getMaxFileSize());
        return "newOffer_from_csv";
    }

    @GetMapping("/dashboard/offer/new")
    public String showContactDetailsForm(@RequestParam String intent,
                                         @RequestParam(required = false) String sourceId,
                                         Model model, Locale locale) {
        Optional<Basket> source = sourceOf(intent, sourceId);
        ContactDetails contact = new ContactDetails();
        String offerName = null;
        Optional<Basket> storeBasket = source.filter(b -> b.hasType(BasketType.Basket));
        if (storeBasket.isPresent()) {
            // a shop basket carries the customer's billing and shipping details, the offer's contact starts from them
            fillFrom(contact, storeBasket.get());
            offerName = messageSource.getMessage("offers.basket.offerName",
                    new Object[]{storeBasket.get().getShortenedBasketId()}, locale);
        }
        model.addAttribute("contactDetails", contact);
        model.addAttribute("offerName", offerName);
        model.addAttribute("intent", intent);
        model.addAttribute("sourceId", sourceId);
        model.addAttribute("source", source.map(OfferController::sourceKind).orElse(null));
        return "offerContact";
    }

    /**
     * What a new offer starts from: nothing for an empty offer, a template for intent=template, a shop basket, an offer or
     * a template for intent=copy. An unknown intent or a source that is missing, of another store or of the wrong type
     * answers 404 on the page and on its POST, instead of a 500 when the copy is made.
     */
    private Optional<Basket> sourceOf(String intent, String sourceId) {
        return switch (intent) {
            case "manual" -> Optional.empty();
            case "template" -> Optional.of(requireSource(sourceId, EnumSet.of(BasketType.OfferTemplate)));
            case "copy" -> Optional.of(requireSource(sourceId, EnumSet.allOf(BasketType.class)));
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
    }

    private Basket requireSource(String sourceId, Set<BasketType> types) {
        // ids are UUIDs; a key longer than DynamoDB accepts would fail the lookup itself
        if (StringUtils.isBlank(sourceId) || sourceId.length() > MAX_SOURCE_ID_LENGTH) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return basketsRepository.findById(getStoreId(), sourceId)
                .filter(b -> b.getType() != null && types.contains(b.getType()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** The page's way back and lead: "basket", "offer" or "template". */
    private static String sourceKind(Basket source) {
        return switch (source.getType()) {
            case Basket -> "basket";
            case Offer -> "offer";
            case OfferTemplate -> "template";
        };
    }

    /** Billing first, shipping fills what billing left empty. */
    private static void fillFrom(ContactDetails contact, Basket basket) {
        BillingDetails b = basket.getBillingDetails();
        ShippingDetails s = basket.getShippingDetails();
        contact.setName(firstNonBlank(b == null ? null : b.getName(), s == null ? null : s.getName()));
        contact.setSurname(firstNonBlank(b == null ? null : b.getSurname(), s == null ? null : s.getSurname()));
        contact.setCompanyName(firstNonBlank(b == null ? null : b.getCompanyName(), s == null ? null : s.getCompanyName()));
        contact.setEmail(firstNonBlank(b == null ? null : b.getEmail(), s == null ? null : s.getEmail()));
        contact.setPhone(firstNonBlank(b == null ? null : b.getPhone(), s == null ? null : s.getPhone()));
    }

    private static String firstNonBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first.trim() : StringUtils.trimToNull(second);
    }

    @PostMapping("/dashboard/offer/new")
    public String createOfferWithContact(@ModelAttribute("contactDetails") ContactDetails contactDetails,
                                         @RequestParam String intent,
                                         @RequestParam(required = false) String sourceId,
                                         @RequestParam(required = false) String gclid,
                                         @RequestParam(required = false) String offerName,
                                         RedirectAttributes redirectAttributes,
                                         Locale locale) {
        Optional<Basket> source = sourceOf(intent, sourceId);
        if (!contactDetails.isProperlyFilled()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("offers.contact.invalid", null, locale));
            return "redirect:" + UriComponentsBuilder.fromPath("/dashboard/offer/new")
                    .queryParam("intent", intent)
                    .queryParamIfPresent("sourceId", Optional.ofNullable(sourceId))
                    .encode().build().toUriString();
        }

        Basket basket = source.map(s -> s.deepCopy(" - Copy", BasketType.Offer))
                .orElseGet(() -> offerBuilder().build());
        basket.setName(offerName);
        basket.setContactDetails(contactDetails);
        basket.setGclid(gclid);
        save(basket);
        return "redirect:/dashboard/offer/" + basket.getBasketId();
    }

    @PostMapping("/dashboard/offer/{offerId}/saveAsTemplate")
    public String saveAsTemplate(@PathVariable String offerId, @ModelAttribute Basket basket, Model model) {
        Basket templateBasket = offerBuilder()
                .withType(BasketType.OfferTemplate)
                .withName("Template based on: " + basket.getName())
                .withFulfilmentType(basket.getFulfilmentType())
                .withBasketItems(normalizeBlankCategories(basket.getBasketItems())).build();
        save(templateBasket);

        return "redirect:/dashboard/offer/" + templateBasket.getBasketId();
    }

    static final String LIST_PATH = "/dashboard/offers";

    /** Without JavaScript the delete link opens this page; with it, confirm-dialog.js posts to the same address. */
    @GetMapping("/dashboard/offer/{offerId}/delete")
    public String confirmDeleteOffer(@PathVariable String offerId, @RequestParam(required = false) String returnTo,
                                     Model model, Locale locale) {
        Basket offer = deletable(offerId);
        String back = safeReturnTo(returnTo);
        boolean template = offer.hasType(BasketType.OfferTemplate);
        String name = displayName(offer, locale);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage(template ? "offers.delete.confirm.titleTemplate" : "offers.delete.confirm.title", new Object[]{name}, locale),
                messageSource.getMessage(template ? "offers.delete.confirm.messageTemplate" : "offers.delete.confirm.message", null, locale),
                messageSource.getMessage("offers.delete.confirm.action", null, locale),
                "/dashboard/offer/" + offerId + "/delete?returnTo=" + URLEncoder.encode(back, StandardCharsets.UTF_8),
                back));
        model.addAttribute("backLabel", messageSource.getMessage("nav.offer", null, locale));
        return "settings-confirm";
    }

    @PostMapping("/dashboard/offer/{offerId}/delete")
    public String deleteOffer(@PathVariable("offerId") String offerId, @RequestParam(required = false) String returnTo,
                              RedirectAttributes redirectAttributes, Locale locale) {
        Basket offer = deletable(offerId);
        basketsRepository.delete(offer);
        String name = displayName(offer, locale);
        redirectAttributes.addFlashAttribute("offerNotice", messageSource.getMessage(
                offer.hasType(BasketType.OfferTemplate) ? "offers.template.deleted" : "offers.deleted", new Object[]{name}, locale));
        return "redirect:" + safeReturnTo(returnTo);
    }

    private String displayName(Basket offer, Locale locale) {
        return displayName(offer, messageSource, locale);
    }

    /** Same fallback as the list rows, so the dialogs, the confirm pages and the notices name a record alike. */
    public static String displayName(Basket offer, MessageSource messages, Locale locale) {
        if (StringUtils.isNotBlank(offer.getName())) {
            return offer.getName();
        }
        String key = offer.hasType(BasketType.OfferTemplate) ? "offers.list.untitledTemplate" : "offers.list.untitled";
        return messages.getMessage(key, new Object[]{offer.getShortenedBasketId()}, locale);
    }

    /** The offer page, keeping the list it was opened from when that is a list address (see safeReturnTo). */
    public static String offerPageHref(String offerId, String returnTo) {
        return offerHref(offerId, "", returnTo);
    }

    /** An address of the offer page (path: "" or "/recalculate", …) that keeps the list it was opened from. */
    public static String offerHref(String offerId, String path, String returnTo) {
        boolean keep = returnTo != null && safeReturnTo(returnTo).equals(returnTo);
        return "/dashboard/offer/" + offerId + path
                + (keep ? "?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8) : "");
    }

    /** An unknown offer, or one of another store, is a 404 instead of the 500 of Optional.get(). */
    private Basket requireBasket(String offerId) {
        return basketsRepository.findById(getStoreId(), offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Basket deletable(String offerId) {
        Basket offer = basketsRepository.findById(getStoreId(), offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (offer.hasType(BasketType.Basket)) {
            // store baskets are only previewed; the list offers no delete for them, this is a guard
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return offer;
    }

    /** Back to the list the delete came from, never to another address (no open redirect, no header injection). */
    public static String safeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.contains("\r") || returnTo.contains("\n")
                // RedirectView expands {…} as URI templates, which would fail after the delete already happened
                || returnTo.contains("{") || returnTo.contains("}")) {
            return LIST_PATH;
        }
        return returnTo.equals(LIST_PATH) || returnTo.startsWith(LIST_PATH + "?") ? returnTo : LIST_PATH;
    }

    @PostMapping("/dashboard/offer/{offerId}/copy")
    public String duplicateOffer(@PathVariable String offerId,
                                 @RequestParam(defaultValue = "false") boolean withContact) {
        if (!withContact) {
            return "redirect:/dashboard/offer/new?intent=copy&sourceId=" + offerId;
        }
        Basket source = requireBasket(offerId);
        Basket copy = source.deepCopy(" - Copy", BasketType.Offer);
        copy.setContactDetails(source.getContactDetails());
        copy.setGclid(source.getGclid());
        save(copy);
        return "redirect:/dashboard/offer/" + copy.getBasketId();
    }

    @PostMapping("/dashboard/offer/{offerId}/recalculate")
    public String recalculateOffer(@PathVariable String offerId, @RequestParam(required = false) String returnTo,
                                   RedirectAttributes redirectAttributes, Locale locale) {
        Basket basket = requireBasket(offerId);
        offerItemReloader.recalculate(basket);
        save(basket);
        // the page looks the same before and after unless a price changed: it says the recalculation ran
        redirectAttributes.addFlashAttribute("offerNotice",
                messageSource.getMessage("offers.details.saved.recalculated", null, locale));
        return "redirect:" + offerPageHref(offerId, returnTo);
    }

    @PostMapping("/dashboard/offer/{offerId}/createProformaInvoice")
    public String createProformaInvoice(@PathVariable String offerId, @RequestParam(required = false) String returnTo,
                                        Locale locale, RedirectAttributes redirectAttributes) {
        Basket basket = requireBasket(offerId);

        InvoicingService.OperationResult op = invoicingService.createProforma(basket, locale, false);

        if (op.hasError()) {
            redirectAttributes.addFlashAttribute("errorMessage", op.getErrorMessage());
            return "redirect:" + offerPageHref(offerId, returnTo);
        }

        return "redirect:" + op.getInvoiceUrl();
    }

    /** Without JavaScript the dialog posts here and the entries are saved at once; with it the table drafts them. */
    @PostMapping("/dashboard/offer/{offerId}/add-items")
    public String addOfferItems(@PathVariable String offerId, @ModelAttribute AddItemsForm form,
                                @RequestParam(required = false) String returnTo, RedirectAttributes redirectAttributes,
                                Locale locale) {
        Basket basket = requireBasket(offerId);
        int skipped = offerItemsDraft.add(basket, form);
        save(basket);
        if (skipped > 0) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("offers.details.items.skipped",
                    new Object[]{skipped, form.entries().size()}, locale));
        }
        return "redirect:" + offerPageHref(offerId, returnTo);
    }

    /**
     * index is the item's position when the page was rendered and itemId the item that sat there: when the offer changed
     * since and the position is gone or holds another item, nothing is removed. A blank id on either side (a page from
     * before the check, an old item without one) removes by the position alone, as before.
     */
    @PostMapping("/dashboard/offer/{offerId}/remove-item/{index}")
    public String removeOfferItem(@PathVariable String offerId, @PathVariable int index,
                                  @RequestParam(required = false) String itemId,
                                  @RequestParam(required = false) String returnTo,
                                  RedirectAttributes redirectAttributes, Locale locale) {
        Basket offer = requireBasket(offerId);
        List<BasketItem> items = offer.getBasketItems();
        boolean inRange = index >= 0 && index < items.size();
        if (!inRange || otherItem(items.get(index), itemId)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("offers.details.line.removed.stale", null, locale));
            return "redirect:" + offerPageHref(offerId, returnTo);
        }
        offer.removeBasketItem(index);
        save(offer);
        return "redirect:" + offerPageHref(offerId, returnTo);
    }

    private static boolean otherItem(BasketItem item, String itemId) {
        return StringUtils.isNotBlank(itemId) && StringUtils.isNotBlank(item.getId()) && !itemId.equals(item.getId());
    }

    @PostMapping("/dashboard/offer/new/csv")
    public String createOfferFromImport(@ModelAttribute OfferCreationDto dto,
                                        RedirectAttributes redirectAttributes,
                                        Locale locale) {

        if (dto.getContactDetails() == null || !dto.getContactDetails().isProperlyFilled()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("offers.contact.invalid", null, locale));
            return "redirect:/dashboard/offer/new/csv";
        }

        try {
            dto.setStoreId(getStoreId());
            OfferImporter importer = getImporter(dto.getType());
            List<BasketItem> basketItems = importer.importOffer(dto);

            if (basketItems.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        messageSource.getMessage("offers.invalid.csv.format", null, locale));
                return "redirect:/dashboard/offer/new/csv";
            }

            Basket offer = offerBuilder()
                    .withName(dto.getOfferName())
                    .withBasketItems(basketItems)
                    .withContactDetails(dto.getContactDetails())
                    .withGclid(dto.getGclid())
                    .build();

            save(offer);
            return "redirect:/dashboard/offer/" + offer.getBasketId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("offers.csv.processing.error", null, locale) + ": " + e.getMessage());
            return "redirect:/dashboard/offer/new/csv";
        }
    }

    public OfferImporter getImporter(String type) {
        return offerImporters.stream().filter(i -> i.getType().equals(type)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown order reference type: " + type));
    }

    private Basket save(Basket basket) {
        stampSource(basket);
        basketsRepository.save(basket);
        return basket;
    }

    /** An offer without a source gets the user who last saved it. */
    public static void stampSource(Basket basket) {
        if (basket.getType() == BasketType.Offer && basket.getSource() == null) {
            basket.setSource(new OrderSource(CustomSecurityContext.getLoggedInUserName(), OrderSourceType.CallCenter));
        }
    }

    public static List<BasketItem> normalizeBlankCategories(List<BasketItem> items) {
        items.forEach(item -> {
            if (StringUtils.isBlank(item.getCategory())) {
                item.setCategory(null);
            }
        });
        return items;
    }

    private Basket.Builder offerBuilder() {
        Store store = storesRepository.findById(getStoreId());
        return Basket.builder(store).withType(BasketType.Offer);
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }
}