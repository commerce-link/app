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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.rma.RmaAddressPage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** The RMA address page through the real request binding: @InitBinder, the posted flag and the flash from "Ship to client". */
@ExtendWith(MockitoExtension.class)
class RMAAddressBindingTest {

    private static final String STORE_ID = "store-1";
    private static final String RMA_ID = "rma-123456789";

    @Mock private RMARepository rmaRepository;
    @Mock private OrdersRepository ordersRepository;
    @Mock private MessageSource messageSource;
    // the remaining collaborators of RMAController are not touched by these endpoints
    @Mock private MarketplaceReturnDecisions marketplaceReturnDecisions;
    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private RMALifecycle rmaLifecycle;
    @Mock private RMAManager rmaManager;
    @Mock private pl.commercelink.orders.OrderItemsRepository orderItemsRepository;
    @Mock private pl.commercelink.orders.OrdersRMAManager ordersRMAManager;
    @Mock private pl.commercelink.starter.storage.FileStorage fileStorage;
    @Mock private OpenRmaCoverage openRmaCoverage;
    @Mock private RmaShipmentsViewFactory rmaShipmentsViewFactory;

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
        ShippingDetails address = new ShippingDetails();
        address.setId("addr-1");
        address.setName("Anna");
        address.setCity("Kraków");
        RMA rma = new RMA(STORE_ID);
        rma.setRmaId(RMA_ID);
        rma.setStatus(RMAStatus.Processing);
        rma.setEmail("buyer@example.com");
        rma.setShippingInsurance(10);
        rma.setShippingDetails(address);
        return rma;
    }

    @Test
    void aSaveStoresOnlyTheAddressWhateverElseIsPosted() throws Exception {
        // given
        RMA rma = existing();
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(rma);
        when(messageSource.getMessage(eq("rma.address.saved"), any(), any())).thenReturn("saved");

        // when
        mvc.perform(post("/dashboard/rma/{id}/updateShippingDetails", RMA_ID)
                        .param("shippingDetails.name", "Jan").param("shippingDetails.streetAndNumber", "Długa 1")
                        .param("shippingDetails.postalCode", "00-001").param("shippingDetails.city", "Warszawa")
                        .param("shippingDetails.country", "pl").param("shippingDetails.email", "jan@example.com")
                        .param("shippingDetails.phone", "600700800").param("shippingDetails.id", "forged")
                        .param("status", "Completed").param("email", "forged@example.com")
                        .param("shippingInsurance", "9999").param("shipments[0].carrier", "forged"))
                .andExpect(redirectedUrl("/dashboard/rma/" + RMA_ID))
                .andExpect(flash().attribute("successMessage", "saved"));

        // then
        ArgumentCaptor<RMA> saved = ArgumentCaptor.forClass(RMA.class);
        verify(rmaRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(RMAStatus.Processing);
        assertThat(saved.getValue().getEmail()).isEqualTo("buyer@example.com");
        assertThat(saved.getValue().getShippingInsurance()).isEqualTo(10);
        assertThat(saved.getValue().getShipments()).isEmpty();
        assertThat(saved.getValue().getShippingDetails().getId()).isEqualTo("addr-1");
        assertThat(saved.getValue().getShippingDetails().getCity()).isEqualTo("Warszawa");
        assertThat(saved.getValue().getShippingDetails().getCountry()).isEqualTo("PL");
    }

    @Test
    void theFlagPostedAfterShipToClientChangesTheSavedMessage() throws Exception {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(existing());
        when(messageSource.getMessage(eq("rma.address.saved.ship.again"), any(), any())).thenReturn("select again");

        // when / then
        mvc.perform(post("/dashboard/rma/{id}/updateShippingDetails", RMA_ID)
                        .param("shippingDetails.name", "Jan").param("shippingDetails.streetAndNumber", "Długa 1")
                        .param("shippingDetails.postalCode", "00-001").param("shippingDetails.city", "Warszawa")
                        .param("shippingDetails.country", "PL").param("shippingDetails.email", "jan@example.com")
                        .param("shippingDetails.phone", "600700800").param(RmaAddressPage.SHIP_TO_CLIENT_PARAM, "true"))
                .andExpect(flash().attribute("successMessage", "select again"));
    }

    @Test
    void theFlashOfShipToClientReachesThePage() throws Exception {
        // given
        when(rmaRepository.findById(STORE_ID, RMA_ID)).thenReturn(existing());

        // when
        RmaAddressPage page = (RmaAddressPage) mvc.perform(get("/dashboard/rma/{id}/shipping", RMA_ID)
                        .flashAttr(RMAController.ADDRESS_INCOMPLETE_FLASH, true))
                .andExpect(view().name("rma/address"))
                .andReturn().getModelAndView().getModel().get("page");

        // then
        assertThat(page.incompleteForShipping()).isTrue();
        assertThat(page.form().errors()).containsKeys("shippingDetails.streetAndNumber", "shippingDetails.phone");
    }
}
