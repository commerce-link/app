package pl.commercelink.baskets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.commercelink.checkout.CheckoutRequest;
import pl.commercelink.invoicing.InvoicingService;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.ObjectIdInvoiceNoDto;

import java.util.List;
import java.util.Locale;
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

    @GetMapping("/{basketId}")
    public ResponseEntity<Basket> getBasket(@PathVariable String storeId, @PathVariable String basketId) {
        return basketsRepository.findById(storeId, basketId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ObjectIdInvoiceNoDto> createBasket(@RequestBody CheckoutRequest req,@PathVariable String storeId) {
        Store store = storesRepository.findById(storeId);

        Basket basket = Basket.builder(store)
                .withSource(new OrderSource("", OrderSourceType.WebStore))
                .build();

        processBasket(basket, req);

        if (req.isSendInvoice()) {
            InvoicingService.OperationResult result = invoicingService.createProforma(basket, Locale.getDefault(), true);
            return ResponseEntity.ok(new ObjectIdInvoiceNoDto(basket.getBasketId(), result.getInvoiceNo()));
        }

        return ResponseEntity.ok(new ObjectIdInvoiceNoDto(basket.getBasketId()));
    }

    @PutMapping("/{basketId}")
    public ResponseEntity<Void> updateBasket(@PathVariable String storeId,
                                             @PathVariable String basketId,
                                             @RequestBody CheckoutRequest req) {
        return basketsRepository.findById(storeId, basketId)
                .map(basket -> {
                    processBasket(basket, req);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void processBasket(Basket basket, CheckoutRequest req) {
        List<BasketItem> items = req.toBasketItems(pricelistRepository);

        basket.setBasketItems(items);
        basket.setDeliveryOptionId(req.getDeliveryOptionId());
        basket.setAffiliateId(req.getAffiliateId());

        if(!Objects.isNull(req.getBillingDetails())){
            basket.setBillingDetails(req.getBillingDetails());
        }
        if(!Objects.isNull(req.getShippingDetails())){
            basket.setShippingDetails(req.getShippingDetails());
        }
        basketsRepository.save(basket);
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
