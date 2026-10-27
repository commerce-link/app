package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.commercelink.products.OrphanedProductCleanupService;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivationService;
import pl.commercelink.stores.StoreCreationService;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoreTrialService;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.StoreSettingsOverviewFactory;
import pl.commercelink.web.stores.StoresListPageFactory;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The super admin's "copy store" feature is removed (spec D16): its routes must not be mapped any more. */
@ExtendWith(MockitoExtension.class)
class StoreCopyRoutesGoneTest {

    @Mock private StoresRepository storesRepository;
    @Mock private MessageSource messageSource;
    @Mock private OrphanedProductCleanupService orphanedProductCleanupService;
    @Mock private StoreDeletionService storeDeletionService;
    @Mock private StoreCreationService storeCreationService;
    @Mock private StoreSettingsOverviewFactory storeSettingsOverviewFactory;
    @Mock private StoreTrialService storeTrialService;
    @Mock private StoresListPageFactory storesListPageFactory;
    @Mock private StoreActivationService storeActivationService;
    @InjectMocks private SuperAdminController controller;

    @Test
    void copyRoutesAreGone() throws Exception {
        // given: the source store exists, so a surviving route could not answer 404 for a missing store
        Store store = new Store();
        store.setStoreId("abc123def4");
        store.setName("Madman Gaming");
        lenient().when(storesRepository.findById(anyString())).thenReturn(store);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        // when / then
        mvc.perform(get("/dashboard/store/abc123def4/copy")).andExpect(status().isNotFound());
        mvc.perform(post("/dashboard/store/abc123def4/copy").param("newStoreName", "Copy")).andExpect(status().isNotFound());
    }
}
