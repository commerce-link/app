package pl.commercelink.warehouse.builtin;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.inventory.supplier.SupplierChoice;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarehouseItemNewControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private TaxonomyCache taxonomy;
    @Mock
    private SupplierChoice supplierChoice;
    @Mock
    private WarehouseInternalReceiptService receipts;
    @Mock
    private StoresRepository stores;
    @Mock
    private SupplierLabels labels;
    @Mock
    private StoreCategories categories;
    @Mock
    private Store store;
    @Mock
    private SupplierLabelMap labelMap;
    @InjectMocks
    private WarehouseItemController controller;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        ReflectionTestUtils.setField(controller, "messageSource", messages);
        when(stores.findById("store-1")).thenReturn(store);
        when(labels.forStoreId("store-1")).thenReturn(labelMap);
        when(categories.groupsFor("store-1")).thenReturn(List.of());
        when(supplierChoice.resolve(any(), eq("Acme"), any())).thenReturn(new SupplierChoice.Resolution("Acme", null, null));
    }

    private static WarehouseItemAddForm form(String mfn) {
        WarehouseItemAddForm form = new WarehouseItemAddForm();
        form.setManufacturerCode(mfn);
        form.setCost("689,00");
        form.setQty("2");
        form.setStatus("New");
        form.setSupplier("Acme");
        return form;
    }

    private <T> T asStore(Callable<T> call) throws Exception {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            security.when(CustomSecurityContext::getLoggedInUserName).thenReturn("operator");
            return call.call();
        }
    }

    private static Taxonomy known() {
        return new Taxonomy("5900000000065", "GV-N406TWF2OC", "Gigabyte", "RTX 4060", "Karty graficzne", 100, null, null);
    }

    @Test
    void successRedirectsAfterPost() throws Exception {
        // given
        when(taxonomy.findByMfn(any())).thenReturn(known());
        when(receipts.addItem(eq("store-1"), any(), eq("operator"))).thenReturn(OperationResult.success());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = asStore(() -> controller.addItem(form("GV-N406TWF2OC"), new ExtendedModelMap(), PL,
                new MockHttpServletResponse(), redirect));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/warehouse?statuses=New");
        assertThat(redirect.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("Dodano pozycję RTX 4060.");
    }

    @Test
    void unknownMfnReturns422WithProductDataAndKeepsValues() throws Exception {
        // given
        when(taxonomy.findByMfn(any())).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = asStore(() -> controller.addItem(form("XPG-S70-2TB"), model, PL, response, new RedirectAttributesModelMap()));

        // then
        assertThat(view).isEqualTo("warehouse-item-new");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(model.get("mfnUnknown")).isEqualTo(true);
        assertThat(((WarehouseItemAddForm) model.get("form")).getCost()).isEqualTo("689,00");
        verifyNoInteractions(receipts);
    }

    @Test
    void unknownMfnWithNameAndEanIsSaved() throws Exception {
        // given
        when(taxonomy.findByMfn(any())).thenReturn(null);
        when(receipts.addItem(eq("store-1"), any(), eq("operator"))).thenReturn(OperationResult.success());
        WarehouseItemAddForm form = form("XPG-S70-2TB");
        form.setName("XPG S70 2TB");
        form.setEan("4710886000000");
        form.setCategory("Dyski SSD");

        // when
        String view = asStore(() -> controller.addItem(form, new ExtendedModelMap(), PL,
                new MockHttpServletResponse(), new RedirectAttributesModelMap()));

        // then
        assertThat(view).startsWith("redirect:");
        verify(receipts).addItem(eq("store-1"), argThat(item -> "XPG S70 2TB".equals(item.getName())
                && "Dyski SSD".equals(item.getCategory()) && item.getQty() == 2), eq("operator"));
    }

    @Test
    void serviceFailureShowsTheMessageAndSavesNothing() throws Exception {
        // given
        when(taxonomy.findByMfn(any())).thenReturn(known());
        when(receipts.addItem(any(), any(), any())).thenReturn(OperationResult.failure("Brak danych firmy"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        asStore(() -> controller.addItem(form("GV-N406TWF2OC"), model, PL, response, new RedirectAttributesModelMap()));

        // then
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(model.get("errorMessage")).isEqualTo("Brak danych firmy");
    }

    @Test
    @SuppressWarnings("unchecked")
    void customSupplierRefusalIsAFieldError() throws Exception {
        // given
        when(taxonomy.findByMfn(any())).thenReturn(known());
        when(supplierChoice.resolve(any(), eq("__custom__"), any()))
                .thenReturn(new SupplierChoice.Resolution(null, "order.item.supplier.required", null));
        WarehouseItemAddForm form = form("GV-N406TWF2OC");
        form.setSupplier("__custom__");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        asStore(() -> controller.addItem(form, model, PL, response, new RedirectAttributesModelMap()));

        // then
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat((Map<String, String>) model.get("errors")).containsKey("new-supplier-custom").doesNotContainKey("supplier");
        verifyNoInteractions(receipts);
    }

    @Test
    void forgedStatusOrQuantityIsRefusedAndSavesNothing() throws Exception {
        // given
        when(taxonomy.findByMfn(any())).thenReturn(known());
        WarehouseItemAddForm forgedStatus = form("GV-N406TWF2OC");
        forgedStatus.setStatus("Delivered");
        WarehouseItemAddForm negativeQty = form("GV-N406TWF2OC");
        negativeQty.setQty("-2");

        for (WarehouseItemAddForm forged : List.of(forgedStatus, negativeQty)) {
            MockHttpServletResponse response = new MockHttpServletResponse();

            // when
            String view = asStore(() -> controller.addItem(forged, new ExtendedModelMap(), PL, response, new RedirectAttributesModelMap()));

            // then
            assertThat(view).isEqualTo("warehouse-item-new");
            assertThat(response.getStatus()).isEqualTo(422);
        }
        verifyNoInteractions(receipts);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEmptySupplierChoiceIsAnErrorOfTheSelect() throws Exception {
        // given
        when(taxonomy.findByMfn(any())).thenReturn(known());
        when(supplierChoice.resolve(any(), eq(""), any()))
                .thenReturn(new SupplierChoice.Resolution(null, "order.item.assign.supplier.required", null));
        WarehouseItemAddForm form = form("GV-N406TWF2OC");
        form.setSupplier("");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        asStore(() -> controller.addItem(form, model, PL, new MockHttpServletResponse(), new RedirectAttributesModelMap()));

        // then
        assertThat((Map<String, String>) model.get("errors"))
                .containsEntry("new-supplier", "order.item.assign.supplier.required").doesNotContainKey("supplier");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aTypedNameUnderAnEmptySelectStillKeysTheErrorToTheSelect() throws Exception {
        // given: no JS, the select left empty while the hidden custom name field carries a value
        when(taxonomy.findByMfn(any())).thenReturn(known());
        when(supplierChoice.resolve(any(), eq(""), eq("Hurtownia")))
                .thenReturn(new SupplierChoice.Resolution(null, "order.item.assign.supplier.required", null));
        WarehouseItemAddForm form = form("GV-N406TWF2OC");
        form.setSupplier("");
        form.setCustomSupplier("Hurtownia");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        asStore(() -> controller.addItem(form, model, PL, new MockHttpServletResponse(), new RedirectAttributesModelMap()));

        // then: SupplierChoice reads the typed name only for "Other supplier…", so the error belongs to the select
        assertThat((Map<String, String>) model.get("errors"))
                .containsEntry("new-supplier", "order.item.assign.supplier.required")
                .doesNotContainKey("new-supplier-custom");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theCategoryListLeavesOutUncategorizedWhichTheEmptyChoiceAlreadyMeans() throws Exception {
        // given
        when(categories.groupsFor("store-1")).thenReturn(List.of(
                new StoreCategories.Group("Computers", List.of("SSD drives", "Uncategorized")),
                new StoreCategories.Group("Uncategorized", List.of("Uncategorized"))));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        asStore(() -> controller.newItem(model));

        // then
        assertThat((List<StoreCategories.Group>) model.get("categoryGroups"))
                .containsExactly(new StoreCategories.Group("Computers", List.of("SSD drives")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSecondSubmitOfAnUnknownCodeWithoutProductDataNamesTheMissingFields() throws Exception {
        // given
        when(taxonomy.findByMfn(any())).thenReturn(null);
        WarehouseItemAddForm form = form("XPG-S70-2TB");
        form.setProductDataShown(true);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        asStore(() -> controller.addItem(form, model, PL, response, new RedirectAttributesModelMap()));

        // then
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat((Map<String, String>) model.get("errors"))
                .containsEntry("name", "warehouse.item.new.error.name").containsEntry("ean", "warehouse.item.new.error.ean");
        verifyNoInteractions(receipts);
    }
}
