package pl.commercelink.checkout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.discounts.DiscountService;
import pl.commercelink.orders.DeliveryDays;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.payments.api.PaymentLineItem;
import pl.commercelink.payments.api.PaymentLink;
import pl.commercelink.payments.api.PaymentRequest;
import pl.commercelink.payments.api.PaymentShippingItem;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.InvoicingConfiguration;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
public class Checkout {

    private static final String REQUIRED_ITEM_MISSING_MESSAGE = "Required item missing: ";

    private final StoresRepository storesRepository;
    private final BasketsRepository basketsRepository;
    private final PricelistRepository pricelistRepository;
    private final ProductCatalogRepository productCatalogRepository;
    private final PaymentProviderFactory paymentProviderFactory;
    private final DiscountService discountService;
    private final String appDomain;

    Checkout(StoresRepository storesRepository,
             BasketsRepository basketsRepository,
             PricelistRepository pricelistRepository,
             ProductCatalogRepository productCatalogRepository,
             PaymentProviderFactory paymentProviderFactory,
             DiscountService discountService,
             @Value("${app.domain}") String appDomain) {
        this.storesRepository = storesRepository;
        this.basketsRepository = basketsRepository;
        this.pricelistRepository = pricelistRepository;
        this.productCatalogRepository = productCatalogRepository;
        this.paymentProviderFactory = paymentProviderFactory;
        this.discountService = discountService;
        this.appDomain = appDomain;
    }

    public CheckoutResponse create(String storeId, CheckoutRequest req) {
        Store store = storesRepository.findById(storeId);
        CheckoutConfiguration checkoutConfiguration = store.getCheckoutConfiguration();

        if (req.getBillingDetails() == null || !req.getBillingDetails().isProperlyFilled()) {
            throw new CheckoutException(CheckoutError.BillingDetailsMissing, "billing details are not properly filled");
        }

        if (req.getShippingDetails() == null || !req.getShippingDetails().isProperlyFilled()) {
            throw new CheckoutException(CheckoutError.ShippingDetailsMissing, "shipping details are not properly filled");
        }

        String providerName = paymentProviderName(store, req.getPaymentOptionId());

        List<CatalogProcessingResult> results = new ArrayList<>();
        for (String catalogId : req.getCatalogIds()) {
            results.add(processCatalog(req, store, checkoutConfiguration, catalogId));
        }

        Basket basket = createBasket(req, store, results, providerName);

        return new CheckoutResponse(createPaymentLink(store, basket, null, providerName));
    }

    public CheckoutResponse create(String storeId, String basketId, String paymentOptionId) {
        Store store = storesRepository.findById(storeId);
        Basket basket = basketsRepository.findById(storeId, basketId).orElseThrow(() -> new IllegalStateException("Basket not found"));

        String offerUrl = basket.createOfferUrl(appDomain);

        return new CheckoutResponse(createPaymentLink(store, basket, offerUrl, paymentProviderName(store, paymentOptionId)));
    }

    private PaymentLink createPaymentLink(Store store, Basket basket, String cancelUrlOverride, String providerName) {
        CheckoutConfiguration checkoutConfiguration = store.getCheckoutConfiguration();

        PaymentRequest paymentRequest = new PaymentRequest(
                store.getName(),
                basket.getBasketId(),
                basket.getBillingDetails().getEmail(),
                checkoutConfiguration.getCurrency(),
                checkoutConfiguration.getSuccessUrl(),
                cancelUrlOverride != null ? cancelUrlOverride : checkoutConfiguration.getCancelUrl(),
                buildPaymentLineItems(store, basket),
                buildShippingOption(store, basket));

        try {
            return paymentProviderFactory.get(store, providerName).createPaymentLink(paymentRequest);
        } catch (RuntimeException e) {
            throw new CheckoutException(CheckoutError.PaymentUnavailable,
                    "payment link of basket " + basket.getBasketId() + " via " + providerName + " failed", e);
        }
    }

    private static String paymentProviderName(Store store, String paymentOptionId) {
        if (isBlank(paymentOptionId)) {
            return store.getDefaultPaymentIntegration()
                    .map(PaymentIntegration::getName)
                    .orElseThrow(() -> new CheckoutException(CheckoutError.PaymentUnavailable,
                            "no default payment gateway in store " + store.getStoreId()));
        }
        if (store.getPaymentIntegration(paymentOptionId) == null) {
            throw new CheckoutException(CheckoutError.PaymentOptionNotFound,
                    "payment gateway " + paymentOptionId + " is not connected in store " + store.getStoreId());
        }
        return paymentOptionId;
    }

