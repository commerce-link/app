package pl.commercelink.web.warehousedocuments;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import pl.commercelink.warehouse.builtin.CounterpartyDetails;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.warehouse.builtin.WarehouseDocument;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListPage.DocumentRow;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** One warehouse document as a row of the list (spec §2.3), every text resolved. */
public class DocumentRowMapper {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final MessageSource messages;
    private final Locale locale;
    private final boolean superAdmin;
    private final String detailsBase;

    public DocumentRowMapper(MessageSource messages, Locale locale, boolean superAdmin, String detailsBase) {
        this.messages = messages;
        this.locale = locale;
        this.superAdmin = superAdmin;
        this.detailsBase = detailsBase;
    }

    public DocumentRow row(WarehouseDocument d) {
        DocumentKind kind = DocumentKind.of(d.getType()).orElse(null);
        String author = StringUtils.isBlank(d.getCreatedBy()) ? text("warehouse.documents.list.author.system") : d.getCreatedBy();
        return new DocumentRow(
                detailsHref(detailsBase, d.getDocumentId()),
                d.getDocumentNo(),
                typeName(d, kind),
                kind != null && kind.incoming(),
                d.getReason() == null ? null : text("DocumentReason." + d.getReason().name()),
                StringUtils.trimToNull(d.getNote()),
                source(d),
                counterpartyName(d.getCounterparty()),
                d.getCreatedAt() == null ? null : d.getCreatedAt().format(DATE),
                (d.getCreatedAt() == null ? "" : d.getCreatedAt().format(TIME) + " · ") + author,
                superAdmin ? d.getStoreId() : null);
    }

    private String typeName(WarehouseDocument d, DocumentKind kind) {
        if (kind != null) return text("warehouse.documents.kind." + kind.code());
        return d.getType() == null ? text("warehouse.documents.list.none") : text("DocumentType." + d.getType().name());
    }

    public static String detailsHref(String detailsBase, String documentId) {
        return detailsBase + "/warehouse-documents/details?documentId=" + URLEncoder.encode(documentId, StandardCharsets.UTF_8);
    }

    /** The record the document came from; a return beats its order, an order beats its delivery. */
    private String source(WarehouseDocument d) {
        if (StringUtils.isNotBlank(d.getRmaId())) return text("warehouse.documents.list.source.rma", shortId(d.getRmaId()));
        if (StringUtils.isNotBlank(d.getOrderId())) return text("warehouse.documents.list.source.order", shortId(d.getOrderId()));
        if (StringUtils.isNotBlank(d.getDeliveryId())) return text("warehouse.documents.list.source.delivery", shortId(d.getDeliveryId()));
        return null;
    }

    public static String counterpartyName(CounterpartyDetails cp) {
        if (cp == null) return null;
        if (StringUtils.isNotBlank(cp.getCompanyName())) return cp.getCompanyName().trim();
        String person = Stream.of(cp.getName(), cp.getSurname()).map(StringUtils::trimToNull).filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        return person.isEmpty() ? null : person;
    }

    /** The first segment of a UUID as stored, the way the delivery and order screens show their ids. */
    public static String shortId(String id) {
        return ConversionUtil.getShortenedId(id);
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
