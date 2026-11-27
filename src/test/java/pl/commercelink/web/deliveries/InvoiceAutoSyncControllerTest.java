package pl.commercelink.web.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.inventory.deliveries.InvoiceAutoSyncResult;
import pl.commercelink.inventory.deliveries.InvoiceAutoSyncService;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceAutoSyncControllerTest {

    private static final Locale PL = new Locale("pl");
    private static final LocalDateTime STOPPED_AT = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Mock
    private InvoiceAutoSyncService service;
    @Mock
    private MessageSource messages;

    @InjectMocks
    private InvoiceAutoSyncController controller;

    @Test
    void runThatRanOutOfTimeHandsTheNextOneWhereItStopped() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            // given
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            when(messages.getMessage(anyString(), any(), any())).thenAnswer(call -> call.getArgument(0));
            when(service.run("store-1", null))
                    .thenReturn(new InvoiceAutoSyncResult(true, List.of("8c124850"), List.of(), 1, 0, 0, List.of(), 3, STOPPED_AT));
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = controller.autoSync(null, redirect, PL);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries?focus=invoice");
            assertThat(redirect.getFlashAttributes().get("invoiceAutoSyncContinueAfter")).isEqualTo("2026-11-02T10:00");
            assertThat(redirect.getFlashAttributes().get(InvoiceAutoSyncController.NOTICE)).isEqualTo(
                    "deliveries.list.invoiceAutoSync.result.synced deliveries.list.invoiceAutoSync.result.unchanged"
                            + " deliveries.list.invoiceAutoSync.result.remaining");
        }
    }

    @Test
    void continuationStartsAfterTheTimeTheButtonCarries() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            // given
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            when(messages.getMessage(anyString(), any(), any())).thenAnswer(call -> call.getArgument(0));
            when(service.run("store-1", STOPPED_AT))
                    .thenReturn(new InvoiceAutoSyncResult(true, List.of(), List.of(), 0, 0, 0, List.of(), 0, null));
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            controller.autoSync("2026-11-02T10:00", redirect, PL);

            // then
            assertThat(redirect.getFlashAttributes().get(InvoiceAutoSyncController.NOTICE)).isEqualTo("deliveries.list.invoiceAutoSync.result.none");
            assertThat(redirect.getFlashAttributes()).doesNotContainKey("invoiceAutoSyncContinueAfter");
        }
    }

    @Test
    void syncedDeliveriesWithAnUnapprovedInvoiceAreNamedInTheErrorMessage() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            // given
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            when(messages.getMessage(anyString(), any(), any()))
                    .thenAnswer(call -> call.<String>getArgument(0) + List.of(call.<Object[]>getArgument(1)));
            when(service.run("store-1", null))
                    .thenReturn(new InvoiceAutoSyncResult(true, List.of("8c124850"), List.of("8c124850"), 0, 0, 0, List.of(), 0, null));
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            controller.autoSync(null, redirect, PL);

            // then
            assertThat(redirect.getFlashAttributes().get(InvoiceAutoSyncController.ERROR))
                    .isEqualTo("deliveries.list.invoiceAutoSync.result.invoiceNotApproved[8c124850]");
        }
    }

    @Test
    void failedDeliveriesAreNamedInTheErrorMessage() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            // given
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            when(messages.getMessage(anyString(), any(), any()))
                    .thenAnswer(call -> call.<String>getArgument(0) + List.of(call.<Object[]>getArgument(1)));
            when(service.run("store-1", null))
                    .thenReturn(new InvoiceAutoSyncResult(true, List.of(), List.of(), 0, 0, 0, List.of("8c124850", "9fb71f29"), 0, null));
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            controller.autoSync(null, redirect, PL);

            // then
            assertThat(redirect.getFlashAttributes().get(InvoiceAutoSyncController.ERROR))
                    .isEqualTo("deliveries.list.invoiceAutoSync.result.failed[8c124850, 9fb71f29]");
        }
    }

    @Test
    void storeWithoutAnInvoicingSystemGetsOnlyTheRefusal() {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            // given
            security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
            when(messages.getMessage(anyString(), any(), any())).thenAnswer(call -> call.getArgument(0));
            when(service.run("store-1", null)).thenReturn(InvoiceAutoSyncResult.notConfigured());
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            controller.autoSync(null, redirect, PL);

            // then
            assertThat(redirect.getFlashAttributes()).containsOnlyKeys(InvoiceAutoSyncController.ERROR);
            assertThat(redirect.getFlashAttributes().get(InvoiceAutoSyncController.ERROR)).isEqualTo("payments.sync.notConfigured");
        }
    }
}