    private List<PaymentLineItem> buildPaymentLineItems(Store store, Basket basket) {
        InvoicingConfiguration invoicingConfiguration = store.getInvoicingConfiguration();

        List<BasketItem> effectiveItems = basket.getEffectiveBasketItems();
        List<BasketItem> consolidatedItems = effectiveItems.stream()
                .filter(BasketItem::isConsolidated)
                .filter(i -> !i.isShippingItem())
                .toList();
        List<BasketItem> nonConsolidatedItems = effectiveItems.stream()
                .filter(i -> !i.isConsolidated())
                .filter(i -> !i.isShippingItem())
                .toList();

        List<PaymentLineItem> lineItems = new ArrayList<>();
        if (!consolidatedItems.isEmpty()) {
            lineItems.add(consolidateLineItem(invoicingConfiguration.getPositionsConsolidationPrefix(), consolidatedItems));
        }
        nonConsolidatedItems.stream()
                .sorted(Comparator.comparing(BasketItem::getUnitPrice).reversed())
                .map(item -> new PaymentLineItem(item.getName(), null, toMinorUnits(item.getUnitPrice()), (int) item.getQty()))
                .forEach(lineItems::add);

        return lineItems;
    }

    private PaymentLineItem consolidateLineItem(String name, List<BasketItem> items) {
        double totalGross = items.stream().mapToDouble(BasketItem::getTotalPrice).sum();
        String description = items.stream()
                .filter(BasketItem::isProduct)
                .map(i -> i.getQty() == 1 ? i.getName().trim() : i.getQty() + "x " + i.getName().trim())
                .collect(Collectors.joining(", "));
        return new PaymentLineItem(name, description, toMinorUnits(totalGross), 1);
    }

    private static int toMinorUnits(double amount) {
        return Math.toIntExact(Math.round(amount * 100));
    }

    private PaymentShippingItem buildShippingOption(Store store, Basket basket) {
        String deliveryOptionId = basket.getDeliveryOptionId();
        if (deliveryOptionId == null) {
            return null;
        }
        DeliveryOption option = store.getCheckoutConfiguration().findDeliveryOptionById(deliveryOptionId)
                .orElseThrow(() -> new CheckoutException(CheckoutError.DeliveryOptionNotFound,
                        "delivery option " + deliveryOptionId + " not found in store " + store.getStoreId()));
        DeliveryDays deliveryDays = DeliveryDays.calculate(store, basket);
        return new PaymentShippingItem(
                option.getName(),
                toMinorUnits(option.getPrice()),
                deliveryDays.getMinEstimatedDeliveryDays(),
                deliveryDays.getMaxEstimatedDeliveryDays()
        );
    }

    private Basket createBasket(CheckoutRequest req, Store store, List<CatalogProcessingResult> results, String providerName) {
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

        if (!isBlank(req.getDiscountCode())) {
            discountService.apply(basket, req.getDiscountCode(), providerName);
        }

        basketsRepository.save(basket);

        return basket;
    }

    private CatalogProcessingResult processCatalog(CheckoutRequest req, Store store, CheckoutConfiguration checkoutConfiguration, String catalogId) {
        ProductCatalog productCatalog = productCatalogRepository.findById(store.getStoreId(), catalogId);

        Pricelist incompletePricelist = pricelistRepository.findTopNPricelist(store.getStoreId(), catalogId, checkoutConfiguration.getNumberOfAcceptedPricelists()).stream()
                .filter(p -> p.getPricelistId().equals(req.getPricelistId(catalogId)))
                .findFirst()
                .orElseThrow(() -> new CheckoutException(CheckoutError.PricelistOutdated,
                        "pricelist " + req.getPricelistId(catalogId) + " of catalog " + catalogId + " is not among the accepted ones"));

        Pricelist pricelist = pricelistRepository.find(store.getStoreId(), catalogId, incompletePricelist.getPricelistId());

        List<BasketItem> basketItems = req.toBasketItems(catalogId, pricelist);
        validateOrderCompleteness(productCatalog, basketItems);

        return new CatalogProcessingResult(catalogId, basketItems);
    }

    void validateOrderCompleteness(ProductCatalog productCatalog, List<BasketItem> items) {
        productCatalog.getCategories().stream()
                .filter(CategoryDefinition::isRequiredDuringOrder)
                .forEach(category -> {
                    if (items.stream().noneMatch(item -> matchesCategory(item, category) && item.getQty() > 0)) {
                        throw new CheckoutException(CheckoutError.RequiredItemMissing,
                                REQUIRED_ITEM_MISSING_MESSAGE + category.getName(), category.getName());
                    }
                });
    }

    private boolean matchesCategory(BasketItem item, CategoryDefinition category) {
        return Objects.equals(item.getCategory(), category.getName());
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
