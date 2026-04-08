package pl.commercelink.checkout;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.orders.DeliveryDays;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.payments.api.PaymentLineItem;
import pl.commercelink.payments.api.PaymentRequest;
import pl.commercelink.payments.api.PaymentShippingItem;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.stores.CheckoutSettings;
import pl.commercelink.stores.InvoicingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class Checkout {

    private static final String REQUIRED_ITEM_MISSING_MESSAGE = "Required item missing: ";

    @Autowired
    private StoresRepository storesRepository;
    @Autowired
    private BasketsRepository basketsRepository;
    @Autowired
    private PricelistRepository pricelistRepository;
    @Autowired
    private ProductCatalogRepository productCatalogRepository;
    @Autowired
    private PaymentProviderFactory paymentProviderFactory;

    @Value("${app.domain}")
    private String appDomain;

    public CheckoutResponse create(String storeId, CheckoutRequest req) {
        Store store = storesRepository.findById(storeId);
        CheckoutSettings checkoutSettings = store.getCheckoutSettings();

        if (req.getBillingDetails() == null || !req.getBillingDetails().isProperlyFilled()) {
            throw new IllegalStateException("Billing details are not properly filled.");
        }

        if (req.getShippingDetails() == null || !req.getShippingDetails().isProperlyFilled()) {
            throw new IllegalStateException("Shipping details are not properly filled.");
        }

        List<CatalogProcessingResult> results = new ArrayList<>();
        for (String catalogId : req.getCatalogIds()) {
            results.add(processCatalog(req, store, checkoutSettings, catalogId));
        }

        Basket basket = createBasket(req, store, results);

        return new CheckoutResponse(createPaymentLink(store, basket, null));
    }

    public CheckoutResponse create(String storeId, String basketId) {
        Store store = storesRepository.findById(storeId);
        Basket basket = basketsRepository.findById(storeId, basketId).orElseThrow(() -> new IllegalStateException("Basket not found"));

        String offerUrl = basket.createOfferUrl(appDomain);

        return new CheckoutResponse(createPaymentLink(store, basket, offerUrl));
    }

    private String createPaymentLink(Store store, Basket basket, String cancelUrlOverride) {
        CheckoutSettings checkoutSettings = store.getCheckoutSettings();

        PaymentRequest paymentRequest = new PaymentRequest(
                store.getName(),
                basket.getBasketId(),
                basket.getBillingDetails().getEmail(),
                checkoutSettings.getCurrency(),
                checkoutSettings.getSuccessUrl(),
                cancelUrlOverride != null ? cancelUrlOverride : checkoutSettings.getCancelUrl(),
                buildPaymentLineItems(store, basket),
                buildShippingOption(store, basket));

        return paymentProviderFactory.get(store).createPaymentLink(paymentRequest);
    }

    private List<PaymentLineItem> buildPaymentLineItems(Store store, Basket basket) {
        InvoicingConfiguration invoicingConfiguration = store.getInvoicingConfiguration();

        List<BasketItem> consolidatedItems = basket.getBasketItems().stream()
                .filter(BasketItem::isConsolidated)
                .filter(i -> !i.isShippingItem())
                .toList();
        List<BasketItem> nonConsolidatedItems = basket.getBasketItems().stream()
                .filter(i -> !i.isConsolidated())
                .filter(i -> !i.isShippingItem())
                .toList();

        List<PaymentLineItem> lineItems = new ArrayList<>();
        if (!consolidatedItems.isEmpty()) {
            lineItems.add(consolidateLineItem(invoicingConfiguration.getPositionsConsolidationPrefix(), consolidatedItems));
        }
        nonConsolidatedItems.stream()
                .sorted(Comparator.comparing(BasketItem::getPrice).reversed())
                .map(item -> new PaymentLineItem(item.getName(), null, (int) (item.getPrice() * 100), (int) item.getQty()))
                .forEach(lineItems::add);

        return lineItems;
    }

    private PaymentLineItem consolidateLineItem(String name, List<BasketItem> items) {
        double totalGross = items.stream().mapToDouble(BasketItem::getTotalPrice).sum();
        String description = items.stream()
                .filter(BasketItem::isProduct)
                .map(i -> i.getQty() == 1 ? i.getName().trim() : i.getQty() + "x " + i.getName().trim())
                .collect(Collectors.joining(", "));
        return new PaymentLineItem(name, description, (int) (totalGross * 100), 1);
    }

    private PaymentShippingItem buildShippingOption(Store store, Basket basket) {
        return basket.resolveDeliveryOption(store).map(opt -> {
            DeliveryDays deliveryDays = DeliveryDays.calculate(store, basket);
            return new PaymentShippingItem(
                    opt.getName(),
                    (int) (opt.getPrice() * 100),
                    deliveryDays.getMinEstimatedDeliveryDays(),
                    deliveryDays.getMaxEstimatedDeliveryDays()
            );
        }).orElse(null);
    }

    private Basket createBasket(CheckoutRequest req, Store store, List<CatalogProcessingResult> results) {
        List<BasketItem> basketItems = results.stream()
                .map(CatalogProcessingResult::getBasketItems)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        Basket basket = Basket.builder(store)
                .withSource(new OrderSource("", OrderSourceType.WebStore))
                .withAffiliateId(req.getAffiliateId())
                .withBillingDetails(req.getBillingDetails())
                .withShippingDetails(req.getShippingDetails())
                .withBasketItems(basketItems)
                .withDeliveryOptionId(req.getDeliveryOptionId())
                .build();

        basketsRepository.save(basket);

        return basket;
    }

    private CatalogProcessingResult processCatalog(CheckoutRequest req, Store store, CheckoutSettings checkoutSettings, String catalogId) {
        ProductCatalog productCatalog = productCatalogRepository.findById(store.getStoreId(), catalogId);

        Pricelist incompletePricelist = pricelistRepository.findTopNPricelist(catalogId, checkoutSettings.getNumberOfAcceptedPricelists()).stream()
                .filter(p -> p.getPricelistId().equals(req.getPricelistId(catalogId)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Pricelist not found for catalog: " + catalogId));

        Pricelist pricelist = pricelistRepository.find(catalogId, incompletePricelist.getPricelistId());

        List<BasketItem> basketItems = req.toBasketItems(catalogId, pricelist);
        validateOrderCompleteness(productCatalog, basketItems);

        return new CatalogProcessingResult(catalogId, basketItems);
    }

    private void validateOrderCompleteness(ProductCatalog productCatalog, List<BasketItem> items) {
        productCatalog.getCategories().stream()
                .filter(CategoryDefinition::isRequiredDuringOrder)
                .forEach(category -> {
                    if (items.stream().noneMatch(item -> item.getCategory() == category.getCategory() && item.getQty() > 0)) {
                        throw new IllegalStateException(REQUIRED_ITEM_MISSING_MESSAGE + category.getCategory());
                    }
                });
    }

    static class CatalogProcessingResult {

        private final String catalogId;
        private final List<BasketItem> basketItems;

        CatalogProcessingResult(String catalogId, List<BasketItem> basketItems) {
            this.catalogId = catalogId;
            this.basketItems = basketItems;
        }

        String getCatalogId() {
            return catalogId;
        }

        List<BasketItem> getBasketItems() {
            return basketItems;
        }
    }

}
