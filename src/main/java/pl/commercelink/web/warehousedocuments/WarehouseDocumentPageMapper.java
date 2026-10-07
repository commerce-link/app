package pl.commercelink.web.warehousedocuments;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.warehouse.builtin.*;
import pl.commercelink.web.deliveries.details.DeliveryLinks;
import pl.commercelink.web.orders.Money;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentPage.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/** One warehouse document as its details page (spec §3); read only, every text resolved. */
public class WarehouseDocumentPageMapper {

    private static final DateTimeFormatter CREATED = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");
    public static final String PRINT_ENDPOINT = "/dashboard/warehouse-documents/print-labels";

    private final MessageSource messages;
    private final Locale locale;

    public WarehouseDocumentPageMapper(MessageSource messages, Locale locale) {
        this.messages = messages;
        this.locale = locale;
    }

    public WarehouseDocumentPage page(WarehouseDocument d, List<WarehouseDocumentItem> items,
                                      List<pl.commercelink.stores.Printer> printers, boolean superAdmin) {
        DocumentKind kind = DocumentKind.of(d.getType()).orElse(null);
        List<WarehouseDocumentItem> sorted = items.stream()
                .sorted(Comparator.comparing((WarehouseDocumentItem i) -> StringUtils.defaultString(i.getName()), String.CASE_INSENSITIVE_ORDER))
                .toList();
        double total = sorted.stream().mapToDouble(i -> i.getQty() * i.getUnitPrice()).sum();
        int qty = sorted.stream().mapToInt(WarehouseDocumentItem::getQty).sum();
        List<ItemLine> lines = sorted.stream().map(i -> line(d, i, superAdmin)).toList();
        boolean internal = d.getCounterparty() == null && StringUtils.isAllBlank(d.getDeliveryId(), d.getOrderId(), d.getRmaId());

        return new WarehouseDocumentPage(
                d.getDocumentId(),
                superAdmin ? "/dashboard/store/" + d.getStoreId() + "/warehouse-documents" : "/dashboard/warehouse-documents",
                text("warehouse.documents.details.back"),
                d.getDocumentNo(),
                typeName(d, kind),
                kind != null && kind.incoming(),
                d.getReason() == null ? null : text("DocumentReason." + d.getReason().name()),
                d.getCreatedAt() == null ? null : text("warehouse.documents.details.created", d.getCreatedAt().format(CREATED)),
                StringUtils.isBlank(d.getCreatedBy()) ? text("warehouse.documents.list.author.system") : d.getCreatedBy(),
                amount(total),
                text("warehouse.documents.details.items", lines.size()),
                lines,
                lines.stream().anyMatch(l -> l.historyHref() != null),
                text("warehouse.documents.details.summary.qty", qty),
                text("warehouse.documents.details.summary.total", amount(total)),
                text(internal ? "warehouse.documents.details.links.internal" : "warehouse.documents.details.links"),
                links(d, superAdmin),
                counterparty(d.getCounterparty()),
                deliveryAddress(d.getDeliveryAddress()),
                issuer(d.getIssuer()),
                print(d, qty, printers, superAdmin));
    }

    private String typeName(WarehouseDocument d, DocumentKind kind) {
        if (kind != null) return text("warehouse.documents.kind." + kind.code());
        return d.getType() == null ? text("warehouse.documents.list.none") : text("DocumentType." + d.getType().name());
    }

    private ItemLine line(WarehouseDocument d, WarehouseDocumentItem i, boolean superAdmin) {
        String history = !superAdmin && StringUtils.isNoneBlank(i.getDeliveryId(), i.getMfn())
                ? "/dashboard/warehouse-documents/delivery-mfn-history?deliveryId=" + encode(i.getDeliveryId())
                  + "&mfn=" + encode(i.getMfn()) + "&from=document&documentId=" + encode(d.getDocumentId())
                : null;
        return new ItemLine(i.getName(), StringUtils.trimToNull(i.getEan()), StringUtils.trimToNull(i.getMfn()),
                text("warehouse.documents.details.qty", i.getQty()), amount(i.getUnitPrice()),
                amount(i.getQty() * i.getUnitPrice()), history,
                text("warehouse.documents.details.item.menu", StringUtils.defaultString(i.getName())));
    }

