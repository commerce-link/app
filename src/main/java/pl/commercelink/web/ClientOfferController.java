package pl.commercelink.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.checkout.Checkout;
import pl.commercelink.checkout.CheckoutResponse;
import pl.commercelink.invoicing.InvoicingService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.clientoffer.ClientOfferDataForm;
import pl.commercelink.web.clientoffer.ClientOfferNotice;
import pl.commercelink.web.clientoffer.ClientOfferPage;
import pl.commercelink.web.clientoffer.ClientOfferPageFactory;
import pl.commercelink.web.dtos.ClientDataDto;
import pl.commercelink.web.dtos.CountryOptions;
import pl.commercelink.web.publicpage.PublicPageFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping({"/store/{storeId}/individual/offer/{offerId}", "/store/{storeId}/client/offer/{offerId}"})
public class ClientOfferController {

    static final String NOTICE = "clientOfferNotice";

    private static final String VIEW = "clientOffer";
    private static final String UNAVAILABLE_VIEW = "client-offer/unavailable";
    private static final String EDIT_DATA = "data";
    private static final int SHORT_ID_LENGTH = 8;
    private static final int VISIBLE_EMAIL_CHARACTERS = 3;

    private final BasketsRepository basketsRepository;
    private final StoresRepository storesRepository;
    private final Checkout checkout;
    private final InvoicingService invoicingService;
    private final MessageSource messageSource;
    private final ClientOfferPageFactory pageFactory;

    @GetMapping("")
    public String getOfferForClient(@PathVariable("storeId") String storeId,
                                    @PathVariable("offerId") String offerId,
                                    @RequestParam(name = "edit", required = false) String edit,
                                    Model model, Locale locale,
                                    HttpServletRequest request, HttpServletResponse response) {
        Optional<Offer> offer = find(storeId, offerId);
        if (offer.isEmpty()) {
            return unavailable(storeId, offerId, model, locale, request, response);
        }
        Basket basket = offer.get().basket();
        Store store = offer.get().store();

        ClientOfferPage page = buildPage(basket, store, storeId, locale);
        ClientOfferDataForm form = ClientOfferDataForm.empty(isPersonalCollection(page));
        fillPage(model, basket, store, page, form, Map.of(), EDIT_DATA.equals(edit), locale);
        return VIEW;
    }

    @PostMapping("/submit")
    public String submitClientOfferForm(@PathVariable("storeId") String storeId,
                                        @PathVariable("offerId") String offerId,
                                        @ModelAttribute ClientDataDto clientDataDto,
                                        @RequestParam(name = "customerType", required = false) String customerType,
                                        @RequestParam(name = "shippingAddress", required = false) String shippingAddress,
                                        @RequestParam(name = "edit", required = false) String edit,
                                        Model model, RedirectAttributes redirectAttributes, Locale locale,
                                        HttpServletRequest request, HttpServletResponse response) {
        Optional<Offer> offer = find(storeId, offerId);
        if (offer.isEmpty()) {
            return unavailable(storeId, offerId, model, locale, request, response);
        }
        Basket basket = offer.get().basket();
        if (basket.isExpired()) {
            return redirectTo(basket);
        }

        Store store = offer.get().store();
        ClientOfferPage page = buildPage(basket, store, storeId, locale);
        ClientOfferDataForm form = ClientOfferDataForm.of(clientDataDto, customerType, shippingAddress,
                isPersonalCollection(page));
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            fillPage(model, basket, store, page, form, errors, EDIT_DATA.equals(edit), locale);
            return VIEW;
        }

