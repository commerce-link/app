package pl.commercelink.web.rma;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.rma.*;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.dtos.RmaItemForm;
import pl.commercelink.web.settings.SettingsFlash;

import java.util.Locale;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RmaItemControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String STORE = "store-1";
    private static final String RMA_ID = "rma-1";
    private static final String ITEM_ID = "item-1";

    @Mock private RMARepository rmaRepository;
    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private OrderItemsRepository orderItemsRepository;
    @Mock private DeliveriesRepository deliveriesRepository;
    @Mock private SupplierLabels supplierLabels;
    @Mock private RmaItemPageFactory pages;
    @InjectMocks private RmaItemController controller;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        ReflectionTestUtils.setField(controller, "messageSource", messages);
    }

    private static RMA rma(RMAStatus status) {
        RMA rma = new RMA(STORE);
        rma.setRmaId(RMA_ID);
        rma.setOrderId("order-1");
        rma.setStatus(status);
        return rma;
    }

    private static RMAItem item() {
        RMAItem item = new RMAItem();
        item.setRmaId(RMA_ID);
        item.setRmaItemId(ITEM_ID);
        item.setItemId("oi-1");
        item.setName("RTX 4070");
        item.setQty(1);
        item.setStatus(RMAItemStatus.Received);
        item.setReason("Old reason");
        item.setVersion(3L);
        return item;
    }

    private void stored(RMAStatus status, RMAItem item) {
        when(rmaRepository.findById(STORE, RMA_ID)).thenReturn(rma(status));
        when(rmaItemsRepository.findById(RMA_ID, ITEM_ID)).thenReturn(item);
    }

    private static RmaItemForm posted(RMAItem item, String reason) {
        RmaItemForm form = RmaItemForm.of(item);
        form.setReason(reason);
        return form;
    }

    private <T> T asStore(Callable<T> call) throws Exception {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE);
            return call.call();
        }
    }

    private String save(RmaItemForm form, String header, MockHttpServletResponse response, RedirectAttributesModelMap ra) throws Exception {
        return asStore(() -> controller.save(RMA_ID, ITEM_ID, form, header, new ExtendedModelMap(), PL, response, ra));
    }

    @Test
    void unknownOrForeignRmaOrUnknownItemIsNotFound() {
        // given
        when(rmaRepository.findById(STORE, RMA_ID)).thenReturn(null);
        when(rmaRepository.findById(STORE, "rma-2")).thenReturn(rma(RMAStatus.New));
        when(rmaItemsRepository.findById("rma-2", ITEM_ID)).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> asStore(() -> controller.show(RMA_ID, ITEM_ID, new ExtendedModelMap(), PL)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(() -> asStore(() -> controller.show("rma-2", ITEM_ID, new ExtendedModelMap(), PL)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(() -> save(new RmaItemForm(), null, new MockHttpServletResponse(), new RedirectAttributesModelMap()))
                .isInstanceOf(ResponseStatusException.class);
        verify(rmaItemsRepository, never()).save(any());
    }

    @Test
    void unknownItemOnPostIsNotFoundAndSavesNothing() {
        // given
        when(rmaRepository.findById(STORE, RMA_ID)).thenReturn(rma(RMAStatus.ItemsReceived));
        when(rmaItemsRepository.findById(RMA_ID, ITEM_ID)).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> save(new RmaItemForm(), null, new MockHttpServletResponse(), new RedirectAttributesModelMap()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verify(rmaItemsRepository, never()).save(any());
    }

    @Test
    void showSkipsTheDeliveryLookupForAnItemWithoutADelivery() throws Exception {
        // given
        RMAItem item = item();
        item.setDeliveryId("Unknown");
        stored(RMAStatus.ItemsReceived, item);

        // when
        asStore(() -> controller.show(RMA_ID, ITEM_ID, new ExtendedModelMap(), PL));

        // then
        verifyNoInteractions(deliveriesRepository, supplierLabels);
    }

    @Test
    void showLooksTheDeliveryUpByItsTrimmedId() throws Exception {
        // given
        RMAItem item = item();
        item.setDeliveryId(" d-1 ");
        stored(RMAStatus.ItemsReceived, item);

        // when
        asStore(() -> controller.show(RMA_ID, ITEM_ID, new ExtendedModelMap(), PL));

        // then
        verify(deliveriesRepository).findById(STORE, "d-1");
    }

    @Test
    void showRendersThePageWithTheOrderItemAndDelivery() throws Exception {
        // given
        RMAItem item = item();
        item.setDeliveryId("d-1");
        stored(RMAStatus.ItemsReceived, item);
        pl.commercelink.inventory.deliveries.Delivery delivery = new pl.commercelink.inventory.deliveries.Delivery();
        delivery.setProvider("action");
        when(deliveriesRepository.findById(STORE, "d-1")).thenReturn(delivery);
        pl.commercelink.inventory.supplier.SupplierLabelMap labels = mock(pl.commercelink.inventory.supplier.SupplierLabelMap.class);
        when(labels.of("action")).thenReturn("Action S.A.");
        when(supplierLabels.forStoreId(STORE)).thenReturn(labels);

        // when
        String view = asStore(() -> controller.show(RMA_ID, ITEM_ID, new ExtendedModelMap(), PL));

        // then
        assertThat(view).isEqualTo("rma-item");
        verify(orderItemsRepository).findById("order-1", "oi-1");
        verify(pages).page(eq(STORE), any(), same(item), any(), same(delivery), eq("Action S.A."), isNull(), eq(java.util.Map.of()), isNull(), isNull(), eq(PL));
    }

    @Test
    void asyncSaveWritesOnlyTheThreeFieldsAndAnswersTheFragment() throws Exception {
        // given
        RMAItem item = item();
        stored(RMAStatus.ItemsReceived, item);
        RmaItemForm form = posted(item, "New reason");
        form.setSerialNo("S1");
        form.setComment("Checked");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = save(form, "fetch", response, new RedirectAttributesModelMap());

        // then
        assertThat(view).isEqualTo("rma-item :: itemForm");
        assertThat(response.getStatus()).isEqualTo(200);
        verify(rmaItemsRepository).save(argThat(i -> "New reason".equals(i.getReason()) && "S1".equals(i.getSerialNo())
                && "Checked".equals(i.getComment()) && i.getQty() == 1 && i.getStatus() == RMAItemStatus.Received));
        verify(pages).page(eq(STORE), any(), same(item), any(), any(), any(), isNull(), eq(java.util.Map.of()), isNull(), eq("Zapisano pozycję."), eq(PL));
    }

    @Test
    void plainSaveRedirectsToTheItemPageWithTheSavedMessage() throws Exception {
        // given
        RMAItem item = item();
        stored(RMAStatus.ItemsReceived, item);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = save(posted(item, "New reason"), null, new MockHttpServletResponse(), ra);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/rma-1/items/item-1");
        assertThat(ra.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE)).isEqualTo("Zapisano pozycję.");
    }

    @Test
    void invalidFormAnswers422WithTheTypedValues() throws Exception {
        // given
        RMAItem item = item();
        stored(RMAStatus.ItemsReceived, item);
        RmaItemForm form = posted(item, "r".repeat(RmaItemForm.MAX_REASON + 1));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = save(form, "fetch", response, new RedirectAttributesModelMap());

        // then
        assertThat(view).isEqualTo("rma-item :: itemForm");
        assertThat(response.getStatus()).isEqualTo(422);
        verify(pages).page(eq(STORE), any(), same(item), any(), any(), any(), same(form), argThat(e -> e.containsKey("reason")), isNull(), isNull(), eq(PL));
        verify(rmaItemsRepository, never()).save(any());
    }

    @Test
    void stalePageVersionAnswers422WithTheConflictAndSavesNothing() throws Exception {
        // given
        RMAItem item = item();
        stored(RMAStatus.ItemsReceived, item);
        RmaItemForm form = posted(item, "New reason");
        form.setVersion("2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = save(form, null, response, new RedirectAttributesModelMap());

        // then
        assertThat(view).isEqualTo("rma-item");
        assertThat(response.getStatus()).isEqualTo(422);
        verify(pages).page(eq(STORE), any(), same(item), any(), any(), any(), same(form), eq(java.util.Map.of()),
                eq("Pozycję zmieniono w międzyczasie. Odśwież stronę i wprowadź zmiany jeszcze raz."), isNull(), eq(PL));
        verify(rmaItemsRepository, never()).save(any());
    }

    @Test
    void concurrentWriteDuringSaveAnswersTheConflict() throws Exception {
        // given
        RMAItem item = item();
        stored(RMAStatus.ItemsReceived, item);
        doThrow(new ConditionalCheckFailedException("version")).when(rmaItemsRepository).save(any());
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = save(posted(item, "New reason"), "fetch", response, new RedirectAttributesModelMap());

        // then
        assertThat(view).isEqualTo("rma-item :: itemForm");
        assertThat(response.getStatus()).isEqualTo(422);
        verify(pages).page(any(), any(), any(), any(), any(), any(), any(), any(),
                eq("Pozycję zmieniono w międzyczasie. Odśwież stronę i wprowadź zmiany jeszcze raz."), isNull(), eq(PL));
    }

    @Test
    void closedRmaRefusesTheAsyncSaveInTheForm() throws Exception {
        // given
        RMAItem item = item();
        stored(RMAStatus.Completed, item);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = save(posted(item, "New reason"), "fetch", response, new RedirectAttributesModelMap());

        // then
        assertThat(view).isEqualTo("rma-item :: itemForm");
        assertThat(response.getStatus()).isEqualTo(422);
        verify(pages).page(any(), any(), any(), any(), any(), any(), notNull(), any(), eq("RMA jest już zamknięte — zmian nie zapisano."), isNull(), eq(PL));
        verify(rmaItemsRepository, never()).save(any());
    }

    @Test
    void closedRmaRefusesThePlainSaveWithAnErrorOnTheItemPage() throws Exception {
        // given
        RMAItem item = item();
        stored(RMAStatus.Rejected, item);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = save(posted(item, "New reason"), null, new MockHttpServletResponse(), ra);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/rma-1/items/item-1");
        assertThat(ra.getFlashAttributes().get(SettingsFlash.ERROR_MESSAGE)).isEqualTo("RMA jest już zamknięte — zmian nie zapisano.");
        verify(rmaItemsRepository, never()).save(any());
    }

    @Test
    void pageBeforeRedesignStillSavesThroughTheAlias() throws Exception {
        // given
        RMAItem item = item();
        stored(RMAStatus.ItemsReceived, item);
        RmaItemForm form = new RmaItemForm();
        form.setReason("From the old page");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.saveFromPageBeforeRedesign(RMA_ID, ITEM_ID, form, null, new ExtendedModelMap(), PL,
                new MockHttpServletResponse(), ra));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/rma/rma-1/items/item-1");
        verify(rmaItemsRepository).save(argThat(i -> "From the old page".equals(i.getReason())));
    }
}
