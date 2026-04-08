package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBDocument
public class Payment {
    @DynamoDBAttribute(attributeName = "referenceNo")
    private String referenceNo;
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "source")
    @DynamoDBTypeConvertedEnum
    private PaymentSource source;
    @DynamoDBAttribute(attributeName = "amount")
    private double amount;
    @DynamoDBAttribute(attributeName = "processingFee")
    private double processingFee;

    public Payment() {
    }

    public Payment(PaymentSource source) {
        this.source = source;
    }

    public Payment(String referenceNo, String name, PaymentSource source, double amount, double processingFee) {
        this.referenceNo = referenceNo;
        this.name = name;
        this.source = source;
        this.amount = amount;
        this.processingFee = processingFee;
    }

    // required by dynamodb
    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PaymentSource getSource() {
        return source;
    }

    public void setSource(PaymentSource source) {
        this.source = source;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getProcessingFee() {
        return processingFee;
    }

    public void setProcessingFee(double processingFee) {
        this.processingFee = processingFee;
    }

    @DynamoDBIgnore
    public boolean isUnsettled() {
        return amount == 0;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return source != null && (isNotBlank(referenceNo) || amount != 0 || processingFee > 0);
    }
}
