package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.stores.BankStatementFormat;
import pl.commercelink.stores.PayoutChannel;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The CSV format of one bank account's statements, on its own settings page. The sample file is not stored: once
 * uploaded it travels with the form (sampleData, Base64) so the preview can be refreshed after a change without
 * choosing the file again. Payout rows are a payer's text and the orders it settles (their source, payment method and
 * payment provider or carrier, each blank for any) with whether it deducts its fees; a row left blank is ignored, and
 * the page always offers empty rows to add one without a script.
 */
@Getter
@Setter
public class BankStatementFormatForm {

    public static final List<String> ENCODINGS = List.of("windows-1250", "UTF-8", "ISO-8859-2");
    public static final List<String> SEPARATORS = List.of(";", ",", "TAB", "|");
    public static final List<String> DATE_FORMATS = List.of("yyyy-MM-dd", "dd.MM.yyyy", "dd-MM-yyyy", "yyyy.MM.dd",
            "dd/MM/yyyy", "yyyy/MM/dd");
    public static final String SINGLE = "single";
    public static final String SPLIT = "split";
    public static final String COMMA = "comma";
    public static final String DOT = "dot";
    public static final int EMPTY_CHANNEL_ROWS = 2;
    private static final int MAX_HEADER_ROW = 500;
    private static final int TITLE_COLUMNS = 3;

    private String encoding;
    private String separator;
    private String headerRow;
    private String dateFormat;
    private String decimalMark;
    private String dateColumn;
    private String amountMode;
    private String amountColumn;
    private String creditColumn;
    private String debitColumn;
    private String titleColumn1;
    private String titleColumn2;
    private String titleColumn3;
    private String senderColumn;
    private String senderAccountColumn;
    private String transactionNoColumn;
    private List<ChannelRow> channels = new ArrayList<>();
    private String sampleData;
    private String sampleName;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ChannelRow {

        private String match;
        private String source;
        private String method;
        private String provider;
        private boolean netOfFees;

        ChannelRow(PayoutChannel channel) {
            this.match = channel.getMatch();
            this.source = channel.getOrderSource();
            this.method = channel.getMethod() == null ? null : channel.getMethod().name();
            this.provider = channel.getProvider();
            this.netOfFees = channel.isNetOfFees();
        }

        boolean isBlank() {
            return StringUtils.isAllBlank(match, source, method, provider);
        }

        /** A row that names no source, method or provider would offer every open order for the payer's transfers. */
        boolean describesNoOrders() {
            return StringUtils.isAllBlank(source, method, provider);
        }
    }

    public static BankStatementFormatForm defaults() {
        BankStatementFormatForm form = new BankStatementFormatForm();
        form.encoding = "windows-1250";
        form.separator = ";";
        form.headerRow = "1";
        form.dateFormat = "yyyy-MM-dd";
        form.decimalMark = COMMA;
        form.amountMode = SINGLE;
        return form.withEmptyChannelRows();
    }

    public static BankStatementFormatForm from(BankStatementFormat format) {
        if (format == null) {
            return defaults();
        }
        BankStatementFormatForm form = new BankStatementFormatForm();
        form.encoding = format.getEncoding();
        form.separator = "\t".equals(format.getSeparator()) ? "TAB" : format.getSeparator();
        form.headerRow = String.valueOf(format.getHeaderRow());
        form.dateFormat = format.getDateFormat();
        form.decimalMark = format.isDecimalComma() ? COMMA : DOT;
        form.dateColumn = format.getDateColumn();
        form.amountMode = format.isSplitAmount() ? SPLIT : SINGLE;
        form.amountColumn = format.getAmountColumn();
        form.creditColumn = format.getCreditColumn();
        form.debitColumn = format.getDebitColumn();
        List<String> titles = format.getTitleColumns() == null ? List.of() : format.getTitleColumns();
        form.titleColumn1 = titles.size() > 0 ? titles.get(0) : null;
        form.titleColumn2 = titles.size() > 1 ? titles.get(1) : null;
        form.titleColumn3 = titles.size() > 2 ? titles.get(2) : null;
        form.senderColumn = format.getSenderColumn();
        form.senderAccountColumn = format.getSenderAccountColumn();
        form.transactionNoColumn = format.getTransactionNoColumn();
        form.channels = new ArrayList<>(format.payoutChannelsOrEmpty().stream().map(ChannelRow::new).toList());
        return form.withEmptyChannelRows();
    }

    /** The guess made from a sample file, keeping the payout channels and the sample of this form. */
    public BankStatementFormatForm withGuess(BankStatementFormat guess) {
        BankStatementFormatForm form = from(guess);
        form.channels = channels;
        form.sampleData = sampleData;
        form.sampleName = sampleName;
        return form.withEmptyChannelRows();
    }

    /** Whether nothing says where the columns are yet, so a sample file may fill the form in. */
    public boolean hasNoColumns() {
        return StringUtils.isAllBlank(dateColumn, amountColumn, creditColumn, debitColumn, titleColumn1, titleColumn2,
                titleColumn3, senderColumn, senderAccountColumn, transactionNoColumn);
    }

    public BankStatementFormatForm withEmptyChannelRows() {
        List<ChannelRow> rows = new ArrayList<>(channels == null ? List.of()
                : channels.stream().filter(row -> row != null && !row.isBlank()).toList());
        for (int index = 0; index < EMPTY_CHANNEL_ROWS; index++) {
            rows.add(new ChannelRow());
        }
        channels = rows;
        return this;
    }

    /** The hidden fields come back empty when no sample was read: empty is no sample. */
    public void setSampleData(String sampleData) {
        this.sampleData = StringUtils.trimToNull(sampleData);
    }

