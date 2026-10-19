package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.commercelink.orders.rma.RmaListService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.rma.RmaListQuery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class RmaListControllerTest {

    @Mock
    private RmaListService rmaList;
    @InjectMocks
    private RmaListController controller;

    private MockMvc mvc;
    private MockedStatic<CustomSecurityContext> security;

    @BeforeEach
    void setUp() {
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn("s1");
        // the logical view "rma" would otherwise resolve to a path that loops back to /dashboard/rma
        mvc = MockMvcBuilders.standaloneSetup(controller, new WebController())
                .setViewResolvers(new org.springframework.web.servlet.view.UrlBasedViewResolver() {
                    { setViewClass(org.springframework.web.servlet.view.InternalResourceView.class); }
                    @Override
                    protected org.springframework.web.servlet.View createView(String viewName, java.util.Locale locale) throws Exception {
                        if (viewName.startsWith(REDIRECT_URL_PREFIX)) {
                            return super.createView(viewName, locale);
                        }
                        return new org.springframework.web.servlet.view.AbstractView() {
                            @Override
                            protected void renderMergedOutputModel(java.util.Map<String, Object> model, jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
                            }
                        };
                    }
                })
                .build();
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    @Test
    void anOldSearchAddressRedirectsWithoutReadingTheList() throws Exception {
        // when / then
        mvc.perform(get("/dashboard/rma").param("email", "jan@x.pl").param("page", "2"))
                .andExpect(redirectedUrl("/dashboard/rma?q=jan%40x.pl"));
        verifyNoInteractions(rmaList);
    }

    @Test
    void theListAndItsFragmentRenderThePageModel() throws Exception {
        // when / then
        mvc.perform(get("/dashboard/rma")).andExpect(view().name("rma"));
        mvc.perform(get("/dashboard/rma/list")).andExpect(view().name("rma :: results"));
        verify(rmaList, times(2)).page(eq("s1"), any(RmaListQuery.class), any(), any());
    }
}
