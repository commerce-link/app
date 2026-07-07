package pl.commercelink.orders.imports;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.orders.PositionGroup;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.Categorized;
import pl.commercelink.web.dtos.ClientDataDto;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BasketOrderImporterTest {

    private static final String STORE_ID = "store-1";
    private static final String BASKET_REF = "basket-ref";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private BasketsRepository basketsRepository;
    @Mock
    private OrdersManager ordersManager;
    @Mock
    private ClientDataDto dto;

    @InjectMocks
    private BasketOrderImporter basketOrderImporter;

    private MockedStatic<CustomSecurityContext> securityStub;

    @BeforeEach
    void setupStoreId() {
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    @Test
    @DisplayName("delivery line is appended at the end with the fixed delivery band position")
    void deliveryLineIsAppendedAtEndWithDeliveryBandPosition() {
        // given
        DeliveryOption deliveryOption = deliveryOption("Courier", 15.0);
        Store store = storeWithDeliveryOption(deliveryOption);
        Basket basket = basketWith(deliveryOption, basketItem("MFN-1"), basketItem("MFN-2"));
        when(dto.getOrderReference()).thenReturn(BASKET_REF);
        when(dto.getBillingDetails()).thenReturn(BillingDetails._default());
        when(dto.getShippingDetails()).thenReturn(ShippingDetails._default());
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(basketsRepository.findById(STORE_ID, BASKET_REF)).thenReturn(Optional.of(basket));

        // when
        basketOrderImporter._import(STORE_ID, dto);

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(ordersManager).saveWithFulfilment(any(Order.class), itemsCaptor.capture());

        List<OrderItem> savedItems = itemsCaptor.getValue();
        assertThat(savedItems).hasSize(3);
        assertThat(savedItems).extracting(OrderItem::getPosition).containsExactly(0, 1, PositionGroup.DELIVERY_POSITION);

        OrderItem deliveryLine = savedItems.get(savedItems.size() - 1);
        assertThat(deliveryLine.getCategory()).isEqualTo(Categorized.SERVICES);
        assertThat(deliveryLine.getPosition()).isEqualTo(PositionGroup.DELIVERY_POSITION);
    }

    private Store storeWithDeliveryOption(DeliveryOption deliveryOption) {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        CheckoutConfiguration checkoutConfiguration = new CheckoutConfiguration();
        checkoutConfiguration.setDeliveryOptions(List.of(deliveryOption));
        store.setCheckoutConfiguration(checkoutConfiguration);
        return store;
    }

    private Basket basketWith(DeliveryOption deliveryOption, BasketItem... items) {
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setFulfilmentType(FulfilmentType.WarehouseFulfilment);
        for (BasketItem item : items) {
            basket.addBasketItem(item);
        }
        basket.setDeliveryOptionId(deliveryOption.getId());
        return basket;
    }

    private BasketItem basketItem(String mfn) {
        return new BasketItem("pim-" + mfn, "Product " + mfn, mfn,
                Categorized.OTHER, 100.0, 0, 1, null, 3, false);
    }

    private DeliveryOption deliveryOption(String name, double price) {
        DeliveryOption option = new DeliveryOption();
        option.setName(name);
        option.setPrice(price);
        return option;
    }
}
