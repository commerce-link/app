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
import pl.commercelink.orders.OrderArchiveService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.orders.OrderArchiveQuery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class OrdersArchiveControllerTest {

    @Mock
    private OrderArchiveService archive;
    @InjectMocks
    private OrdersArchiveController controller;

    private MockMvc mvc;
    private MockedStatic<CustomSecurityContext> security;

    @BeforeEach
    void setUp() {
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn("s1");
        mvc = MockMvcBuilders.standaloneSetup(controller, new WebController()).build();
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    @Test
    void pageAndFragmentReadTheSessionStore() throws Exception {
        // when / then
        mvc.perform(get("/dashboard/orders/archive").param("q", "kowalski")).andExpect(view().name("orders/archive"));
        mvc.perform(get("/dashboard/orders/archive/list")).andExpect(view().name("orders/archive :: results"));
        verify(archive, org.mockito.Mockito.times(2)).page(eq("s1"), any(OrderArchiveQuery.class), any(), any());
    }

    @Test
    void oldClientsAddressMovesPermanentlyToTheArchive() throws Exception {
        // when / then
        mvc.perform(get("/dashboard/clients").param("email", "jan@x.pl").param("orderedAtStart", "2026-05-01"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/dashboard/orders/archive?q=jan%40x.pl&from=2026-05-01"));
        mvc.perform(get("/dashboard/clients"))
                .andExpect(header().string("Location", "/dashboard/orders/archive"));
    }
}
