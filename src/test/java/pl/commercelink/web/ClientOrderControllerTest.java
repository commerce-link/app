package pl.commercelink.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.CategoryLocalizer;
import pl.commercelink.web.dtos.ClientOrderView;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientOrderControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "3f9a2c1e-7b44-4d0e-9a1f-52c8e0b7d611";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private CategoryLocalizer categoryLocalizer;

    @InjectMocks
    private ClientOrderController controller;

    @Test
    @DisplayName("getOrderForClient renders the status page with the view, store and branding for a trackable order")
    void rendersStatusPageForTrackableOrder() {
        // given
        Order order = order(OrderStatus.Assembly);
        Store store = store();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        Model model = new ExtendedModelMap();

        // when
        String template = controller.getOrderForClient(STORE_ID, ORDER_ID, model);

        // then
        assertThat(template).isEqualTo("clientOrder");
        assertThat(model.getAttribute("store")).isSameAs(store);
        assertThat(model.getAttribute("branding")).isInstanceOf(Branding.class);
        ClientOrderView view = (ClientOrderView) model.getAttribute("view");
        assertThat(view.getShortOrderId()).isEqualTo("3f9a2c1e");
    }

    @Test
    @DisplayName("getOrderForClient returns 404 when the order does not exist")
    void returns404WhenOrderMissing() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        String template = controller.getOrderForClient(STORE_ID, ORDER_ID, new ExtendedModelMap());

        // then
        assertThat(template).isEqualTo("error/404");
        verifyNoInteractions(orderItemsRepository);
    }

    @Test
    @DisplayName("getOrderForClient returns 404 for a completed order so the public link expires with it")
    void returns404ForCompletedOrder() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Completed));

        // when
        String template = controller.getOrderForClient(STORE_ID, ORDER_ID, new ExtendedModelMap());

        // then
        assertThat(template).isEqualTo("error/404");
        verifyNoInteractions(orderItemsRepository);
    }

    @Test
    @DisplayName("getOrderForClient still renders a cancelled order")
    void rendersCancelledOrder() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Cancelled));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        Model model = new ExtendedModelMap();

        // when
        String template = controller.getOrderForClient(STORE_ID, ORDER_ID, model);

        // then
        assertThat(template).isEqualTo("clientOrder");
        assertThat(((ClientOrderView) model.getAttribute("view")).isCancelled()).isTrue();
    }

    @Test
    @DisplayName("getOrderForClient returns 404 when the store has the client order page disabled")
    void returns404WhenClientOrderPageDisabled() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(new Store());

        // when
        String template = controller.getOrderForClient(STORE_ID, ORDER_ID, new ExtendedModelMap());

        // then
        assertThat(template).isEqualTo("error/404");
        verifyNoInteractions(ordersRepository, orderItemsRepository);
    }

    private static Order order(OrderStatus status) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setStatus(status);
        return order;
    }

    private static Store store() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Sklep");
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setClientOrderPageEnabled(true);
        store.setFulfilmentConfiguration(configuration);
        return store;
    }
}
