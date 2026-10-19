package pl.commercelink.web.activity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import pl.commercelink.starter.security.tenant.PathTenantResolver;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.net.URI;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class PublicStoreActivityInterceptorTest {

    private static final String STORE_ID = "abc123def4";
    private static final String STORE_ID_WITH_ENCODED_FIRST_LETTER = "%61bc123def4";

    @Mock private StoresRepository storesRepository;
    @Mock private StoreActivity storeActivity;

    private final Store store = new Store();
    private MockMvc mvc;

    @Controller
    static class StubPublicController {

        @PostMapping("/Store/{storeId}/Checkout")
        @ResponseBody
        String checkout() {
            return "checkout";
        }

        @GetMapping("/store/{storeId}/client/order/{orderId}")
        String clientOrder() {
            return "clientOrder";
        }

        @GetMapping("/Global/Inventory")
        @ResponseBody
        String globalInventory() {
            return "inventory";
        }
    }

    @BeforeEach
    void setUp() {
        store.setStoreId(STORE_ID);
        PublicStoreActivityInterceptor interceptor =
                new PublicStoreActivityInterceptor(new PathTenantResolver(), storesRepository, storeActivity);
        mvc = MockMvcBuilders.standaloneSetup(new StubPublicController())
                .setControllerAdvice(new InactiveStorePageAdvice())
                .addMappedInterceptors(new String[]{"/Store/*/**", "/store/*/client/**", "/Global/**"}, interceptor)
                .build();
    }

    @Test
    void activeStoreIsServed() throws Exception {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeActivity.isActive(store)).thenReturn(true);

        // when / then
        mvc.perform(post("/Store/" + STORE_ID + "/Checkout"))
                .andExpect(status().isOk())
                .andExpect(content().string("checkout"));
        mvc.perform(get("/store/" + STORE_ID + "/client/order/order-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("clientOrder"));
    }

    @Test
    void unknownStoreIsLeftToTheEndpoint() throws Exception {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);

        // when / then
        mvc.perform(post("/Store/" + STORE_ID + "/Checkout")).andExpect(content().string("checkout"));
        verifyNoInteractions(storeActivity);
    }

    @Test
    void pathWithoutStoreIsNotLookedAt() throws Exception {
        // when / then
        mvc.perform(get("/Global/Inventory")).andExpect(content().string("inventory"));
        verifyNoInteractions(storesRepository, storeActivity);
    }

    @Test
    void shopApiOfInactiveStoreAnswersForbiddenWithTheReason() throws Exception {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeActivity.isActive(store)).thenReturn(false);

        // when / then
        mvc.perform(post("/Store/" + STORE_ID + "/Checkout"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().json("{\"error\":\"store-inactive\"}"));
    }

    @Test
    void percentEncodedIdOfInactiveStoreIsRefusedLikeThePlainOne() throws Exception {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeActivity.isActive(store)).thenReturn(false);

        // when / then
        mvc.perform(post(URI.create("/Store/" + STORE_ID_WITH_ENCODED_FIRST_LETTER + "/Checkout")))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"store-inactive\"}"));
        mvc.perform(get(URI.create("/store/" + STORE_ID_WITH_ENCODED_FIRST_LETTER + "/client/order/order-1")))
                .andExpect(status().isForbidden())
                .andExpect(view().name("store-inactive"));
    }

    @Test
    void percentEncodedRootOfInactiveStoreIsRefusedLikeThePlainOne() throws Exception {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeActivity.isActive(store)).thenReturn(false);

        // when / then
        mvc.perform(post(URI.create("/%53tore/" + STORE_ID + "/Checkout")))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"store-inactive\"}"));
        mvc.perform(get(URI.create("/%73tore/" + STORE_ID + "/client/order/order-1")))
                .andExpect(status().isForbidden())
                .andExpect(view().name("store-inactive"));
    }

    @Test
    void clientPageOfInactiveStoreSaysTheStoreIsInactive() throws Exception {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeActivity.isActive(store)).thenReturn(false);

        // when / then
        mvc.perform(get("/store/" + STORE_ID + "/client/order/order-1"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("store-inactive"));
    }
}
