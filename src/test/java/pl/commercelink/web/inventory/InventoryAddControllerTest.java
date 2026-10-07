package pl.commercelink.web.inventory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.catalog.ProductsAddReview;
import pl.commercelink.web.dtos.ComboboxOption;
import pl.commercelink.web.dtos.ProductsBulkAddForm;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryAddControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String LIST = "/dashboard/inventory?cat=11&supplier=AB&q2=fan&sort=cost&page=2";

    @Mock private CatalogTargetOptionsFactory optionsFactory;
    @Mock private CatalogPlacement catalogPlacement;
    @Mock private CatalogAccess access;
    @Mock private ProductsAddReview review;
    @Mock private MessageSource messageSource;
    @InjectMocks private InventoryAddController controller;

    private CategoryDefinition gpu;
    private CategoryDefinition cases;
    private CategoryDefinition automatic;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        signIn(Map.of("storeId", STORE_ID, "role", "ADMIN"));
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "Podzespoły");
        catalog.setCatalogId("c-1");
        gpu = category("cat-gpu", "Karta graficzna");
        cases = category("cat-case", "Obudowa");
        automatic = category("cat-auto", "Automatyczna");
        automatic.setType(CategoryDefinitionType.Dynamic);
        catalog.getCategories().addAll(List.of(gpu, cases, automatic));
        when(access.requireCatalog(STORE_ID, "c-1")).thenReturn(catalog);
        when(access.requireCategory(catalog, "cat-gpu")).thenReturn(gpu);
        when(access.requireCategory(catalog, "cat-case")).thenReturn(cases);
        when(access.requireCategory(catalog, "cat-auto")).thenReturn(automatic);
        // the cached placement may still list a category that has become automatic since
        when(catalogPlacement.forStore(STORE_ID)).thenReturn(new CatalogPlacement.StorePlacement(List.of(
                target("cat-gpu", "Karta graficzna"), target("cat-case", "Obudowa"), target("cat-auto", "Automatyczna")),
                List.of()));
        when(optionsFactory.build(eq(STORE_ID), anyList())).thenReturn(options("c-1/cat-gpu"));
        when(review.prepare(eq(STORE_ID), any(), anyList(), any())).thenReturn(
                new ProductsAddReview.Prepared(ProductsBulkAddForm.of(List.of()), List.of(), List.of("5901000000009"), 0));
        when(review.render(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(ProductsAddReview.VIEW);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rowMenuOpensTheReviewForTheMatchingCategoryWithTheWayBackToTheList() throws Exception {
        // when / then
        mvc.perform(get("/dashboard/inventory/add").param("ean", "5901000000001").param("returnTo", LIST))
                .andExpect(status().isOk())
                .andExpect(view().name(ProductsAddReview.VIEW))
                .andExpect(model().attribute("selectedTarget", "c-1/cat-gpu"))
                .andExpect(model().attribute("returnTo", LIST))
                .andExpect(model().attribute("backHref", LIST))
                .andExpect(model().attribute("skippedBefore", 1))
                .andExpect(model().attribute("saveAction", "/dashboard/inventory/add/save"))
                .andExpect(model().attribute("changeAction", "/dashboard/inventory/add"));
        verify(review).prepare(STORE_ID, gpu, List.of("5901000000001"), null);
    }

    @Test
    void productsWithoutAMatchingCategoryGetTheEmptyFieldAndNoRows() throws Exception {
        // given
        when(optionsFactory.build(eq(STORE_ID), anyList())).thenReturn(options(null));

        // when / then
        mvc.perform(get("/dashboard/inventory/add").param("ean", "5901000000001").param("returnTo", LIST))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedTarget", ""))
                .andExpect(model().attribute("errors", Map.of()))
                .andExpect(model().attributeDoesNotExist("category"));
        verify(review, never()).prepare(any(), any(), any(), any());
    }

    @Test
    void categoryOfAnotherStoreOrAnAutomaticOneIsAnErrorOfTheField() throws Exception {
        // when / then
        for (String foreign : List.of("c-9/cat-other", "c-1/cat-auto")) {
            mvc.perform(get("/dashboard/inventory/add").param("ean", "5901000000001").param("target", foreign))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("errors", hasKey(InventoryAddController.TARGET_FIELD)))
                    .andExpect(model().attribute("selectedTarget", ""))
                    .andExpect(model().attribute("returnTo", "/dashboard/inventory"));
        }
        verify(review, never()).prepare(any(), any(), any(), any());
    }

    @Test
    void checkedRowsOfTheListAreReviewedTogetherOnceEach() throws Exception {
        // when / then
        mvc.perform(post("/dashboard/inventory/add").param("ean", "5901000000001", "5901000000002", "5901000000001")
                        .param("returnTo", LIST))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedTarget", "c-1/cat-gpu"));
        verify(optionsFactory).build(STORE_ID, List.of("5901000000001", "5901000000002"));
        verify(review).prepare(eq(STORE_ID), eq(gpu), eq(List.of("5901000000001", "5901000000002")), any());
    }

    @Test
    void anotherCategoryReviewsTheRowsAgainWithWhatWasTyped() throws Exception {
        // given
        ArgumentCaptor<ProductsBulkAddForm> edited = ArgumentCaptor.forClass(ProductsBulkAddForm.class);

        // when
        mvc.perform(post("/dashboard/inventory/add").param("ean", "5901000000001").param("target", "c-1/cat-case")
                        .param("reviewedTarget", "c-1/cat-gpu").param("reviewId", "rid")
                        .param("products[0].sourceEan", "5901000000001").param("products[0].name", "Typed")
                        .param("returnTo", LIST))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedTarget", "c-1/cat-case"));

        // then
        verify(review).prepare(eq(STORE_ID), eq(cases), eq(List.of("5901000000001")), edited.capture());
        assertThat(edited.getValue().getReviewId()).isEqualTo("rid");
        assertThat(edited.getValue().getProducts()).extracting(ProductsBulkAddForm.Row::getName).containsExactly("Typed");
    }

    /** A pick in the combobox (fetch) gets the redrawn rows alone, with what was typed; the field stays on the page. */
    @Test
    void pickInTheComboboxGetsOnlyTheRedrawnRowsWithWhatWasTyped() throws Exception {
        // given
        ArgumentCaptor<ProductsBulkAddForm> edited = ArgumentCaptor.forClass(ProductsBulkAddForm.class);

        // when
        mvc.perform(post("/dashboard/inventory/add").header("X-Requested-With", "fetch")
                        .param("ean", "5901000000001").param("target", "c-1/cat-case").param("reviewId", "rid")
                        .param("products[0].sourceEan", "5901000000001").param("products[0].name", "Typed")
                        .param("returnTo", LIST))
                .andExpect(status().isOk())
                .andExpect(view().name(ProductsAddReview.REDRAWN_PART))
                .andExpect(model().attribute("partial", true))
                .andExpect(model().attribute("selectedTarget", "c-1/cat-case"))
                .andExpect(model().attribute("skippedBefore", 1))
                .andExpect(model().attribute("returnTo", LIST));

        // then
        verify(review).prepare(eq(STORE_ID), eq(cases), eq(List.of("5901000000001")), edited.capture());
        assertThat(edited.getValue().getReviewId()).isEqualTo("rid");
        assertThat(edited.getValue().getProducts()).extracting(ProductsBulkAddForm.Row::getName).containsExactly("Typed");
    }

    /** "Zmień kategorię" without the script posts the same form without the header and gets the whole page. */
    @Test
    void changeWithoutTheScriptGetsTheWholePage() throws Exception {
        // when / then
        mvc.perform(post("/dashboard/inventory/add").param("ean", "5901000000001").param("target", "c-1/cat-case"))
                .andExpect(status().isOk())
                .andExpect(view().name(ProductsAddReview.VIEW))
                .andExpect(model().attributeDoesNotExist("partial"));
    }

    /** A pick that no longer leads anywhere (no products left) is a redirect: the script then reloads the page. */
    @Test
    void pickWithoutProductsIsStillARedirect() throws Exception {
        // when / then
        mvc.perform(post("/dashboard/inventory/add").header("X-Requested-With", "fetch").param("target", "c-1/cat-case")
                        .param("returnTo", LIST))
                .andExpect(redirectedUrl(LIST));
    }

    /**
     * One flat list: the matching categories first, as the factory ordered them, then the others. The name is the first
     * line; the catalog, with "matches" on a matching one, the grey second line. No "already here" count any more.
     */
    @Test
    void comboboxOptionsPutTheMatchingCategoriesFirstWithTheirCatalogAndMatchesOnTheSecondLine() {
        // given
        CatalogTargetOptions options = new CatalogTargetOptions(3,
                List.of(new CatalogTargetOptions.Option("c-2", "cat-b2b", "Sklep B2B", "Karty", 2),
                        new CatalogTargetOptions.Option("c-1", "cat-gpu", "Podzespoły", "Karta graficzna", 0)),
                List.of(new CatalogTargetOptions.Option("c-1", "cat-case", "Podzespoły", "Obudowa", 3)),
                "c-1/cat-gpu", false, "Karty graficzne");
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0)
                + (call.getArgument(1) == null ? "" : List.of((Object[]) call.getArgument(1)).toString()));

        // when
        List<ComboboxOption> choices = InventoryAddController.targetOptions(options, messageSource, Locale.ENGLISH);

        // then
        assertThat(choices).containsExactly(
                new ComboboxOption("c-2/cat-b2b", "Karty", "catalog.products.review.target.matches.in[Sklep B2B]"),
                new ComboboxOption("c-1/cat-gpu", "Karta graficzna", "catalog.products.review.target.matches.in[Podzespoły]"),
                new ComboboxOption("c-1/cat-case", "Obudowa", "Podzespoły"));
    }

    /** With every manual category in one catalog its name would repeat on every line: only "matches" stays. */
    @Test
    void comboboxOptionsOfASingleCatalogLeaveTheCatalogOut() {
        // given
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));

        // when
        List<ComboboxOption> choices = InventoryAddController.targetOptions(options("c-1/cat-gpu"), messageSource, Locale.ENGLISH);

        // then
        assertThat(choices).containsExactly(
                new ComboboxOption("c-1/cat-gpu", "Karta graficzna", "catalog.products.review.target.matches"),
                new ComboboxOption("c-1/cat-case", "Obudowa", null));
    }

    /** The rows were checked against the category they were drawn for; another one may skip or reset some of them. */
    @Test
    void saveForAnotherCategoryThanTheReviewedOneSavesNothingAndReviewsAgain() throws Exception {
        // when / then
        mvc.perform(validSave().param("target", "c-1/cat-case").param("reviewedTarget", "c-1/cat-gpu"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("targetNotice", "catalog.products.review.target.changed"))
                .andExpect(model().attribute("selectedTarget", "c-1/cat-case"));
        verify(review, never()).save(any(), any(), any());
        verify(review).prepare(eq(STORE_ID), eq(cases), eq(List.of("5901000000001")), any());
    }

    @Test
    void saveAddsToTheChosenCategoryAndGoesBackToTheExactListWithTheNotice() throws Exception {
        // given
        when(review.save(eq(STORE_ID), eq(gpu), any())).thenReturn(1);

        // when / then
        mvc.perform(validSave().param("target", "c-1/cat-gpu").param("reviewedTarget", "c-1/cat-gpu")
                        .param("skippedBefore", "2"))
                .andExpect(redirectedUrl(LIST));
        verify(review).noticeForInventory(any(), eq("c-1"), eq(gpu), eq(1), eq(2), any(Locale.class));
    }

    @Test
    void saveWithAMistakeRendersTheReviewAgainKeepingTheSkippedCount() throws Exception {
        // when / then
        mvc.perform(post("/dashboard/inventory/add/save").param("ean", "5901000000001").param("target", "c-1/cat-gpu")
                        .param("reviewedTarget", "c-1/cat-gpu").param("returnTo", LIST).param("skippedBefore", "3")
                        .param("products[0].name", "").param("products[0].ean", "5901234567890")
                        .param("products[0].manufacturerCode", "m").param("products[0].pricingGroup", "Default"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(model().attribute("skippedBefore", 3))
                .andExpect(model().attribute("selectedTarget", "c-1/cat-gpu"));
        verify(review, never()).save(any(), any(), any());
    }

    @Test
    void saveWithoutAChosenCategoryIsRefusedAtTheField() throws Exception {
        // when / then
        mvc.perform(validSave().param("target", "").param("reviewedTarget", ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(model().attribute("errors", hasKey(InventoryAddController.TARGET_FIELD)));
        verify(review, never()).save(any(), any(), any());
    }

    @Test
    void foreignWayBackIsReplacedByTheListStart() throws Exception {
        // given
        when(review.save(eq(STORE_ID), eq(gpu), any())).thenReturn(1);

        // when / then
        mvc.perform(post("/dashboard/inventory/add/save").param("ean", "5901000000001").param("target", "c-1/cat-gpu")
                        .param("reviewedTarget", "c-1/cat-gpu").param("returnTo", "//evil.com")
                        .param("products[0].name", "X").param("products[0].ean", "5901234567890")
                        .param("products[0].manufacturerCode", "m").param("products[0].pricingGroup", "Default"))
                .andExpect(redirectedUrl("/dashboard/inventory"));
    }

    /** A reload, a bookmark or Back to the save answers with a GET: back to the list, never a 405. */
    @Test
    void getWithoutProductsAndGetOfTheSaveGoBackToTheList() throws Exception {
        // when / then
        mvc.perform(get("/dashboard/inventory/add").param("returnTo", LIST)).andExpect(redirectedUrl(LIST));
        mvc.perform(get("/dashboard/inventory/add/save").param("returnTo", LIST)).andExpect(redirectedUrl(LIST));
        verify(review, never()).prepare(any(), any(), any(), isNull());
    }

    @Test
    void rowLinkCarriesTheEanAndTheEncodedWayBack() {
        // when
        String href = InventoryAddController.href("5901000000001", "/dashboard/inventory?cat=11&q2=a b");

        // then
        assertThat(href).isEqualTo("/dashboard/inventory/add?ean=5901000000001&returnTo=%2Fdashboard%2Finventory%3Fcat%3D11%26q2%3Da+b");
    }

    private MockHttpServletRequestBuilder validSave() {
        return post("/dashboard/inventory/add/save").param("ean", "5901000000001").param("returnTo", LIST)
                .param("products[0].sourceEan", "5901000000001")
                .param("products[0].name", "X").param("products[0].ean", "5901234567890")
                .param("products[0].manufacturerCode", "m").param("products[0].pricingGroup", "Default");
    }

    private static CategoryDefinition category(String id, String name) {
        CategoryDefinition category = new CategoryDefinition().withName(name);
        category.setCategoryId(id);
        category.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        return category;
    }

    private static CatalogPlacement.Target target(String categoryId, String name) {
        return new CatalogPlacement.Target("c-1", "Podzespoły", categoryId, name, List.of("11"));
    }

    private static CatalogTargetOptions options(String preselected) {
        List<CatalogTargetOptions.Option> matching = preselected == null ? List.of()
                : List.of(new CatalogTargetOptions.Option("c-1", "cat-gpu", "Podzespoły", "Karta graficzna", 0));
        return new CatalogTargetOptions(1, matching,
                List.of(new CatalogTargetOptions.Option("c-1", "cat-case", "Podzespoły", "Obudowa", 0)),
                preselected, false, "Karty graficzne");
    }

    private static void signIn(Map<String, String> attributes) {
        CustomUser user = new CustomUser(null, null, attributes);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