        basket.setBillingDetails(form.toBilling());
        basket.setShippingDetails(form.toShipping());
        basketsRepository.save(basket);
        redirectAttributes.addFlashAttribute(NOTICE,
                ClientOfferNotice.ok(messageSource.getMessage("client.offer.notice.saved", null, locale)));
        return redirectTo(basket);
    }

    @PostMapping("/select-variant")
    public String selectVariant(@PathVariable("storeId") String storeId,
                                @PathVariable("offerId") String offerId,
                                @RequestParam String groupId,
                                @RequestParam int position,
                                Model model, Locale locale,
                                HttpServletRequest request, HttpServletResponse response) {
        Optional<Offer> offer = find(storeId, offerId);
        if (offer.isEmpty()) {
            return unavailable(storeId, offerId, model, locale, request, response);
        }
        Basket basket = offer.get().basket();
        if (basket.isExpired()) {
            return redirectTo(basket);
        }

        try {
            basket.selectVariant(groupId, position);
        } catch (IllegalArgumentException e) {
            // a stale page or a crafted form: show the offer as it is instead of an error page
            return redirectTo(basket);
        }
        basketsRepository.save(basket);
        return redirectTo(basket);
    }

    @PostMapping("/checkout")
    public ModelAndView createPaymentLink(@PathVariable("storeId") String storeId,
                                          @PathVariable("offerId") String offerId,
                                          @RequestParam(name = "paymentOptionId", required = false) String paymentOptionId,
                                          RedirectAttributes redirectAttributes, Locale locale,
                                          HttpServletRequest request, HttpServletResponse response) {
        Optional<Offer> offer = find(storeId, offerId);
        if (offer.isEmpty()) {
            Model model = new ExtendedModelMap();
            String view = unavailable(storeId, offerId, model, locale, request, response);
            return new ModelAndView(view, model.asMap(), HttpStatus.NOT_FOUND);
        }
        Basket basket = offer.get().basket();
        if (basket.isExpired() || !isSubmitted(basket)) {
            return new ModelAndView(redirectTo(basket));
        }

        CheckoutResponse checkoutResponse;
        try {
            checkoutResponse = checkout.create(storeId, offerId, paymentOptionId);
        } catch (RuntimeException e) {
            log.error("Payment link failed for offer {} in store {}", offerId, storeId, e);
            redirectAttributes.addFlashAttribute(NOTICE,
                    ClientOfferNotice.bad(messageSource.getMessage("client.offer.notice.payment.failed", null, locale)));
            return new ModelAndView(redirectTo(basket));
        }

        if ("POST".equalsIgnoreCase(checkoutResponse.getMethod())) {
            ModelAndView mv = new ModelAndView("payments/payment-bridge");
            mv.addObject("url", checkoutResponse.getUrl());
            mv.addObject("params", checkoutResponse.getParams());
            mv.addObject("publicPage", PublicPageFactory.of(offer.get().store(), basket.createOfferUrl(""), locale));
            return mv;
        }
        return new ModelAndView(new RedirectView(checkoutResponse.getUrl()));
    }

    @PostMapping("/send-proforma-invoice")
    public String createProformaInvoice(@PathVariable("storeId") String storeId,
                                        @PathVariable("offerId") String offerId,
                                        Locale locale, RedirectAttributes redirectAttributes, Model model,
                                        HttpServletRequest request, HttpServletResponse response) {
        Optional<Offer> offer = find(storeId, offerId);
        if (offer.isEmpty()) {
            return unavailable(storeId, offerId, model, locale, request, response);
        }
        Basket basket = offer.get().basket();
        if (basket.isExpired() || !isSubmitted(basket)) {
            return redirectTo(basket);
        }

        InvoicingService.OperationResult op = invoicingService.createProforma(basket, locale, true);
        if (op.hasError()) {
            // the provider's text is technical and often English, so the customer gets a fixed message instead
            log.warn("Proforma for offer {} in store {} failed: {}", offerId, storeId, op.getErrorMessage());
            redirectAttributes.addFlashAttribute(NOTICE, ClientOfferNotice.bad(
                    messageSource.getMessage("client.offer.notice.proforma.failed", null, locale)));
            return redirectTo(basket);
        }

        String email = maskEmail(basket.getBillingDetails().getEmail());
        redirectAttributes.addFlashAttribute(NOTICE, ClientOfferNotice.ok(
                messageSource.getMessage("client.offer.notice.proforma.sent", new Object[]{email}, locale)));
        return redirectTo(basket);
    }

    /** The notice survives in the page, so anyone with the link would read the full address. */
    static String maskEmail(String email) {
        if (StringUtils.isBlank(email)) {
            return "***";
        }
        String trimmed = email.trim();
        return trimmed.substring(0, Math.min(VISIBLE_EMAIL_CHARACTERS, trimmed.length())) + "***";
    }

    private Optional<Offer> find(String storeId, String offerId) {
        Optional<Basket> basket = basketsRepository.findById(storeId, offerId);
        if (basket.isEmpty()) {
            return Optional.empty();
        }
        Store store = storesRepository.findById(storeId);
        return store == null ? Optional.empty() : Optional.of(new Offer(basket.get(), store));
    }

    private String unavailable(String storeId, String offerId, Model model, Locale locale,
                               HttpServletRequest request, HttpServletResponse response) {
        String path = offerPath(storeId, offerId, request);
        Store store = storesRepository.findById(storeId);
        model.addAttribute("publicPage", store != null
                ? PublicPageFactory.of(store, path, locale)
                : PublicPageFactory.unknownStore(path, locale));
        model.addAttribute("shortId", StringUtils.left(offerId, SHORT_ID_LENGTH));
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return UNAVAILABLE_VIEW;
    }

    /** The GET address of the offer, also when a POST to one of its actions found nothing. */
    private static String offerPath(String storeId, String offerId, HttpServletRequest request) {
        String segment = request.getRequestURI().contains("/individual/offer/") ? "individual" : "client";
        return "/store/" + storeId + "/" + segment + "/offer/" + offerId;
    }

    private ClientOfferPage buildPage(Basket basket, Store store, String storeId, Locale locale) {
        return pageFactory.build(basket, store, storeId.equals(CustomSecurityContext.getStoreId()), locale);
    }

    private static void fillPage(Model model, Basket basket, Store store, ClientOfferPage page,
                                 ClientOfferDataForm form, Map<String, String> errors, boolean editing, Locale locale) {
        model.addAttribute("publicPage", PublicPageFactory.of(store, basket.createOfferUrl(""), locale));
        model.addAttribute("page", page);
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("editing", editing);
        model.addAttribute("countries", CountryOptions.forPicker(form.dto().getBillingDetails().getCountry(), locale));
    }

    private static boolean isPersonalCollection(ClientOfferPage page) {
        return page.delivery() != null && page.delivery().personalCollection();
    }

    private static boolean isSubmitted(Basket basket) {
        return basket.getBillingDetails() != null && basket.getShippingDetails() != null;
    }

    private static String redirectTo(Basket basket) {
        return "redirect:" + basket.createOfferUrl("");
    }

    private record Offer(Basket basket, Store store) {
    }
}
