package pl.commercelink.web.inventory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.ConcurrentModel;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryBrowseControllerTest {

    private static final String STORE_ID = "store-1";

    @Mock private BrowsePageFactory pageFactory;
    @Mock private AddToCatalogDialogFactory dialogFactory;
    @Mock private CatalogPlacement catalogPlacement;
    @Mock private org.springframework.context.MessageSource messageSource;
    @InjectMocks private InventoryBrowseController controller;
    private MockedStatic<CustomSecurityContext> security;

    @BeforeEach
    void setUp() {
        security = mockStatic(CustomSecurityContext.class);
        signedInAs("ADMIN");
        when(pageFactory.build(any(), any(), anyBoolean(), anyBoolean())).thenReturn(mock(BrowsePage.class));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));
        CatalogPlacement.Target gpu = new CatalogPlacement.Target("c-1", "Podzespoły", "cat-gpu", "Karta graficzna", List.of("11"));
        when(catalogPlacement.forStore(STORE_ID)).thenReturn(new CatalogPlacement.StorePlacement(List.of(gpu), List.of()));
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    private void signedInAs(String role) {
        security.when(CustomSecurityContext::getStoreId).thenReturn("SUPER_ADMIN".equals(role) ? null : STORE_ID);
        security.when(() -> CustomSecurityContext.hasRole(anyString())).thenAnswer(call -> role.equals(call.getArgument(0)));
    }

    @Test
    void browseViewRendersTheInventoryPageInBrowseMode() {
        // given
        ConcurrentModel model = new ConcurrentModel();
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("view", "browse");
        params.add("cat", "11");

        // when
        String view = controller.page(params, model);

        // then
        assertThat(view).isEqualTo("inventory");
        assertThat(model.getAttribute("mode")).isEqualTo("browse");
        assertThat(model.getAttribute("browse")).isNotNull();
        assertThat(model.getAttribute("addDialog")).isNull();
    }

    @Test
    void openAddRendersTheDialogForAnAdminOnly() {
        // given
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("view", "browse");
        params.add("open", "add");
        params.add("ean", "5901000000001");
        AddToCatalogDialog dialog = mock(AddToCatalogDialog.class);
        when(dialogFactory.build(eq(STORE_ID), eq(List.of("5901000000001")), anyString())).thenReturn(dialog);
        ConcurrentModel adminModel = new ConcurrentModel();

        // when
        controller.page(params, adminModel);
        signedInAs("USER");
        ConcurrentModel userModel = new ConcurrentModel();
        controller.page(params, userModel);

        // then
        assertThat(adminModel.getAttribute("addDialog")).isSameAs(dialog);
        assertThat(userModel.getAttribute("addDialog")).isNull();
    }

    @Test
    void fragmentReturnsTheResultsBlock() {
        // when
        String view = controller.results(new LinkedMultiValueMap<>(), new ConcurrentModel());

        // then
        assertThat(view).isEqualTo("fragments/inventory-browse :: results");
    }

    @Test
    void addForwardsToTheCatalogReviewOfTheChosenCategory() {
        // when
        String view = controller.add("c-1/cat-gpu", null, "/dashboard/inventory?view=browse", new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("forward:/dashboard/catalogs/c-1/category/cat-gpu/products/add/review");
    }

    @Test
    void addWithOtherUsesTheSelectedCategory() {
        // when
        String view = controller.add(AddToCatalogDialog.OTHER, "c-1/cat-gpu", null, new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        assertThat(view).startsWith("forward:");
    }

    @Test
    @SuppressWarnings("unchecked")
    void addWithUnknownTargetGoesBackWithAnError() {
        // given
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.add("c-9/nope", null, "/dashboard/inventory?view=browse&cat=11", redirect, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/inventory?view=browse&cat=11");
        assertThat((java.util.Map<String, Object>) redirect.getFlashAttributes()).containsEntry("inventoryError", "inventory.browse.add.noTarget");
    }

    @Test
    void dialogAndAddAreAdminOnly() throws Exception {
        // when
        PreAuthorize dialog = InventoryBrowseController.class
                .getMethod("addDialog", List.class, String.class, org.springframework.ui.Model.class)
                .getAnnotation(PreAuthorize.class);
        PreAuthorize add = InventoryBrowseController.class
                .getMethod("add", String.class, String.class, String.class,
                        org.springframework.web.servlet.mvc.support.RedirectAttributes.class, Locale.class)
                .getAnnotation(PreAuthorize.class);

        // then
        assertThat(dialog.value()).isEqualTo("hasRole('ADMIN')");
        assertThat(add.value()).isEqualTo("hasRole('ADMIN')");
        verifyNoInteractions(dialogFactory);
    }
}
