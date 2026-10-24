package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.DiscountCodesRepository;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.PaymentOptionView;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.DiscountCodeDetailsView;
import pl.commercelink.web.settings.SettingsFlash;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreDiscountCodeControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private DiscountCodesRepository discountCodesRepository;
    @Mock
    private PaymentGateways paymentGateways;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreDiscountCodeController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));
        when(paymentGateways.paymentOptions(any())).thenReturn(List.of(new PaymentOptionView("paynow", "PayNow", true)));
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theDetailsShowTheCodeOfTheSessionStore() {
        // given
        store("store-1");
        DiscountCode code = code("store-1", "LATO-10", true, 0);
        code.setPaymentProvider("paynow");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.details("lato-10", model, PL);

        // then
        assertThat(view).isEqualTo("store-discount-code");
        DiscountCodeDetailsView details = (DiscountCodeDetailsView) model.getAttribute("details");
        assertThat(details.summary().code()).isEqualTo("LATO-10");
        assertThat(details.summary().paymentProvider()).isEqualTo("PayNow");
        assertThat(details.toggleHref()).isEqualTo("/dashboard/store/discounts/LATO-10/toggle");
        assertThat(details.deleteHref()).isEqualTo("/dashboard/store/discounts/LATO-10/delete");
        assertThat(details.deletable()).isTrue();
        assertThat(model.getAttribute("backHref")).isEqualTo("/dashboard/store/discounts");
        assertThat(model.getAttribute("backLabel")).isEqualTo("store.discounts");
    }

    @Test
    void theSuperAdminSeesTheCodeOfTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        store("store-2");
        code("store-2", "LATO-10", true, 0);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.superAdminDetails("store-2", "LATO-10", model, PL);

        // then
        DiscountCodeDetailsView details = (DiscountCodeDetailsView) model.getAttribute("details");
        assertThat(details.toggleHref()).isEqualTo("/dashboard/store/store-2/discounts/LATO-10/toggle");
        assertThat(model.getAttribute("backHref")).isEqualTo("/dashboard/store/store-2/discounts");
    }

    @Test
    void anUnknownStoreOrCodeAnswers404OnEveryAction() {
        // given
        store("store-1");
        when(discountCodesRepository.find("store-1", "GONE-10")).thenReturn(Optional.empty());
        List<Consumer<String>> calls = List.of(
                code -> controller.details(code, new ExtendedModelMap(), PL),
                code -> controller.toggle(code, false, PL, new RedirectAttributesModelMap()),
                code -> controller.confirmDelete(code, new ExtendedModelMap(), PL));

        // when / then
        calls.forEach(call -> assertThatThrownBy(() -> call.accept("GONE-10")).isInstanceOf(ResponseStatusException.class));
        authenticateAs(null, "SUPER_ADMIN");
        assertThatThrownBy(() -> controller.superAdminDetails("missing", "LATO-10", new ExtendedModelMap(), PL))
                .isInstanceOf(ResponseStatusException.class);
        verify(discountCodesRepository, never()).setActive(anyString(), anyString(), anyBoolean());
    }

    @Test
    void togglingAnActiveCodeSwitchesItOff() {
        // given
        store("store-1");
        code("store-1", "LATO-10", true, 0);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.toggle("LATO-10", false, PL, redirectAttributes);

        // then
        verify(discountCodesRepository).setActive("store-1", "LATO-10", false);
        assertThat(view).isEqualTo("redirect:/dashboard/store/discounts/LATO-10");
        assertThat(redirectAttributes.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE)).isEqualTo("store.discounts.toggled.off");
    }

    @Test
    void togglingASwitchedOffCodeSwitchesItOn() {
        // given
        store("store-1");
        code("store-1", "LATO-10", false, 0);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.toggle("LATO-10", true, PL, redirectAttributes);

        // then
        verify(discountCodesRepository).setActive("store-1", "LATO-10", true);
        assertThat(redirectAttributes.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE)).isEqualTo("store.discounts.toggled.on");
    }

    @Test
    void aRepeatedSwitchOffKeepsTheCodeOff() {
        // given
        store("store-1");
        code("store-1", "LATO-10", false, 0);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        controller.toggle("LATO-10", false, PL, redirectAttributes);

        // then
        verify(discountCodesRepository).setActive("store-1", "LATO-10", false);
        verify(discountCodesRepository, never()).setActive("store-1", "LATO-10", true);
        assertThat(redirectAttributes.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE)).isEqualTo("store.discounts.toggled.off");
    }

    @Test
    void theSuperAdminTogglesTheCodeOfTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        store("store-2");
        code("store-2", "LATO-10", true, 0);

        // when
        String view = controller.superAdminToggle("store-2", "LATO-10", false, PL, new RedirectAttributesModelMap());

        // then
        verify(discountCodesRepository).setActive("store-2", "LATO-10", false);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/discounts/LATO-10");
    }

    @Test
    void theConfirmationPageDeletesNothing() {
        // given
        store("store-1");
        code("store-1", "BF-2026", true, 0);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.confirmDelete("BF-2026", model, PL);

        // then
        assertThat(view).isEqualTo("settings-confirm");
        ConfirmAction confirm = (ConfirmAction) model.getAttribute("confirm");
        assertThat(confirm.actionPath()).isEqualTo("/dashboard/store/discounts/BF-2026/delete");
        assertThat(confirm.cancelPath()).isEqualTo("/dashboard/store/discounts/BF-2026");
        assertThat(confirm.destructive()).isTrue();
        verify(discountCodesRepository, never()).delete(anyString(), anyString());
    }

    @Test
    void deletingAnUnusedCodeReturnsToTheListWithAMessage() {
        // given
        store("store-1");
        code("store-1", "BF-2026", true, 0);
        when(discountCodesRepository.delete("store-1", "BF-2026")).thenReturn(true);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.delete("BF-2026", PL, redirectAttributes);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/discounts");
        assertThat(redirectAttributes.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE)).isEqualTo("store.discounts.deleted");
    }

    @Test
    void deletingAUsedCodeIsRefusedWithAnError() {
        // given
        store("store-1");
        code("store-1", "WITAJ-5", true, 1);
        when(discountCodesRepository.delete("store-1", "WITAJ-5")).thenReturn(false);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.delete("WITAJ-5", PL, redirectAttributes);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/discounts/WITAJ-5");
        assertThat(redirectAttributes.getFlashAttributes().get(SettingsFlash.ERROR_MESSAGE)).isEqualTo("store.discounts.delete.refused");
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKey(SettingsFlash.SAVED_MESSAGE);
    }

    @Test
    void deletingACodeThatIsAlreadyGoneReturnsToTheList() {
        // given
        store("store-1");
        when(discountCodesRepository.find("store-1", "GONE-10")).thenReturn(Optional.empty());

        // when
        String view = controller.delete("GONE-10", PL, new RedirectAttributesModelMap());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/discounts");
        verify(discountCodesRepository, never()).delete(anyString(), anyString());
    }

    @Test
    void theSuperAdminDeletesInTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        store("store-2");
        code("store-2", "BF-2026", true, 0);
        when(discountCodesRepository.delete("store-2", "BF-2026")).thenReturn(true);

        // when
        String view = controller.superAdminDelete("store-2", "BF-2026", PL, new RedirectAttributesModelMap());

        // then
        verify(discountCodesRepository).delete("store-2", "BF-2026");
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/discounts");
    }

    private DiscountCode code(String storeId, String code, boolean active, int usedCount) {
        DiscountCode discountCode = new DiscountCode();
        discountCode.setStoreId(storeId);
        discountCode.setCode(code);
        discountCode.setName("Kampania " + code);
        discountCode.setType(DiscountType.Percentage);
        discountCode.setValue(10);
        discountCode.setActive(active);
        discountCode.setUsedCount(usedCount);
        when(discountCodesRepository.find(storeId, code)).thenReturn(Optional.of(discountCode));
        when(discountCodesRepository.find(storeId, code.toLowerCase(Locale.ROOT))).thenReturn(Optional.of(discountCode));
        return discountCode;
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private void authenticateAs(String storeId, String role) {
        Map<String, String> attributes = storeId != null
                ? Map.of("storeId", storeId, "role", role)
                : Map.of("role", role);
        CustomUser user = new CustomUser(null, null, attributes);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
