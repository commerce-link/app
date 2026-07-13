package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.products.OrphanedProductCleanupService;
import pl.commercelink.stores.CreateStoreRequest;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreCopyService;
import pl.commercelink.stores.StoreCreationService;
import pl.commercelink.stores.StoreDeletionService;
import pl.commercelink.stores.StoresRepository;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperAdminControllerTest {

    private static final String STORE_ID = "abc123def4";

    @Mock private StoresRepository storesRepository;
    @Mock private MessageSource messageSource;
    @Mock private StoreCopyService storeCopyService;
    @Mock private OrphanedProductCleanupService orphanedProductCleanupService;
    @Mock private StoreDeletionService storeDeletionService;
    @Mock private StoreCreationService storeCreationService;
    @InjectMocks private SuperAdminController controller;

    private final Locale locale = Locale.forLanguageTag("pl");

    @Test
    void showsSuccessWhenDemoStoreFullyDeleted() {
        // given
        when(storeDeletionService.deleteStore(STORE_ID, StoreDeletionService.Guard.ANY)).thenReturn(true);
        when(messageSource.getMessage("store.delete.success", null, locale)).thenReturn("Usunięto");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteStore(STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Usunięto", redirectAttributes.getFlashAttributes().get("successMessage"));
        assertNull(redirectAttributes.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void showsErrorWhenCascadeCompletesPartially() {
        // given
        when(storeDeletionService.deleteStore(STORE_ID, StoreDeletionService.Guard.ANY)).thenReturn(false);
        when(messageSource.getMessage("store.delete.error", null, locale)).thenReturn("Błąd usuwania");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteStore(STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Błąd usuwania", redirectAttributes.getFlashAttributes().get("errorMessage"));
        assertNull(redirectAttributes.getFlashAttributes().get("successMessage"));
    }

    @Test
    void showsErrorWhenDeletionThrows() {
        // given
        when(storeDeletionService.deleteStore(STORE_ID, StoreDeletionService.Guard.ANY))
                .thenThrow(new IllegalStateException("not a demo store"));
        when(messageSource.getMessage("store.delete.error", null, locale)).thenReturn("Błąd usuwania");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteStore(STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Błąd usuwania", redirectAttributes.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void createStoreDelegatesToCreationServiceAndIgnoresClientStoreId() {
        // given
        Store created = new Store();
        created.setStoreId("srv-gen-001");
        when(storeCreationService.createStore(CreateStoreRequest.bare("Nowy sklep", "key-9"))).thenReturn(created);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.createStore("Nowy sklep", "key-9", locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/store/srv-gen-001", view);
        verify(storeCreationService).createStore(CreateStoreRequest.bare("Nowy sklep", "key-9"));
        verifyNoMoreInteractions(storeCreationService);
    }
}
