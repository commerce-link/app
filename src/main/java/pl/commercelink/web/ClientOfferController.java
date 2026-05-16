package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
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
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.invoicing.api.SplitPaymentPolicy;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.payments.api.PaymentProviderDescriptor;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.ClientDataDto;
import pl.commercelink.web.dtos.PaymentOptionView;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static pl.commercelink.invoicing.api.Price.DEFAULT_VAT_RATE;

@Controller
@RequestMapping("/store/{storeId}/individual/offer/{offerId}")
public class ClientOfferController {

    private static final String PAYMENT_PICKER_FLAG_VALUE = "all";

    @Autowired
    private BasketsRepository basketsRepository;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private Checkout checkout;

    @Autowired
    private InvoicingService invoicingService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private PaymentProviderFactory paymentProviderFactory;

    @GetMapping("")
    public String getOfferForClient(@PathVariable("storeId") String storeId,
                                    @PathVariable("offerId") String offerId,
                                    @RequestParam(name = "payments", required = false) String payments,
                                    Model model) {
        Optional<Basket> existingOfferOpt = basketsRepository.findById(storeId, offerId);
        if (!existingOfferOpt.isPresent()) {
            model.addAttribute("error", "Offer not found");
            return "error";
        }
        Basket existingOffer = existingOfferOpt.get();

        Store store = storesRepository.findById(storeId);

        ClientDataDto form = new ClientDataDto();
        form.setBillingDetails(BillingDetails._default());
        form.setShippingDetails(ShippingDetails._default());

        boolean isSplitPaymentRequired = existingOffer.getBillingDetails() != null && SplitPaymentPolicy.isRequired(
                existingOffer.getBillingDetails().toBillingParty(),
                existingOffer.getTotalPrice(),
                store.getInvoicingConfiguration().isSplitPaymentsEnabled()
        );

        DeliveryOption deliveryOption = existingOffer.resolveDeliveryOption(store).orElse(null);
        double totalPrice = existingOffer.getTotalPrice() + existingOffer.getDeliveryPrice(store);
        model.addAttribute("offer", existingOffer);
        model.addAttribute("deliveryOption", deliveryOption);
        model.addAttribute("totalPrice", totalPrice);
        model.addAttribute("totalPriceNet", Price.fromGross(totalPrice, DEFAULT_VAT_RATE).netValue());
        model.addAttribute("store", store);
        model.addAttribute("bankAccount", store.getDefaultBankAccount() != null ? store.getDefaultBankAccount() : null);
        model.addAttribute("branding", store.getBranding() != null ? store.getBranding() : new Branding());
        model.addAttribute("form", form);
        model.addAttribute("submitted",  existingOffer.getBillingDetails() != null && existingOffer.getShippingDetails() != null);
        model.addAttribute("isSplitPaymentRequired", isSplitPaymentRequired);
        List<PaymentOptionView> paymentOptions = buildPaymentOptions(store);
        model.addAttribute("paymentOptions", paymentOptions);
        model.addAttribute("defaultPaymentOption", resolveDefaultPaymentOption(paymentOptions));
        model.addAttribute("showPaymentPicker", PAYMENT_PICKER_FLAG_VALUE.equals(payments));

        return "clientOffer";
    }

    private String resolveDefaultPaymentOption(List<PaymentOptionView> paymentOptions) {
        return paymentOptions.stream()
                .filter(PaymentOptionView::isDefault)
                .map(PaymentOptionView::name)
                .findFirst()
                .orElseGet(() -> paymentOptions.isEmpty() ? null : paymentOptions.get(0).name());
    }

    private List<PaymentOptionView> buildPaymentOptions(Store store) {
        return store.getPayments().stream()
                .map(this::toPaymentOptionView)
                .toList();
    }

    private PaymentOptionView toPaymentOptionView(PaymentIntegration integration) {
        PaymentProviderDescriptor descriptor = paymentProviderFactory.getDescriptor(integration.getName());
        String displayName = descriptor != null ? descriptor.displayName() : integration.getName();
        return new PaymentOptionView(integration.getName(), displayName, integration.is_default());
    }

    @PostMapping("/submit")
    public String submitClientOfferForm(@PathVariable("storeId") String storeId,
                                        @PathVariable String offerId,
                                        @RequestParam(name = "payments", required = false) String payments,
                                        @ModelAttribute ClientDataDto clientDataDto) {
        Optional<Basket> basketOpt = basketsRepository.findById(storeId, offerId);
        Basket basket = basketOpt.get();

        basket.setBillingDetails(clientDataDto.getBillingDetails());
        basket.setShippingDetails(clientDataDto.getShippingDetails());

        basketsRepository.save(basket);
        return redirectToOffer(storeId, offerId, payments);
    }

    @PostMapping("/checkout")
    public ModelAndView createPaymentLink(@PathVariable("storeId") String storeId,
                                          @PathVariable("offerId") String offerId,
                                          @RequestParam(name = "paymentOptionId", required = false) String paymentOptionId) {
        CheckoutResponse response = checkout.create(storeId, offerId, paymentOptionId);
        if ("POST".equalsIgnoreCase(response.getMethod())) {
            ModelAndView mv = new ModelAndView("payments/payment-bridge");
            mv.addObject("url", response.getUrl());
            mv.addObject("params", response.getParams());
            return mv;
        }
        return new ModelAndView(new RedirectView(response.getUrl()));
    }

    @PostMapping("/send-proforma-invoice")
    public String createProformaInvoice(@PathVariable("storeId") String storeId,
                                        @PathVariable String offerId,
                                        @RequestParam(name = "payments", required = false) String payments,
                                        Locale locale,
                                        RedirectAttributes redirectAttributes) {
        Basket basket = basketsRepository.findById(storeId, offerId).get();

        InvoicingService.OperationResult op = invoicingService.createProforma(basket, locale, true);

        if (op.hasError()) {
            redirectAttributes.addFlashAttribute("errorMessage", op.getErrorMessage());
            return redirectToOffer(storeId, offerId, payments);
        }

        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("offers.send.invoice.success", null, locale));
        return redirectToOffer(storeId, offerId, payments);
    }

    private String redirectToOffer(String storeId, String offerId, String payments) {
        String base = "redirect:/store/" + storeId + "/individual/offer/" + offerId;
        return PAYMENT_PICKER_FLAG_VALUE.equals(payments) ? base + "?payments=" + PAYMENT_PICKER_FLAG_VALUE : base;
    }

}