    private List<Link> links(WarehouseDocument d, boolean superAdmin) {
        List<Link> links = new ArrayList<>();
        if (StringUtils.isNotBlank(d.getDeliveryId())) {
            links.add(new Link(text("warehouse.documents.details.link.delivery"), DocumentRowMapper.shortId(d.getDeliveryId()),
                    DeliveryLinks.of(superAdmin, d.getStoreId(), d.getDeliveryId()).details(), false));
        }
        // the order and RMA screens refuse a super admin, so he gets the number as text
        if (StringUtils.isNotBlank(d.getOrderId())) {
            links.add(new Link(text("warehouse.documents.details.link.order"), DocumentRowMapper.shortId(d.getOrderId()),
                    superAdmin ? null : "/dashboard/orders/" + d.getOrderId(), false));
        }
        if (StringUtils.isNotBlank(d.getRmaId())) {
            links.add(new Link(text("warehouse.documents.details.link.rma"), DocumentRowMapper.shortId(d.getRmaId()),
                    superAdmin ? null : "/dashboard/rma/" + d.getRmaId(), false));
        }
        if (StringUtils.isNotBlank(d.getWarehouseId())) {
            links.add(new Link(text("warehouse.documents.details.link.warehouse"), d.getWarehouseId(), null, false));
        }
        if (StringUtils.isNotBlank(d.getNote())) {
            links.add(new Link(text("warehouse.documents.details.link.note"), d.getNote().trim(), null, true));
        }
        return links;
    }

    private Address counterparty(CounterpartyDetails c) {
        if (c == null) return null;
        return address(DocumentRowMapper.counterpartyName(c), c.getStreetAndNumber(), c.getPostalCode(), c.getCity(), c.getCountry(), c.getTaxId());
    }

    private Address issuer(IssuerDetails i) {
        if (i == null) return null;
        return address(StringUtils.trimToNull(i.getCompanyName()), i.getStreetAndNumber(), i.getPostalCode(), i.getCity(), i.getCountry(), i.getTaxId());
    }

    private Address deliveryAddress(DeliveryAddress a) {
        if (a == null) return null;
        String person = String.join(" ", Stream.of(a.getName(), a.getSurname()).map(StringUtils::trimToNull).filter(Objects::nonNull).toList());
        String name = StringUtils.isNotBlank(a.getCompanyName()) ? a.getCompanyName().trim() : StringUtils.trimToNull(person);
        return address(name, a.getStreetAndNumber(), a.getPostalCode(), a.getCity(), a.getCountry(), null);
    }

    /** Lines from the non-empty parts only ("null null" used to print for a partial address); null when nothing is left. */
    private Address address(String name, String street, String postalCode, String city, String country, String taxId) {
        List<String> lines = new ArrayList<>();
        Optional.ofNullable(StringUtils.trimToNull(street)).ifPresent(lines::add);
        String cityLine = String.join(" ", Stream.of(postalCode, city).map(StringUtils::trimToNull).filter(Objects::nonNull).toList());
        if (!cityLine.isEmpty()) lines.add(cityLine);
        Optional.ofNullable(StringUtils.trimToNull(country)).ifPresent(lines::add);
        String tax = StringUtils.isBlank(taxId) ? null : text("warehouse.documents.details.taxId", taxId.trim());
        if (name == null && lines.isEmpty() && tax == null) return null;
        return new Address(name, lines, tax);
    }

    private PrintAction print(WarehouseDocument d, int labels, List<pl.commercelink.stores.Printer> printers, boolean superAdmin) {
        if (superAdmin || d.getType() != DocumentType.GoodsReceipt || printers == null || printers.isEmpty()) {
            return null;
        }
        return new PrintAction(PRINT_ENDPOINT, text("warehouse.documents.print.count", labels),
                printers.stream().map(p -> new Printer(p.getName(), p.getSettings().get("deviceId"))).toList());
    }

    private String amount(double value) {
        return text("general.currency.amount", Money.format(value));
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
