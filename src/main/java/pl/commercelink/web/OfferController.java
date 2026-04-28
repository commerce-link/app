package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.baskets.*;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.invoicing.InvoicingService;
import pl.commercelink.offer.imports.OfferImporter;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.OfferCreationDto;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static pl.commercelink.invoicing.api.Price.DEFAULT_VAT_RATE;

@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
public class OfferController {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private BasketsRepository basketsRepository;

    @Autowired
    private ProductCatalogRepository productCatalogRepository;

    @Autowired
    private PricelistRepository pricelistRepository;

    @Autowired
    private OfferItemReloader offerItemReloader;

    @Autowired
    private Inventory inventory;

    @Autowired
    private InvoicingService invoicingService;

    @Autowired
    private List<OfferImporter> offerImporters;

    @Autowired
    private MessageSource messageSource;

    @Value("${app.domain}")
    private String appDomain;

    private static final int OFFER_PAGE_SIZE = 25;

    @GetMapping("/dashboard/offers")
    public String offers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String basketId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdAtStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdAtEnd,
            @RequestParam(required = false, defaultValue = "1") int page,
            Model model) {
        List<Basket> paginatedOffers = new LinkedList<>();
        boolean hasSearchParams = isNotBlank(name) || isNotBlank(basketId) || isNotBlank(type) || createdAtStart != null || createdAtEnd != null;
        if (hasSearchParams) {
            BasketFilter filter = new BasketFilter(name, basketId, isNotBlank(type) ? BasketType.valueOf(type) : null, createdAtStart, createdAtEnd);
            paginatedOffers = basketsRepository.search(getStoreId(), filter, page, OFFER_PAGE_SIZE);
        }

        List<ProductCatalog> catalogs = productCatalogRepository.findAll(getStoreId());

        HashMap<String, Object> searchParams = new HashMap<>();
        searchParams.put("name", name);
        searchParams.put("basketId", basketId);
        searchParams.put("createdAtStart", createdAtStart);
        searchParams.put("createdAtEnd", createdAtEnd);
        searchParams.put("type", type);

        model.addAttribute("offers", paginatedOffers.subList(0, Math.min(paginatedOffers.size(), OFFER_PAGE_SIZE)));
        model.addAttribute("currentPage", page);
        model.addAttribute("hasNextPage", paginatedOffers.size() > OFFER_PAGE_SIZE);
        model.addAttribute("catalogs", catalogs);
        model.addAttribute("searchParams", searchParams);
        model.addAttribute("basketTypes", BasketType.values());

        return "offers";
    }

    @GetMapping("/dashboard/offer/new/csv")
    public String showOfferImportSelection(Model model) {
        OfferCreationDto form = new OfferCreationDto();
        form.setType("CSV");
        model.addAttribute("form", form);
        return "newOffer_from_csv";
    }

    @GetMapping("/dashboard/offer/new")
    public String showContactDetailsForm(@RequestParam String intent,
                                         @RequestParam(required = false) String sourceId,
                                         Model model) {
        model.addAttribute("contactDetails", new ContactDetails());
        model.addAttribute("intent", intent);
        model.addAttribute("sourceId", sourceId);
        return "offerContact";
    }

    @PostMapping("/dashboard/offer/new")
    public String createOfferWithContact(@ModelAttribute("contactDetails") ContactDetails contactDetails,
                                         @RequestParam String intent,
                                         @RequestParam(required = false) String sourceId,
                                         @RequestParam(required = false) String gclid,
                                         RedirectAttributes redirectAttributes,
                                         Locale locale) {
        if (!contactDetails.isProperlyFilled()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("offers.contact.invalid", null, locale));
            return "redirect:/dashboard/offer/new?intent=" + intent
                    + (sourceId != null ? "&sourceId=" + sourceId : "");
        }

        Basket basket = switch (intent) {
            case "manual" -> offerBuilder().build();
            case "copy", "template" -> copyOf(sourceId);
            default -> throw new IllegalArgumentException("Unknown intent: " + intent);
        };
        basket.setContactDetails(contactDetails);
        basket.setGclid(gclid);
        save(basket);
        return "redirect:/dashboard/offer/" + basket.getBasketId();
    }

    @GetMapping("/dashboard/offer/{offerId}")
    public String showOfferDetails(@PathVariable String offerId, @ModelAttribute("catalogId") String catalogId, Model model) {
        Basket basket = basketsRepository.findById(getStoreId(), offerId).get();
        return showEditOfferForm(model, basket, catalogId, Mode.EDIT, basket.hasType(BasketType.OfferTemplate));
    }

    private String showEditOfferForm(Model model, Basket basket, String catalogId, Mode mode, boolean recalculate) {
        List<OfferItem> offerItems = recalculate ? offerItemReloader.recalculate(getStoreId(), basket) : offerItemReloader.reload(getStoreId(), basket);

        Store store = storesRepository.findById(getStoreId());
        if (Mode.CREATE == mode) {
            basket.setFulfilmentType(store.getDefaultFulfilmentType());
        }
        if (basket.getContactDetails() == null) {
            basket.setContactDetails(new ContactDetails());
        }

        Pricelist pricelist = Pricelist.empty();
        if (isNotBlank(catalogId)) {
            String newestPricelistId = pricelistRepository.findNewestPricelistIdCached(catalogId);
            pricelist = pricelistRepository.find(catalogId, newestPricelistId);
        }

        List<ProductCatalog> catalogs = productCatalogRepository.findAll(getStoreId());

        double deliveryPrice = basket.getDeliveryPrice(store);
        double totalPrice = offerItems.stream().mapToDouble(OfferItem::getTotalPrice).sum() + deliveryPrice;
        double totalCost = offerItems.stream().mapToDouble(OfferItem::getTotalCost).sum();
        double totalProfitGross = totalPrice - totalCost;
        double totalProfitNet = totalProfitGross / DEFAULT_VAT_RATE; // Assuming 23% VAT

        model.addAttribute("mode", mode.name());
        model.addAttribute("offer", basket);
        model.addAttribute("offerItems", offerItems);
        model.addAttribute("productCategories", store.getEnabledProductCategories());
        model.addAttribute("fulfilmentTypes", FulfilmentType.values());
        model.addAttribute("totalPrice", totalPrice);
        model.addAttribute("totalCost", totalCost);
        model.addAttribute("totalProfitGross", Math.round(totalProfitGross * 100.0) / 100.0);
        model.addAttribute("totalProfitNet", Math.round(totalProfitNet * 100.0) / 100.0);
        model.addAttribute("storeId", getStoreId());
        model.addAttribute("backofficeDomain", appDomain);

        model.addAttribute("catalogs", catalogs);
        model.addAttribute("catalogId", catalogId);
        model.addAttribute("pricelistId", pricelist.getPricelistId());
        model.addAttribute("availabilityAndPrices", pricelist.getAvailabilityAndPrices());
        model.addAttribute("availableCategories", pricelist.getAvailableCategories());
        model.addAttribute("deliveryOptions", store.getCheckoutConfiguration().getDeliveryOptions());

        return "offerDetails";
    }

    @PostMapping("/dashboard/offer/{offerId}")
    public String updateOffer(@PathVariable String offerId, @ModelAttribute Basket basket) {
        Basket existingBasket = basketsRepository.findById(getStoreId(), offerId).get();
        existingBasket.setName(basket.getName());
        existingBasket.setFulfilmentType(basket.getFulfilmentType());
        existingBasket.setBasketItems(basket.getBasketItems().stream().filter(BasketItem::isComplete).collect(Collectors.toList()));
        existingBasket.setComment(basket.getComment());
        existingBasket.setShowPrices(basket.isShowPrices());
        existingBasket.setExpiresAt(basket.getExpiresAt());
        existingBasket.setDeliveryOptionId(basket.getDeliveryOptionId());
        existingBasket.setContactDetails(basket.getContactDetails());
        existingBasket.setGclid(basket.getGclid());

        save(existingBasket);
        return "redirect:/dashboard/offer/" + offerId;
    }

    @PostMapping("/dashboard/offer/{offerId}/saveAsTemplate")
    public String saveAsTemplate(@PathVariable String offerId, @ModelAttribute Basket basket, Model model) {
        Basket templateBasket = offerBuilder()
                .withType(BasketType.OfferTemplate)
                .withName("Template based on: " + basket.getName())
                .withFulfilmentType(basket.getFulfilmentType())
                .withBasketItems(basket.getBasketItems()).build();
        save(templateBasket);

        return "redirect:/dashboard/offer/" + templateBasket.getBasketId();
    }

    @PostMapping("/dashboard/offer/{offerId}/delete")
    public String deleteOffer(@PathVariable("offerId") String offerId, Model model) {
        Optional<Basket> existingOfferOpt = basketsRepository.findById(getStoreId(), offerId);
        if (!existingOfferOpt.isPresent()) {
            model.addAttribute("error", "Offer not found");
            return "error";
        }

        Basket existingOffer = existingOfferOpt.get();
        if (existingOffer.hasType(BasketType.Basket)) {
            model.addAttribute("error", "Cannot delete Basket");
            return "error";
        }

        basketsRepository.delete(existingOffer);

        return "redirect:/dashboard/offers";
    }

    @PostMapping("/dashboard/offer/{offerId}/copy")
    public String duplicateOffer(@PathVariable String offerId,
                                 @RequestParam(defaultValue = "false") boolean withContact) {
        if (!withContact) {
            return "redirect:/dashboard/offer/new?intent=copy&sourceId=" + offerId;
        }
        Basket source = basketsRepository.findById(getStoreId(), offerId).get();
        Basket copy = source.deepCopy(" - Copy", BasketType.Offer);
        copy.setContactDetails(source.getContactDetails());
        copy.setGclid(source.getGclid());
        save(copy);
        return "redirect:/dashboard/offer/" + copy.getBasketId();
    }

    @PostMapping("/dashboard/offer/{offerId}/recalculate")
    public String recalculateOffer(@PathVariable String offerId, Model model) {
        Optional<Basket> existingOfferOpt = basketsRepository.findById(getStoreId(), offerId);
        offerItemReloader.recalculate(getStoreId(), existingOfferOpt.get());

        return "redirect:/dashboard/offer/" + offerId;
    }

    @PostMapping("/dashboard/offer/{offerId}/createProformaInvoice")
    public String createProformaInvoice(@PathVariable String offerId, Locale locale, RedirectAttributes redirectAttributes) {
        Basket basket = basketsRepository.findById(getStoreId(), offerId).get();

        InvoicingService.OperationResult op = invoicingService.createProforma(basket, locale, false);

        if (op.hasError()) {
            redirectAttributes.addFlashAttribute("errorMessage", op.getErrorMessage());
            return "redirect:/dashboard/offer/" + offerId;
        }

        return "redirect:" + op.getInvoiceUrl();
    }

    @PostMapping("/dashboard/offer/{offerId}/select-catalog")
    public String onCatalogChange(@PathVariable String offerId, @RequestParam String catalogId, @ModelAttribute Basket offer, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("catalogId", catalogId);
        redirectAttributes.addFlashAttribute("offer", offer);
        redirectAttributes.addFlashAttribute("openModal", true);
        return "redirect:/dashboard/offer/" + offerId;
    }

    @PostMapping("/dashboard/offer/{offerId}/add-item/pricelist")
    public String addOfferItemFromPriceList(@PathVariable String offerId,
                                            @RequestParam("itemCatalogId") String catalogId,
                                            @RequestParam("itemPricelistId") String pricelistId,
                                            @RequestParam("category") String category,
                                            @RequestParam("itemLabel") String itemLabel,
                                            @RequestParam("itemName") String itemName) {
        Basket basket = basketsRepository.findById(getStoreId(), offerId).get();

        Pricelist pricelist = pricelistRepository.find(catalogId, pricelistId);
        List<AvailabilityAndPrice> availabilityAndPrices = pricelist.getAvailabilityAndPrices();

        AvailabilityAndPrice itemAvailabilityAndPrice = availabilityAndPrices.stream()
                .filter(a -> a.getCategory() == ProductCategory.valueOf(category) && a.getLabel().equals(itemLabel) && a.getName().equals(itemName))
                .findFirst().get();

        BasketItem basketItem = BasketItem.of(itemAvailabilityAndPrice, 1, catalogId, !basket.isShowPrices());
        basket.getBasketItems().add(basketItem);
        save(basket);

        return "redirect:/dashboard/offer/" + offerId;
    }

    @PostMapping("/dashboard/offer/{offerId}/add-item/inventory")
    public String addOfferItemFromInventory(@PathVariable String offerId,
                                            @RequestParam(required = false) String itemEan,
                                            @RequestParam(required = false) String itemManufacturerCode) {
        Basket basket = basketsRepository.findById(getStoreId(), offerId).get();

        MatchedInventory matchedInventory = inventory.withEnabledSuppliersOnly(getStoreId())
                .findByInventoryKey(new InventoryKey(itemEan.trim(), itemManufacturerCode.trim()));

        basket.getBasketItems().add(BasketItem.of(matchedInventory, 1, !basket.isShowPrices()));
        save(basket);

        return "redirect:/dashboard/offer/" + offerId;
    }

    @PostMapping("/dashboard/offer/{offerId}/remove-item/{index}")
    public String removeOfferItem(@PathVariable String offerId, @PathVariable int index, Model model) {
        Optional<Basket> offerOpt = basketsRepository.findById(getStoreId(), offerId);
        if (!offerOpt.isPresent()) {
            model.addAttribute("error", "Offer not found");
            return "error";
        }
        Basket offer = offerOpt.get();
        offer.getBasketItems().remove(index);
        save(offer);
        return "redirect:/dashboard/offer/" + offerId;
    }

