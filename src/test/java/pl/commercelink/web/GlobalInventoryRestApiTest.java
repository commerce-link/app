package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GlobalInventoryRestApiTest {

    @Mock
    private Inventory inventory;
    @Mock
    private InventoryView view;

    @InjectMocks
    private GlobalInventoryRestApi api;

    @Test
    void globalApiLooksUpOnlyByEan() throws Exception {
        // given
        when(inventory.withGlobalData()).thenReturn(view);
        when(view.findByEan("5900000000014")).thenReturn(MatchedInventory.empty(new InventoryKey()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(api).build();

        // when
        mvc.perform(get("/Global/Inventory/AvailabilityAndPrice")
                        .param("manufacturerCode", "MFN-1")
                        .param("ean", "5900000000014"))
                .andExpect(status().isOk());

        // then
        verify(view).findByEan("5900000000014");
    }
}
