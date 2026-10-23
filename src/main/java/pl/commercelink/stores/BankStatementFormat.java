package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * How to read the CSV history of operations a bank exports for one account. Columns are named by their header text, or
 * "[n]" (from 1) when the file has no header row ({@code headerRow} 0); {@code headerRow} counts physical lines from 1,
 * since Polish banks put a preamble of account details before the header. The amount is either one signed column or
 * two columns of credits and debits.
 */
@DynamoDBDocument
@Getter
@Setter
@NoArgsConstructor
public class BankStatementFormat {

    @DynamoDBAttribute(attributeName = "encoding")
    private String encoding;
    @DynamoDBAttribute(attributeName = "separator")
    private String separator;
    @DynamoDBAttribute(attributeName = "headerRow")
    private int headerRow;
    @DynamoDBAttribute(attributeName = "dateFormat")
    private String dateFormat;
    @DynamoDBAttribute(attributeName = "decimalComma")
    private boolean decimalComma;
    @DynamoDBAttribute(attributeName = "dateColumn")
    private String dateColumn;
    @DynamoDBAttribute(attributeName = "amountColumn")
    private String amountColumn;
    @DynamoDBAttribute(attributeName = "creditColumn")
    private String creditColumn;
    @DynamoDBAttribute(attributeName = "debitColumn")
    private String debitColumn;
    @DynamoDBAttribute(attributeName = "titleColumns")
    private List<String> titleColumns = new ArrayList<>();
    @DynamoDBAttribute(attributeName = "senderColumn")
    private String senderColumn;
    @DynamoDBAttribute(attributeName = "senderAccountColumn")
    private String senderAccountColumn;
    @DynamoDBAttribute(attributeName = "transactionNoColumn")
    private String transactionNoColumn;
    @DynamoDBAttribute(attributeName = "payoutChannels")
    private List<PayoutChannel> payoutChannels = new ArrayList<>();

    /** Credits and debits in two columns instead of one signed amount. */
    @DynamoDBIgnore
    public boolean isSplitAmount() {
        return !isNotBlank(amountColumn) && isNotBlank(creditColumn) && isNotBlank(debitColumn);
    }

    /** Whether a statement can be read with it: a date, an amount and a title. */
    @DynamoDBIgnore
    public boolean isComplete() {
        boolean amount = isNotBlank(amountColumn) || isSplitAmount();
        boolean title = titleColumns != null && titleColumns.stream().anyMatch(column -> isNotBlank(column));
        // a tab is a separator, not a blank one
        return isNotBlank(encoding) && isNotEmpty(separator) && isNotBlank(dateFormat) && isNotBlank(dateColumn)
                && amount && title;
    }

    @DynamoDBIgnore
    public List<PayoutChannel> payoutChannelsOrEmpty() {
        return payoutChannels == null ? List.of() : payoutChannels;
    }
}