@GetMapping("/dashboard/basket/view/{basketId}")
    public String viewBasket(@PathVariable String basketId, Model model) {
        Optional<Basket> existingBasketOpt = basketsRepository.findById(getStoreId(), basketId);
        if (!existingBasketOpt.isPresent()) {
            model.addAttribute("error", "Basket not found");
            return "error";
        }
        Basket basket = existingBasketOpt.get();

        List<BasketItem> basketItems = basket.getBasketItems();
        double totalPrice = basketItems.stream()
                .mapToDouble(BasketItem::getTotalPrice)
                .sum();
        double totalCost = basketItems.stream()
                .mapToDouble(BasketItem::getTotalCost)
                .sum();
        double totalProfitGross = totalPrice - totalCost;
        double totalProfitNet = totalProfitGross / DEFAULT_VAT_RATE;

        model.addAttribute("basket", basket);
        model.addAttribute("basketItems", basketItems);
        model.addAttribute("totalPrice", totalPrice);
        model.addAttribute("totalCost", totalCost);
        model.addAttribute("totalProfitGross", Math.round(totalProfitGross * 100.0) / 100.0);
        model.addAttribute("totalProfitNet", Math.round(totalProfitNet * 100.0) / 100.0);

        return "basketView";
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

    private Basket copyOf(String basketId) {
        Basket sourceBasket = basketsRepository.findById(getStoreId(), basketId).get();
        return sourceBasket.deepCopy(" - Copy", BasketType.Offer);
    }

    private Basket save(Basket basket) {
        if (basket.getType() == BasketType.Offer && basket.getSource() == null) {
            basket.setSource(new OrderSource(CustomSecurityContext.getLoggedInUserName(), OrderSourceType.CallCenter));
        }

        basketsRepository.save(basket);

        return basket;
    }

    private Basket.Builder offerBuilder() {
        Store store = storesRepository.findById(getStoreId());
        return Basket.builder(store).withType(BasketType.Offer);
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

    private enum Mode {
        CREATE,
        EDIT
    }
}