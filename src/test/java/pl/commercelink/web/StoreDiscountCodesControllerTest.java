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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.DiscountCodesRepository;
import pl.commercelink.discounts.DiscountTarget;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.TargetValue;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.PaymentOptionView;
import pl.commercelink.web.settings.DiscountCodeListView;
import pl.commercelink.web.settings.DiscountCodeView;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreDiscountCodesControllerTest {

    private static final LocalDateTime FAR_PAST = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2099, 1, 1, 0, 0);

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private DiscountCodesRepository discountCodesRepository;
    @Mock
    private PaymentGateways paymentGateways;

    @InjectMocks
    private StoreDiscountCodesController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        when(paymentGateways.paymentOptions(any())).thenReturn(List.of(new PaymentOptionView("paynow", "PayNow", true)));
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theAdminSeesTheCodesOfTheStoreInTheSession() {
        // given
        store("store-1");
        when(discountCodesRepository.findAll("store-1")).thenReturn(List.of(code("WITAJ-5", "Powitalny"), code("BF-2026", "Black Friday")));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.codes(null, null, null, model);

        // then
        assertThat(view).isEqualTo("store-discount-codes");
        assertThat(rows(model)).extracting(DiscountCodeView::code).containsExactly("BF-2026", "WITAJ-5");
        assertThat(rows(model)).extracting(DiscountCodeView::href)
                .containsExactly("/dashboard/store/discounts/BF-2026", "/dashboard/store/discounts/WITAJ-5");
        assertThat(model.getAttribute("listPath")).isEqualTo("/dashboard/store/discounts");
        assertThat(model.getAttribute("newCodeHref")).isEqualTo("/dashboard/store/discounts/new");
    }

    @Test
    void theSuperAdminSeesTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        store("store-2");
        when(discountCodesRepository.findAll("store-2")).thenReturn(List.of(code("LATO-10", "Kampania letnia")));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.superAdminCodes("store-2", null, null, null, model);

        // then
        assertThat(rows(model)).extracting(DiscountCodeView::href).containsExactly("/dashboard/store/store-2/discounts/LATO-10");
        assertThat(model.getAttribute("listPath")).isEqualTo("/dashboard/store/store-2/discounts");
        assertThat(model.getAttribute("newCodeHref")).isEqualTo("/dashboard/store/store-2/discounts/new");
    }

    @Test
    void aStoreWithoutGatewaysIsOfferedNoNewCode() {
        // given
        store("store-1");
        when(paymentGateways.paymentOptions(any())).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.codes(null, null, null, model);

        // then
        assertThat(model.getAttribute("newCodeHref")).isNull();
        assertThat(model.getAttribute("paymentsHref")).isEqualTo("/dashboard/store/payments");
    }

    @Test
    void anUnknownStoreAnswers404() {
        // given
        authenticateAs(null, "SUPER_ADMIN");

        // when / then
        assertThatThrownBy(() -> controller.superAdminCodes("missing", null, null, null, new ExtendedModelMap()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void searchMatchesThePartOfACodeOrOfANameInAnyCase() {
        // given
        store("store-1");
        when(discountCodesRepository.findAll("store-1")).thenReturn(List.of(code("LATO-10", "Kampania letnia"),
                code("BF-2026", "Black Friday"), code("WITAJ-5", "Powitalny")));

        // when
        ExtendedModelMap byCode = new ExtendedModelMap();
        controller.codes(" lato ", null, null, byCode);
        ExtendedModelMap byName = new ExtendedModelMap();
        controller.codes("FRIDAY", null, null, byName);

        // then
        assertThat(rows(byCode)).extracting(DiscountCodeView::code).containsExactly("LATO-10");
        assertThat(rows(byName)).extracting(DiscountCodeView::code).containsExactly("BF-2026");
    }

    @Test
    void statusFilterKeepsOnlyCodesInThatStatusAndCountsEachStatus() {
        // given
        store("store-1");
        DiscountCode disabled = code("ASUS-15", "Promocja ASUS");
        disabled.setActive(false);
        DiscountCode scheduled = code("BF-2026", "Black Friday");
        scheduled.setValidFrom(FAR_FUTURE);
        DiscountCode expired = code("LATO-10", "Kampania letnia");
        expired.setValidTo(FAR_PAST);
        DiscountCode exhausted = code("WITAJ-5", "Powitalny");
        exhausted.setMaxUses(1);
        exhausted.setUsedCount(1);
        DiscountCode active = code("ZESTAW-PC", "Zestawy");
        when(discountCodesRepository.findAll("store-1")).thenReturn(List.of(disabled, scheduled, expired, exhausted, active));

        // when
        ExtendedModelMap model = new ExtendedModelMap();
        controller.codes(null, "scheduled", null, model);

        // then
        assertThat(rows(model)).extracting(DiscountCodeView::code).containsExactly("BF-2026");
        DiscountCodeListView list = (DiscountCodeListView) model.getAttribute("codes");
        assertThat(list.segments()).extracting(DiscountCodeListView.Segment::param)
                .containsExactly("", "active", "scheduled", "exhausted", "expired", "disabled");
        assertThat(list.segments()).extracting(DiscountCodeListView.Segment::count).containsExactly(5L, 1L, 1L, 1L, 1L, 1L);
        assertThat(list.segments()).filteredOn(DiscountCodeListView.Segment::active)
                .extracting(DiscountCodeListView.Segment::param).containsExactly("scheduled");
    }

    @Test
    void targetFilterTellsTheWholeBasketFromCategories() {
        // given
        store("store-1");
        DiscountCode categories = code("GPU-50", "Rabat na podzespoły");
        categories.setTarget(DiscountTarget.Category);
        categories.setTargetValues(List.of(new TargetValue("Karty graficzne", "Karty graficzne")));
        when(discountCodesRepository.findAll("store-1")).thenReturn(List.of(categories, code("LATO-10", "Kampania letnia")));

        // when
        ExtendedModelMap basket = new ExtendedModelMap();
        controller.codes(null, null, "basket", basket);
        ExtendedModelMap category = new ExtendedModelMap();
        controller.codes(null, null, "category", category);
        ExtendedModelMap unknown = new ExtendedModelMap();
        controller.codes(null, null, "catalog", unknown);

        // then
        assertThat(rows(basket)).extracting(DiscountCodeView::code).containsExactly("LATO-10");
        assertThat(rows(category)).extracting(DiscountCodeView::code).containsExactly("GPU-50");
        assertThat(rows(unknown)).hasSize(2);
    }

    @Test
    void segmentLinksKeepTheSearchAndTheTarget() {
        // given
        store("store-1");
        when(discountCodesRepository.findAll("store-1")).thenReturn(List.of(code("LATO-10", "Kampania letnia")));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.codes("lato 10", "active", "basket", model);

        // then
        DiscountCodeListView list = (DiscountCodeListView) model.getAttribute("codes");
        assertThat(list.segments()).filteredOn(segment -> segment.param().equals("expired"))
                .extracting(DiscountCodeListView.Segment::href)
                .containsExactly("/dashboard/store/discounts?q=lato%2010&target=basket&status=expired");
        assertThat(list.segments()).filteredOn(segment -> segment.param().isEmpty())
                .extracting(DiscountCodeListView.Segment::href)
                .containsExactly("/dashboard/store/discounts?q=lato%2010&target=basket");
    }

    private static List<DiscountCodeView> rows(ExtendedModelMap model) {
        return ((DiscountCodeListView) model.getAttribute("codes")).rows();
    }

    private static DiscountCode code(String code, String name) {
        DiscountCode discountCode = new DiscountCode();
        discountCode.setStoreId("store-1");
        discountCode.setCode(code);
        discountCode.setName(name);
        discountCode.setType(DiscountType.Percentage);
        discountCode.setValue(10);
        discountCode.setActive(true);
        return discountCode;
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.addPaymentIntegration("paynow");
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
