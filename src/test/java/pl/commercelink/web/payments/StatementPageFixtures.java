package pl.commercelink.web.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.payments.statements.BankOperation;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.BankStatementFormat;
import pl.commercelink.stores.PayoutChannel;
import pl.commercelink.stores.Store;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/** A store with an mBank account and its open orders, as the reconciliation page tests see them. */
public final class StatementPageFixtures {

    public static final String JAN = "3f9a2c1e-7b4d-4e1a-9c2f-5d8e6a7b9c01";
    public static final String ANNA = "c42d91f0-1111-4222-8333-444455556666";
    public static final String ALLEGRO_1 = "91c2e7a0-0000-4000-8000-000000000001";
    public static final String ALLEGRO_2 = "5d0b8f13-0000-4000-8000-000000000002";
    public static final String BUDMAX = "b71e04d2-0000-4000-8000-000000000003";

    private StatementPageFixtures() {
    }

    public static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }

    public static StatementJson json() {
        return new StatementJson(new ObjectMapper());
    }

    public static Store store() {
        Store store = new Store();
        store.setStoreId("store-1");
        BankAccount account = new BankAccount();
        account.setId("account-1");
        account.setBankName("mBank");
        account.setIban("PL61114020040000340276554567");
        BankStatementFormat format = new BankStatementFormat();
        format.setEncoding("windows-1250");
        format.setSeparator(";");
        format.setHeaderRow(1);
        format.setDateFormat("yyyy-MM-dd");
        format.setDecimalComma(true);
        format.setDateColumn("Data");
        format.setAmountColumn("Kwota");
        format.setTitleColumns(List.of("Tytul"));
        format.setPayoutChannels(new ArrayList<>(List.of(
                new PayoutChannel("ALLEGRO", "Allegro", null, true))));
        account.setStatementFormat(format);
        store.setBankAccounts(new ArrayList<>(List.of(account)));
        return store;
    }

    public static List<Order> openOrders() {
        Order jan = order(JAN, 1249.00, pending(PaymentSource.BankTransfer, null, 0), 1);
        billed(jan, "Jan", "Kowalski");
        Order anna = order(ANNA, 399.99, pending(PaymentSource.BankTransfer, null, 0), 2);
        billed(anna, "Anna", "Wiśniewska");
        Order budmax = order(BUDMAX, 4920.00, pending(PaymentSource.BankTransfer, "PRO 0391/10/2026", 0), 2);
        budmax.addDocument(new Document("fv", "FV 0412/10/2026", null, DocumentType.Proforma));
        Order allegro1 = order(ALLEGRO_1, 649.00, pending(PaymentSource.OnlinePayment, "7f2c9e10", 64.90), 3);
        allegro1.setSource(new OrderSource("Allegro", OrderSourceType.Marketplace));
        Order allegro2 = order(ALLEGRO_2, 219.00, pending(PaymentSource.OnlinePayment, "b41d0a73", 23.13), 4);
        allegro2.setSource(new OrderSource("Allegro", OrderSourceType.Marketplace));
        return List.of(jan, anna, budmax, allegro1, allegro2);
    }

    public static List<BankOperation> incoming() {
        return List.of(
                operation(10, "WB-JAN", "1249.00", "Zamowienie " + JAN, "JAN KOWALSKI"),
                operation(11, "WB-ANNA", "389.99", "Platnosc za zamowienie c42d91f0 <b>", "ANNA WISNIEWSKA"),
                operation(12, "WB-BUDMAX", "4920.00", "FV 0412/10/2026", "BUDMAX PIOTR NOWAK"),
                operation(13, "WB-ALLEGRO", "779.97", "Wyplata srodkow Allegro Finanse", "ALLEGRO SP. Z O.O."),
                operation(14, "WB-LOAN", "500.00", "zwrot pozyczki", "TOMASZ WOJCIK"));
    }

    public static void stub(OrdersRepository repository, List<Order> orders) {
        lenient().when(repository.findByStoreAndStatuses(eq("store-1"), any())).thenReturn(orders);
        lenient().when(repository.findByShortId(eq("store-1"), any())).thenReturn(List.of());
        lenient().when(repository.findByIds(eq("store-1"), any())).thenReturn(List.of());
    }

    public static BankOperation operation(int line, String number, String amount, String title, String sender) {
        return new BankOperation(line, LocalDate.of(2026, 10, 7), new BigDecimal(amount), title, sender, "", number);
    }

    private static Order order(String id, double total, Payment pending, int day) {
        Order order = new Order("store-1");
        order.setOrderId(id);
        order.setTotalPrice(total);
        order.setOrderedAt(LocalDateTime.of(2026, 10, day, 12, 0));
        order.setPayments(new ArrayList<>(List.of(pending)));
        return order;
    }

    private static void billed(Order order, String name, String surname) {
        BillingDetails billing = new BillingDetails();
        billing.setName(name);
        billing.setSurname(surname);
        order.setBillingDetails(billing);
    }

    private static Payment pending(PaymentSource source, String referenceNo, double fee) {
        Payment payment = new Payment(source);
        payment.setReferenceNo(referenceNo);
        payment.setFee(fee);
        return payment;
    }
}
