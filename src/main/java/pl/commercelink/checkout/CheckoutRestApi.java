package pl.commercelink.checkout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

@RestController
class CheckoutRestApi {

    @Value("${application.env}")
    private String env;

    @Autowired
    private Checkout checkout;

    @Autowired
    private StoresRepository storesRepository;

    @GetMapping("/env")
    public String env() {
        return env;
    }

    @PostMapping("/Store/{storeId}/Checkout")
    public CheckoutResponse createCheckoutSession(
            @RequestBody CheckoutRequest req,
            @PathVariable("storeId") String storeId) {
        return checkout.create(storeId, req);
    }

    @GetMapping("/Store/{storeId}/Checkout/DeliveryOptions")
    public List<DeliveryOption> getDeliveryOptions(@PathVariable("storeId") String storeId) {
        Store store = storesRepository.findById(storeId);
        return store.getCheckoutSettings().getDeliveryOptions();
    }

}
