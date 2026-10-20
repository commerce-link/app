package pl.commercelink.orders.rma;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentTrackingStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.storage.FileStorage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * The forms of the RMA details page through the real request binding (@InitBinder with its allowed fields, Spring's
 * checkbox marker, request parameters of a multipart and a urlencoded request), posted the way the templates render them.
 */
@ExtendWith(MockitoExtension.class)
class RMADetailFormBindingTest {

    private static final String STORE_ID = "store-1";
    private static final String RMA_ID = "rma-123456789";

    @Mock private RMARepository rmaRepository;
    @Mock private RMALifecycle rmaLifecycle;
    @Mock private MessageSource messageSource;
    @Mock private FileStorage fileStorage;
    // the remaining collaborators of RMAController are not touched by these endpoints
    @Mock private MarketplaceReturnDecisions marketplaceReturnDecisions;
    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private RMAManager rmaManager;
    @Mock private pl.commercelink.orders.OrderItemsRepository orderItemsRepository;
    @Mock private pl.commercelink.orders.OrdersRMAManager ordersRMAManager;
    @Mock private OpenRmaCoverage openRmaCoverage;
    @Mock private RmaShipmentsViewFactory rmaShipmentsViewFactory;
    @Mock private OrdersRepository ordersRepository;

    @InjectMocks
    private RMAController controller;

    private MockedStatic<CustomSecurityContext> security;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    private static RMA existing() {
        RMA rma = new RMA(STORE_ID);
        rma.setRmaId(RMA_ID);
        rma.setStatus(RMAStatus.Processing);
        rma.setEmail("buyer@example.com");
        rma.setEmailNotificationsEnabled(true);
        rma.setShippingInsurance(10);
        rma.setMedia(new ArrayList<>(List.of(STORE_ID + "/rma/" + RMA_ID + "/photo.jpg")));
        return rma;
    }

    private RMA updated() {
        ArgumentCaptor<RMA> saved = ArgumentCaptor.forClass(RMA.class);
        verify(rmaLifecycle).update(saved.capture());
        return saved.getValue();
    }

    @Test
    void theSettingsFormWithTheNotificationsUntickedTurnsThemOff() throws Exception {
        // given: an unticked checkbox is not posted at all, only Spring's marker of it is
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(existing());
        when(messageSource.getMessage(eq("rma.update.success"), any(), any())).thenReturn("saved");

        // when
        mvc.perform(post("/dashboard/rma/{id}", RMA_ID)
                        .param("status", "Processing").param("email", "buyer@example.com")
                        .param("rejectionReason", "").param("_emailNotificationsEnabled", "on")
                        .param("shippingInsurance", "10.00")
                        .param("existingMedia", STORE_ID + "/rma/" + RMA_ID + "/photo.jpg"))
                .andExpect(redirectedUrl("/dashboard/rma/" + RMA_ID));

        // then
        RMA rma = updated();
        assertThat(rma.isEmailNotificationsEnabled()).isFalse();
        assertThat(rma.getStatus()).isEqualTo(RMAStatus.Processing);
        assertThat(rma.getMedia()).containsExactly(STORE_ID + "/rma/" + RMA_ID + "/photo.jpg");
    }

    @Test
    void theFilesFormWithEveryFileUntickedLeavesNoMedia() throws Exception {
        // given: the files form is multipart, and with every "keep" checkbox unticked no existingMedia is posted
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(existing());

        // when
        mvc.perform(multipart("/dashboard/rma/{id}", RMA_ID)
                        .file(new MockMultipartFile("rmaMedia", "", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[0]))
                        .param("status", "Processing").param("email", "buyer@example.com")
                        .param("emailNotificationsEnabled", "true").param("shippingInsurance", "10"))
                .andExpect(redirectedUrl("/dashboard/rma/" + RMA_ID));

        // then
        RMA rma = updated();
        assertThat(rma.getMedia()).isEmpty();
        assertThat(rma.isEmailNotificationsEnabled()).isTrue();
    }

    @Test
    void aUrlencodedFormReachesTheHandlerThatAlsoTakesFiles() throws Exception {
        // given: the status dialogs post urlencoded to the handler whose rmaMedia is a map of files
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(existing());

        // when
        mvc.perform(post("/dashboard/rma/{id}", RMA_ID)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("status", "Completed").param("email", "buyer@example.com")
                        .param("emailNotificationsEnabled", "true").param("shippingInsurance", "10")
                        .param("existingMedia", STORE_ID + "/rma/" + RMA_ID + "/photo.jpg"))
                .andExpect(redirectedUrl("/dashboard/rma/" + RMA_ID));

        // then
        RMA rma = updated();
        assertThat(rma.getStatus()).isEqualTo(RMAStatus.Completed);
        assertThat(rma.getMedia()).containsExactly(STORE_ID + "/rma/" + RMA_ID + "/photo.jpg");
    }

    @Test
    void aShipmentPostedAtIndexOneWithoutIndexZeroIsKept() throws Exception {
        // given: the shipment being created holds index 0 and is never posted, so the form leaves a gap there
        Shipment inFlight = new Shipment(ShipmentType.Courier);
        inFlight.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()));
        Shipment typed = new Shipment(ShipmentType.Courier);
        typed.setCarrier("DPD");
        typed.setTrackingNo("T-1");
        typed.setShippedAt(LocalDateTime.of(2026, 10, 6, 9, 31, 17));
        typed.setTrackingSubscriptionStatus(ShipmentTrackingStatus.ACTIVE);
        typed.setTrackingSubscriptionId("sub-T-1");
        RMA rma = existing();
        rma.setShipments(new ArrayList<>(List.of(inFlight, typed)));
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);

        // when
        mvc.perform(post("/dashboard/rma/{id}/updateShipments", RMA_ID)
                        .param("shipments[1].type", "Courier").param("shipments[1].carrier", "DPD")
                        .param("shipments[1].trackingNo", "T-1").param("shipments[1].trackingUrl", "")
                        .param("shipments[1].shippedAt", "2026-10-06T09:31").param("shipments[1].deliveredAt", ""))
                .andExpect(redirectedUrl("/dashboard/rma/" + RMA_ID));

        // then
        ArgumentCaptor<RMA> saved = ArgumentCaptor.forClass(RMA.class);
        verify(rmaRepository).save(saved.capture());
        List<Shipment> shipments = saved.getValue().getShipments();
        assertThat(shipments).hasSize(2);
        assertThat(shipments.get(0).getTrackingNo()).isEqualTo("T-1");
        assertThat(shipments.get(0).getTrackingSubscriptionId()).isEqualTo("sub-T-1");
        assertThat(shipments.get(0).getShippedAt()).isEqualTo(LocalDateTime.of(2026, 10, 6, 9, 31, 17));
        assertThat(shipments.get(1)).isSameAs(inFlight);
    }
}
