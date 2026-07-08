package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.demo.DemoStoreDeletionService;
import pl.commercelink.products.OrphanedProductCleanupService;
import pl.commercelink.stores.StoreCopyService;
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
    @Mock private DemoStoreDeletionService demoStoreDeletionService;
    @InjectMocks private SuperAdminController controller;

    private final Locale locale = Locale.forLanguageTag("pl");

    @Test
    void showsSuccessWhenDemoStoreFullyDeleted() {
        // given
        when(demoStoreDeletionService.deleteDemoStore(STORE_ID)).thenReturn(true);
        when(messageSource.getMessage("store.delete.success", null, locale)).thenReturn("Usunięto");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteDemoStore(STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Usunięto", redirectAttributes.getFlashAttributes().get("successMessage"));
        assertNull(redirectAttributes.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void showsErrorWhenCascadeCompletesPartially() {
        // given
        when(demoStoreDeletionService.deleteDemoStore(STORE_ID)).thenReturn(false);
        when(messageSource.getMessage("store.delete.error", null, locale)).thenReturn("Błąd usuwania");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteDemoStore(STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Błąd usuwania", redirectAttributes.getFlashAttributes().get("errorMessage"));
        assertNull(redirectAttributes.getFlashAttributes().get("successMessage"));
    }

    @Test
    void showsErrorWhenDeletionThrows() {
        // given
        when(demoStoreDeletionService.deleteDemoStore(STORE_ID)).thenThrow(new IllegalStateException("not a demo store"));
        when(messageSource.getMessage("store.delete.error", null, locale)).thenReturn("Błąd usuwania");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.deleteDemoStore(STORE_ID, locale, redirectAttributes);

        // then
        assertEquals("redirect:/dashboard/stores", view);
        assertEquals("Błąd usuwania", redirectAttributes.getFlashAttributes().get("errorMessage"));
    }
}
