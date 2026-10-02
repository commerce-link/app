package pl.commercelink.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.commercelink.stores.StoresRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;

/**
 * Which supplier identities the supplier page is reachable under. Price lists uploaded before identities became
 * generated tokens are keyed "manual:&lt;the name the operator typed&gt;", so the name — spaces, Polish letters and
 * all — ends up in the path. The unit tests of the controller call its methods directly and never exercise the
 * mapping, which is why this one goes through the dispatcher.
 */
@ExtendWith(MockitoExtension.class)
class StoreSupplierControllerRoutingTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private SupplierConnections suppliers;
    @Mock
    private MessageSource messageSource;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new StoreSupplierController(storesRepository, suppliers, messageSource)).build();
    }

    /** The store is not stubbed, so the handler answers 404; what matters here is that it is reached at all. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "manual-k7f3a9c2",
            "Kosatec-a1b2c3d4",
            "manual:Hurtownia",
            "manual:Cennik%20hurtowy",
            "manual:%C5%BBywno%C5%9B%C4%87"})
    void routesEveryKindOfSupplierIdentityToTheSupplierPage(String identity) throws Exception {
        mockMvc.perform(get("/dashboard/store/suppliers/" + identity))
                .andExpect(handler().handlerType(StoreSupplierController.class));
    }
}
