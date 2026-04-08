package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;

@DynamoDBDocument
public class InvoicingConfiguration {

    @DynamoDBAttribute(attributeName = "sendInvoicesAsAttachment")
    private boolean sendInvoicesAsAttachment;
    @DynamoDBAttribute(attributeName = "splitPayments")
    private boolean splitPaymentsEnabled;
    @DynamoDBAttribute(attributeName = "paymentTerms")
    private int paymentTerms;
    @DynamoDBAttribute(attributeName = "positionsConsolidation")
    private boolean positionsConsolidation;
    @DynamoDBAttribute(attributeName = "positionsConsolidationPrefix")
    private String positionsConsolidationPrefix;

    // required by DynamoDB
    public InvoicingConfiguration() {

    }

    public boolean isSendInvoicesAsAttachment() {
        return sendInvoicesAsAttachment;
    }

    public void setSendInvoicesAsAttachment(boolean sendInvoicesAsAttachment) {
        this.sendInvoicesAsAttachment = sendInvoicesAsAttachment;
    }

    public boolean isSplitPaymentsEnabled() {
        return splitPaymentsEnabled;
    }

    public void setSplitPaymentsEnabled(boolean splitPaymentsEnabled) {
        this.splitPaymentsEnabled = splitPaymentsEnabled;
    }

    public int getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(int paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public boolean isPositionsConsolidation() {
        return positionsConsolidation;
    }

    public void setPositionsConsolidation(boolean positionsConsolidation) {
        this.positionsConsolidation = positionsConsolidation;
    }

    public String getPositionsConsolidationPrefix() {
        return positionsConsolidationPrefix;
    }

    public void setPositionsConsolidationPrefix(String positionsConsolidationPrefix) {
        this.positionsConsolidationPrefix = positionsConsolidationPrefix;
    }
}
