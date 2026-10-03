package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.warehouse.RestockScope;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseRestockControllerTest {

    @Mock
    private ProductCatalogRepository productCatalogRepository;

    @InjectMocks
    private WarehouseController controller;

    @Test
    void restockPageIsForAdminsOnly() throws Exception {
        // given
        var get = WarehouseController.class.getDeclaredMethod("restockPage", Model.class);

        // when
        String rule = get.getAnnotation(PreAuthorize.class).value();

        // then
        assertThat(rule).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void missingCatalogComesBackToTheFormWith422() {
        // given
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of());
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        ReflectionTestUtils.setField(controller, "messageSource", messages);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");

            // when
            String view = controller.restock(null, null, RestockScope.WholeCatalog, null, false,
                    model, Locale.forLanguageTag("pl"), response);

            // then
            assertThat(view).isEqualTo("warehouse-restock");
            assertThat(response.getStatus()).isEqualTo(422);
            assertThat(((RestockForm) model.get("restock")).error()).isEqualTo("Wybierz katalog.");
        }
    }
}
