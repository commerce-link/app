package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.context.Context;
import pl.commercelink.receipts.InMemoryReceiptAttemptStore;
import pl.commercelink.receipts.ReceiptAttempt;
import pl.commercelink.receipts.ReceiptAttemptState;
import pl.commercelink.receipts.ReceiptProviderFactory;
import pl.commercelink.receipts.ReceiptRequestSnapshot;
import pl.commercelink.receipts.ReceiptSnapshotJson;
import pl.commercelink.receipts.api.LineKind;
import pl.commercelink.receipts.api.PaymentForm;
import pl.commercelink.receipts.api.ReceiptProviderDescriptor;
import pl.commercelink.receipts.api.VatRate;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DevReceiptPreviewControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String OTHER_STORE_ID = "store-2";

    private final ReceiptProviderFactory providerFactory = mock(ReceiptProviderFactory.class);
    private final InMemoryReceiptAttemptStore attemptStore = new InMemoryReceiptAttemptStore();
    private final StoresRepository storesRepository = mock(StoresRepository.class);
    private final DevReceiptPreviewController controller =
            new DevReceiptPreviewController(providerFactory, attemptStore, storesRepository);
    private MockedStatic<CustomSecurityContext> securityStub;

    @BeforeEach
    void setUp() {
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
        when(providerFactory.getDescriptor("receipts-dev")).thenReturn(mock(ReceiptProviderDescriptor.class));
        Store store = new Store();
        store.setName("Sklep Demo");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        attemptStore.create(attempt(STORE_ID, "order-1:R1", "dev-rcpt-1"));
        attemptStore.create(attempt(OTHER_STORE_ID, "order-9:R1", "dev-rcpt-9"));
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    /** Without the simulator on the classpath (every non-dev build) the page does not exist. */
    @Test
    void answers404WithoutTheDevReceiptsAdapter() {
        // given
        when(providerFactory.getDescriptor("receipts-dev")).thenReturn(null);

        // when / then
        assertNotFound(() -> controller.preview("dev-rcpt-1", new ExtendedModelMap()));
    }

    @Test
    void answers404ForAnUnknownReceiptId() {
        // when / then
        assertNotFound(() -> controller.preview("dev-rcpt-unknown", new ExtendedModelMap()));
    }

    /** The receipt id is looked up in the logged-in store only: another store's receipt is not found. */
    @Test
    void answers404ForAReceiptOfAnotherStore() {
        // when / then
        assertNotFound(() -> controller.preview("dev-rcpt-9", new ExtendedModelMap()));
    }

    @Test
    void rendersTheLinesTotalsVatSummaryAndTheSimulationBanner() {
        // given
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.preview("dev-rcpt-1", model);
        Context context = new Context();
        context.setVariables(model);
        String html = EnglishFragmentTemplateEngine.create().process(view, context);

        // then
        assertThat(view).isEqualTo("dev-receipt");
        assertThat(html)
                .contains("SIMULATION — this is not a fiscal document (receipts-dev preview)")
                .contains("Sklep Demo").contains("E-receipt")
                .contains("2026-09-24 14:05")
                .contains("order-1:R1").contains("dev-rcpt-1").contains("Fiscalised")
                .contains("Laptop Pro 14").contains("Kurier").contains("Książka")
                .contains("1 999,99 zł").contains("3 999,98 zł")
                .contains("49,90 zł").contains("23%").contains("8%")
                .contains("4 014,98 zł")
                .contains("4 064,88 zł")
                .contains("Bank transfer").contains("(PayU)")
                .contains("jan@example.com")
                .contains("href=\"/dashboard/orders/order-1\"")
                .doesNotContain("??");
        assertThat(html.split("data-cl-receipt-line", -1)).hasSize(4);
    }

    /** A label that only repeats the displayed form name ("Przelew (Przelew)") is not shown again. */
    @Test
    void doesNotRepeatThePaymentFormAsItsLabel() {
        // when
        String html = renderedWithPaymentLabel(" bank TRANSFER ");

        // then
        assertThat(html).contains("<dt><span>Bank transfer</span></dt>");
    }

    @Test
    void showsAPaymentLabelThatDiffersFromTheForm() {
        // when
        String html = renderedWithPaymentLabel("Przelew24");

        // then
        assertThat(html).contains("<dt><span>Bank transfer</span> (Przelew24)</dt>");
    }

    @Test
    void omitsABlankPaymentLabel() {
        // when
        String html = renderedWithPaymentLabel("  ");

        // then
        assertThat(html).contains("<dt><span>Bank transfer</span></dt>");
    }

    @Test
    void polishPageCarriesTheSimulationBanner() throws Exception {
        // when / then
        assertThat(messages("messages_pl.properties").getProperty("devReceipts.banner"))
                .isEqualTo("SYMULACJA — to nie jest dokument fiskalny (podgląd z receipts-dev)");
    }

    @Test
    void polishAndEnglishDefineTheSameDevReceiptKeys() throws Exception {
        // when / then
        assertThat(devReceiptKeys("messages_pl.properties")).isEqualTo(devReceiptKeys("messages_en.properties"))
                .contains("devReceipts.vat.EXEMPT", "devReceipts.payment.OTHER");
    }

    /** The rendered page of a one-line receipt paid by transfer with the given payment label. */
    private String renderedWithPaymentLabel(String label) {
        ReceiptAttempt attempt = attempt(STORE_ID, "order-2:R1", "dev-rcpt-2");
        attempt.setRequestSnapshot(ReceiptSnapshotJson.write(new ReceiptRequestSnapshot("order-2",
                LocalDateTime.of(2026, 9, 24, 14, 5), null,
                List.of(new ReceiptRequestSnapshot.Line(LineKind.GOODS, "Kabel", BigDecimal.ONE, 2000,
                        VatRate.VAT_23, null, null)),
                List.of(new ReceiptRequestSnapshot.Pay(PaymentForm.TRANSFER, 2000, label)))));
        attemptStore.create(attempt);
        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.preview("dev-rcpt-2", model);
        Context context = new Context();
        context.setVariables(model);
        return EnglishFragmentTemplateEngine.create().process(view, context);
    }

    private static ReceiptAttempt attempt(String storeId, String receiptKey, String providerReceiptId) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setStoreId(storeId);
        attempt.setReceiptKey(receiptKey);
        attempt.setOrderId(receiptKey.substring(0, receiptKey.indexOf(':')));
        attempt.setAttemptNo(1);
        attempt.setProvider("receipts-dev");
        attempt.setState(ReceiptAttemptState.FISCALISED);
        attempt.setProviderReceiptId(providerReceiptId);
        attempt.setRequestSnapshot(ReceiptSnapshotJson.write(new ReceiptRequestSnapshot(attempt.getOrderId(),
                LocalDateTime.of(2026, 9, 24, 14, 5), "jan@example.com",
                List.of(new ReceiptRequestSnapshot.Line(LineKind.GOODS, "Laptop Pro 14", BigDecimal.valueOf(2), 199999,
                                VatRate.VAT_23, "SKU-1", "5901234123457"),
                        new ReceiptRequestSnapshot.Line(LineKind.SHIPPING, "Kurier", BigDecimal.ONE, 1500,
                                VatRate.VAT_23, null, null),
                        new ReceiptRequestSnapshot.Line(LineKind.GOODS, "Książka", BigDecimal.ONE, 4990,
                                VatRate.VAT_8, null, null)),
                List.of(new ReceiptRequestSnapshot.Pay(PaymentForm.TRANSFER, 406488, "PayU")))));
        return attempt;
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static Properties messages(String file) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources", file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Set<String> devReceiptKeys(String file) throws Exception {
        return messages(file).stringPropertyNames().stream()
                .filter(key -> key.startsWith("devReceipts."))
                .collect(Collectors.toSet());
    }
}
