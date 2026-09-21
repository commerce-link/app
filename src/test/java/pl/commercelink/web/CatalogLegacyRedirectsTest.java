package pl.commercelink.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogLegacyRedirectsTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CatalogLegacyRedirects()).build();
    }

    @Test
    void productViewsBecomeStatusAndFeatureFilters() throws Exception {
        mvc.perform(get("/dashboard/catalogs/c1/category/k1/products").param("status", "Enabled"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/dashboard/catalogs/c1/category/k1?status=active"));
        mvc.perform(get("/dashboard/catalogs/c1/category/k1/products").param("status", "Queued"))
                .andExpect(redirectedUrl("/dashboard/catalogs/c1/category/k1?status=nopim"));
        mvc.perform(get("/dashboard/catalogs/c1/category/k1/products").param("status", "MarketplaceEligible"))
                .andExpect(redirectedUrl("/dashboard/catalogs/c1/category/k1?status=all&feature=marketplace"));
        mvc.perform(get("/dashboard/catalogs/c1/category/k1/products").param("status", "SuggestedRetailPrice"))
                .andExpect(redirectedUrl("/dashboard/catalogs/c1/category/k1?status=all&feature=srp"));
        mvc.perform(get("/dashboard/catalogs/c1/category/k1/products"))
                .andExpect(redirectedUrl("/dashboard/catalogs/c1/category/k1?status=active"));
    }

    @Test
    void recommendationsAndBulkPagesMoveUnderProductsAdd() throws Exception {
        mvc.perform(get("/dashboard/catalogs/c1/category/k1/recommendations"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/dashboard/catalogs/c1/category/k1/products/add"));
        mvc.perform(get("/dashboard/catalogs/c1/category/k1/products/bulk-new").param("eans", "1"))
                .andExpect(redirectedUrl("/dashboard/catalogs/c1/category/k1/products/add"));
    }

    @Test
    void newProductFromEanBecomesAQueryParameter() throws Exception {
        mvc.perform(get("/dashboard/catalogs/c1/category/k1/products/5900000000001/new"))
                .andExpect(redirectedUrl("/dashboard/catalogs/c1/category/k1/products/new?ean=5900000000001"));
    }
}
