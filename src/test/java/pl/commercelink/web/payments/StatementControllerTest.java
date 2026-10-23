package pl.commercelink.web.payments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.payments.statements.Allocation;
import pl.commercelink.payments.statements.BankOperation;
import pl.commercelink.payments.statements.StatementBooking;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.web.payments.StatementPageFixtures.*;

@ExtendWith(MockitoExtension.class)
class StatementControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock StoresRepository storesRepository;
    @Mock StatementPageFactory pageFactory;
    @Mock StatementBooking booking;
    @Spy StatementJson json = json();
    @Spy ResourceBundleMessageSource messageSource = messages();
    @InjectMocks StatementController controller;

    @Test
    void readsTheUploadWithTheAccountsFormat() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            // given
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            Store store = store();
            when(storesRepository.findById("store-1")).thenReturn(store);
            MockMultipartFile file = new MockMultipartFile("file", "wyciag.csv", "text/csv",
                    "Data;Kwota;Tytul\n2026-10-07;159,00;zakupy\n2026-10-07;-9,00;oplata\n".getBytes(Charset.forName("windows-1250")));
            ArgumentCaptor<StatementPageFactory.Statement> statement = ArgumentCaptor.forClass(StatementPageFactory.Statement.class);
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = controller.upload("account-1", file, model, PL, new RedirectAttributesModelMap());

            // then
            assertThat(view).isEqualTo("payments-statement");
            verify(pageFactory).page(eq(store), statement.capture(), eq(Map.of()), eq(List.of()), eq(PL));
            assertThat(statement.getValue().incoming()).hasSize(1);
            assertThat(statement.getValue().outgoing()).isEqualTo(1);
            assertThat(statement.getValue().fileName()).isEqualTo("wyciag.csv");
        }
    }

    @Test
    void sendsTheOperatorBackWhenTheAccountHasNoFormat() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            // given
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            Store store = store();
            store.getBankAccounts().get(0).setStatementFormat(null);
            when(storesRepository.findById("store-1")).thenReturn(store);
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = controller.upload("account-1", new MockMultipartFile("file", new byte[]{1}), new ExtendedModelMap(), PL, redirect);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/payments");
            assertThat(redirect.getFlashAttributes().get(PaymentsReturn.ERROR))
                    .isEqualTo("Wybierz rachunek z ustawionym formatem wyciągu.");
        }
    }

    @Test
    void sendsTheOperatorBackWithTheReasonAFileCannotBeRead() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            // given
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            when(storesRepository.findById("store-1")).thenReturn(store());
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
            MockMultipartFile file = new MockMultipartFile("file", "x.csv", "text/csv", "Data;Tytul\n".getBytes());

            // when
            String view = controller.upload("account-1", file, new ExtendedModelMap(), PL, redirect);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/payments");
            assertThat(redirect.getFlashAttributes().get(PaymentsReturn.ERROR)).asString().contains("„Kwota”");
        }
    }

    @Test
    void booksTheTickedOperationsWithTheChannelOfTheFormatNotOfThePage() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            // given
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            Store store = store();
            when(storesRepository.findById("store-1")).thenReturn(store);
            List<BankOperation> operations = incoming();
            Map<String, List<Allocation>> chosen = Map.of(
                    "WB-JAN", List.of(new Allocation(JAN, new BigDecimal("1249.00"), new BigDecimal("0.00"))),
                    "WB-ALLEGRO", List.of(new Allocation(ALLEGRO_1, new BigDecimal("649.00"), new BigDecimal("64.90")),
                            new Allocation(ALLEGRO_2, new BigDecimal("219.00"), new BigDecimal("23.13"))),
                    "WB-LOAN", List.of(new Allocation(ANNA, new BigDecimal("500.00"), new BigDecimal("0.00"))));
            when(booking.book(eq("store-1"), any(), anyList(), any()))
                    .thenAnswer(invocation -> new StatementBooking.Outcome(invocation.getArgument(1), null, List.of()));

            // when
            String view = controller.book("account-1", "wyciag.csv", json.operations(operations), json.allocations(chosen),
                    List.of("WB-JAN", "WB-ALLEGRO"), 25, 1, null, new ExtendedModelMap(), PL, new RedirectAttributesModelMap());

            // then
            assertThat(view).isEqualTo("payments-statement");
            verify(booking).book(eq("store-1"), eq(operations.get(0)), eq(chosen.get("WB-JAN")), eq(List.of()));
            verify(booking).book(eq("store-1"), eq(operations.get(3)), eq(chosen.get("WB-ALLEGRO")),
                    eq(store.getBankAccounts().get(0).getStatementFormat().getPayoutChannels()));
            verify(booking, never()).book(eq("store-1"), eq(operations.get(4)), anyList(), any());
            ArgumentCaptor<Map<String, List<Allocation>>> kept = ArgumentCaptor.forClass(Map.class);
            verify(pageFactory).page(eq(store), any(), kept.capture(), anyList(), eq(PL));
            assertThat(kept.getValue()).containsOnlyKeys("WB-LOAN");
        }
    }

    @Test
    void sendsTheOperatorBackWhenThePageLostItsOperations() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            // given
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            when(storesRepository.findById("store-1")).thenReturn(store());
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = controller.book("account-1", "x.csv", "[]", "{}", List.of("WB-1"), 0, 0, null,
                    new ExtendedModelMap(), PL, redirect);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/payments");
            verify(booking, never()).book(any(), any(), anyList(), any());
        }
    }
}
