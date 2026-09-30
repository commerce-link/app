package pl.commercelink.web.deliveries;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryListState;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierRegistry;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the rows of the deliveries list; created once per request, like OrderRowMapper. */
public class DeliveryRowMapper {

    private static final DateTimeFormatter SAME_YEAR = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter OTHER_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final MessageSource messages;
    private final Locale locale;
    private final SupplierLabelMap labels;
    private final boolean superAdmin;
    private final DecimalFormat amount;

    public DeliveryRowMapper(MessageSource messages, Locale locale, SupplierLabelMap labels, boolean superAdmin) {
        this.messages = messages;
        this.locale = locale;
        this.labels = labels;
        this.superAdmin = superAdmin;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        this.amount = new DecimalFormat("#,##0.00", symbols);
    }

    public DeliveryRow map(Delivery delivery, LocalDate today) {
        DeliveryListState state = DeliveryListState.of(delivery);
        String base = superAdmin ? "/dashboard/store/" + delivery.getStoreId() : "/dashboard";
        String supplier = supplierLabel(delivery);
        String counterparty = StringUtils.trimToNull(delivery.getCounterpartyShortcut());
        boolean received = state.isReceived();
        LocalDate date = received ? delivery.getReceivedAt().toLocalDate() : delivery.getEstimatedDeliveryAt();
        String dueNote = null;
        String dueTone = "";
        if (!received && date != null && !date.isAfter(today)) {
            long days = ChronoUnit.DAYS.between(date, today);
            dueNote = days == 0 ? text("deliveries.list.due.today")
                    : days == 1 ? text("deliveries.list.due.overdue.one") : text("deliveries.list.due.overdue", days);
            dueTone = days == 0 ? "is-warn" : "is-bad";
        }
        return new DeliveryRow(
                base + "/deliveries/details?deliveryId=" + delivery.getDeliveryId(),
                delivery.getShortenedDeliveryId(),
                superAdmin ? delivery.getStoreId() : null,
                delivery.isDropship(),
                supplier,
                StringUtils.trimToNull(delivery.getExternalDeliveryId()),
                state == DeliveryListState.ORDER_PENDING,
                counterparty != null && !counterparty.equals(supplier) ? counterparty : null,
                delivery.getOrderedAt() == null ? null : format(delivery.getOrderedAt().toLocalDate(), today),
                date == null ? null : format(date, today),
                received ? "deliveries.list.column.received" : "deliveries.list.column.planned",
                dueNote, dueTone,
                text(state.messageKey()), state.tone(),
                text("general.currency.amount", amount.format(delivery.getTotalCostGross())),
                text("deliveries.list.cost.netLine", text("general.currency.amount", amount.format(delivery.getTotalCost()))),
                received ? marks(delivery) : List.of());
    }

    private String supplierLabel(Delivery delivery) {
        String provider = delivery.getProvider();
        String label = labels.of(delivery.getStoreId(), provider);
        // a provider without a connection was typed in by hand; the row must not pass it off as a connected supplier
        boolean typed = provider != null && !SupplierRegistry.WAREHOUSE.equals(provider)
                && !labels.has(delivery.getStoreId(), provider);
        return typed ? text("deliveries.list.supplier.typed", provider) : label;
    }

    private List<DeliveryRow.Mark> marks(Delivery delivery) {
        if (SupplierRegistry.WAREHOUSE.equals(delivery.getProvider())) {
            return List.of();
        }
        List<DeliveryRow.Mark> marks = new ArrayList<>();
        marks.add(new DeliveryRow.Mark("FV", delivery.isInvoiced(),
                text(delivery.isInvoiced() ? "deliveries.list.mark.invoice.done" : "deliveries.list.mark.invoice.todo")));
        if (delivery.isInvoiced()) {
            marks.add(new DeliveryRow.Mark("SYNC", delivery.isSynced(),
                    text(delivery.isSynced() ? "deliveries.list.mark.sync.done" : "deliveries.list.mark.sync.todo")));
        }
        return marks;
    }

    private static String format(LocalDate date, LocalDate today) {
        return (date.getYear() == today.getYear() ? SAME_YEAR : OTHER_YEAR).format(date);
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