    public void setSampleName(String sampleName) {
        this.sampleName = StringUtils.trimToNull(sampleName);
    }

    public void attachSample(byte[] content, String name) {
        sampleData = Base64.getEncoder().encodeToString(content);
        sampleName = StringUtils.trimToNull(name);
    }

    /** The sample travelling with the form, or null. */
    public byte[] sample() {
        if (StringUtils.isBlank(sampleData)) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(sampleData.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Field id to message key, in the order of the form. */
    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!ENCODINGS.contains(encoding)) {
            errors.put("encoding", "store.payments.statement.error.encoding");
        }
        if (!SEPARATORS.contains(separator)) {
            errors.put("separator", "store.payments.statement.error.separator");
        }
        if (headerRowNumber() == null) {
            errors.put("headerRow", "store.payments.statement.error.headerRow");
        }
        if (!DATE_FORMATS.contains(dateFormat)) {
            errors.put("dateFormat", "store.payments.statement.error.dateFormat");
        }
        if (StringUtils.isBlank(dateColumn)) {
            errors.put("dateColumn", "store.payments.statement.error.dateColumn");
        }
        if (SPLIT.equals(amountMode)) {
            if (StringUtils.isBlank(creditColumn)) {
                errors.put("creditColumn", "store.payments.statement.error.creditColumn");
            }
            if (StringUtils.isBlank(debitColumn)) {
                errors.put("debitColumn", "store.payments.statement.error.debitColumn");
            }
        } else if (StringUtils.isBlank(amountColumn)) {
            errors.put("amountColumn", "store.payments.statement.error.amountColumn");
        }
        if (titleColumns().isEmpty()) {
            errors.put("titleColumn1", "store.payments.statement.error.titleColumn");
        }
        for (int index = 0; index < channels.size(); index++) {
            ChannelRow channelRow = channels.get(index);
            if (channelRow == null || channelRow.isBlank()) {
                continue;
            }
            if (StringUtils.isBlank(channelRow.getMatch())) {
                errors.put(channelField(index, "match"), "store.payments.statement.error.channelMatch");
            }
            if (StringUtils.isNotBlank(channelRow.getMethod()) && method(channelRow.getMethod()) == null) {
                errors.put(channelField(index, "method"), "store.payments.statement.error.channelMethod");
            } else if (channelRow.describesNoOrders()) {
                errors.put(channelField(index, "source"), "store.payments.statement.error.channelOrders");
            }
        }
        return errors;
    }

    /** The format to store. Call only after {@link #validate()} found nothing. */
    public BankStatementFormat toFormat() {
        BankStatementFormat format = new BankStatementFormat();
        format.setEncoding(encoding);
        format.setSeparator("TAB".equals(separator) ? "\t" : separator);
        Integer row = headerRowNumber();
        format.setHeaderRow(row == null ? 1 : row);
        format.setDateFormat(dateFormat);
        format.setDecimalComma(!DOT.equals(decimalMark));
        format.setDateColumn(StringUtils.trimToNull(dateColumn));
        if (SPLIT.equals(amountMode)) {
            format.setCreditColumn(StringUtils.trimToNull(creditColumn));
            format.setDebitColumn(StringUtils.trimToNull(debitColumn));
        } else {
            format.setAmountColumn(StringUtils.trimToNull(amountColumn));
        }
        format.setTitleColumns(new ArrayList<>(titleColumns()));
        format.setSenderColumn(StringUtils.trimToNull(senderColumn));
        format.setSenderAccountColumn(StringUtils.trimToNull(senderAccountColumn));
        format.setTransactionNoColumn(StringUtils.trimToNull(transactionNoColumn));
        format.setPayoutChannels(new ArrayList<>(channels.stream()
                .filter(channelRow -> channelRow != null && !channelRow.isBlank() && StringUtils.isNotBlank(channelRow.getMatch()))
                .map(channelRow -> new PayoutChannel(channelRow.getMatch().trim(), StringUtils.trimToNull(channelRow.getSource()),
                        method(channelRow.getMethod()), StringUtils.trimToNull(channelRow.getProvider()), channelRow.isNetOfFees()))
                .toList()));
        return format;
    }

    /** The format as typed so far, for the preview: what cannot be read yet is left as the defaults. */
    public BankStatementFormat toPreviewFormat() {
        BankStatementFormat format = toFormat();
        if (!ENCODINGS.contains(encoding)) {
            format.setEncoding("windows-1250");
        }
        if (!DATE_FORMATS.contains(dateFormat)) {
            format.setDateFormat("yyyy-MM-dd");
        }
        if (!SEPARATORS.contains(separator)) {
            format.setSeparator(";");
        }
        return format;
    }

    public List<String> titleColumns() {
        return Stream.of(titleColumn1, titleColumn2, titleColumn3).map(StringUtils::trimToNull)
                .filter(StringUtils::isNotBlank).limit(TITLE_COLUMNS).toList();
    }

    public boolean isSplit() {
        return SPLIT.equals(amountMode);
    }

    public static String channelField(int index, String property) {
        return "channel-" + index + "-" + property;
    }

    /** The payment method a row names, or null for any (blank) or for a value that is no method. */
    public static PaymentSource method(String name) {
        if (StringUtils.isBlank(name)) {
            return null;
        }
        try {
            return PaymentSource.valueOf(name.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Integer headerRowNumber() {
        try {
            int value = Integer.parseInt(StringUtils.trimToEmpty(headerRow));
            return value >= 0 && value <= MAX_HEADER_ROW ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
