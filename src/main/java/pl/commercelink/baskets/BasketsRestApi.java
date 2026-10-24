package pl.commercelink.baskets;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.commercelink.checkout.CheckoutRequest;
import pl.commercelink.discounts.DiscountService;
import pl.commercelink.invoicing.InvoicingService;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/Store/{storeId}/Basket")
public class BasketsRestApi {

    @Autowired
    private StoresRepository storesRepository;
    @Autowired
    private BasketsRepository basketsRepository;
    @Autowired
    private InvoicingService invoicingService;
    @Autowired
    private PricelistRepository pricelistRepository;
    @Autowired
    private DiscountService discountService;
    @Value("${commercelink.localization.fallback-locale:#{null}}")
    private Locale fallbackLocale;

    @GetMapping("/{basketId}")
    public ResponseEntity<Basket> getBasket(@PathVariable String storeId, @PathVariable String basketId) {
        return basketsRepository.findById(storeId, basketId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CreatedBasketResponse> createBasket(@RequestBody CheckoutRequest req,@PathVariable String storeId) {
        Store store = storesRepository.findById(storeId);

        Basket basket = Basket.builder(store)
                .withSource(new OrderSource("", OrderSourceType.WebStore))
                .build();

        fillBasket(basket, req);
        Map<BasketItem, Double> listPrices = CreatedBasketResponse.listPrices(basket);
        if (StringUtils.isNotBlank(req.getDiscountCode())) {
            discountService.apply(basket, req.getDiscountCode(), paymentProvider(store, req.getPaymentOptionId()));
        }
        basketsRepository.save(basket);

        String invoiceNo = null;
        if (req.isSendInvoice()) {
            invoiceNo = invoicingService.createProforma(basket, fallbackLocale != null ? fallbackLocale : Locale.getDefault(), true).getInvoiceNo();
        }

        return ResponseEntity.ok(CreatedBasketResponse.of(basket, listPrices, invoiceNo));
    }

    private void fillBasket(Basket basket, CheckoutRequest req) {
        List<BasketItem> items = req.toBasketItems(basket.getStoreId(), pricelistRepository);

        basket.setBasketItems(items);
        basket.setDeliveryOptionId(req.getDeliveryOptionId());
        basket.setAffiliateId(req.getAffiliateId());

        if(!Objects.isNull(req.getBillingDetails())){
            basket.setBillingDetails(req.getBillingDetails());
        }
        if(!Objects.isNull(req.getShippingDetails())){
            basket.setShippingDetails(req.getShippingDetails());
        }
    }

    private static String paymentProvider(Store store, String paymentOptionId) {
        if (StringUtils.isNotBlank(paymentOptionId)) {
            return paymentOptionId;
        }
        return store.getDefaultPaymentIntegration().map(PaymentIntegration::getName).orElse(null);
    }

    @DeleteMapping("/{basketId}")
    public ResponseEntity<Void> deleteBasket(@PathVariable String storeId,
                                             @PathVariable String basketId) {
        return basketsRepository.findById(storeId, basketId)
                .map(basket -> {
                    basketsRepository.delete(basket);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
