package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

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
    @DynamoDBAttribute(attributeName = "bankTransactionNo")
    private String bankTransactionNo;
    @DynamoDBAttribute(attributeName = "bankTransactionDate")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateConverter.class)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate bankTransactionDate;

    public Payment() {
    }

    public Payment(PaymentSource source) {
        this.source = source;
    }

    public Payment(String referenceNo, String name, PaymentSource source, double amount, double processingFee) {
        this(referenceNo, name, source, amount, processingFee, null, null);
    }

    public Payment(String referenceNo, String name, PaymentSource source, double amount, double processingFee, String bankTransactionNo, LocalDate bankTransactionDate) {
        this.referenceNo = referenceNo;
        this.name = name;
        this.source = source;
        this.amount = amount;
        this.processingFee = processingFee;
        this.bankTransactionNo = bankTransactionNo;
        this.bankTransactionDate = bankTransactionDate;
    }

    public static Payment bankTransfer(String referenceNo, String name, double amount) {
        return new Payment(referenceNo, name, PaymentSource.BankTransfer, amount, 0);
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

    public String getBankTransactionNo() {
        return bankTransactionNo;
    }

    public void setBankTransactionNo(String bankTransactionNo) {
        this.bankTransactionNo = bankTransactionNo;
    }

    public LocalDate getBankTransactionDate() {
        return bankTransactionDate;
    }

    public void setBankTransactionDate(LocalDate bankTransactionDate) {
        this.bankTransactionDate = bankTransactionDate;
    }

    @DynamoDBIgnore
    public boolean isUnsettled() {
        return amount == 0;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return source != null && (isNotBlank(referenceNo) || amount != 0 || processingFee > 0);
    }

    @DynamoDBIgnore
    public Payment split(double movedAmount) {
        double splitFee = amount > 0 ? round(processingFee * movedAmount / amount) : 0;

        this.amount = round(amount - movedAmount);
        this.processingFee = round(processingFee - splitFee);

        return new Payment(referenceNo, name, source, movedAmount, splitFee, bankTransactionNo, bankTransactionDate);
    }

    @DynamoDBIgnore
    public void absorb(Payment other) {
        this.amount = round(amount + other.amount);
        this.processingFee = round(processingFee + other.processingFee);
    }

    @DynamoDBIgnore
    public boolean matches(Payment other) {
        if (isNotBlank(bankTransactionNo) && isNotBlank(other.bankTransactionNo)) {
            return StringUtils.equals(bankTransactionNo, other.bankTransactionNo);
        }

        if (isNotBlank(referenceNo) && isNotBlank(other.referenceNo)) {
            return StringUtils.equals(referenceNo, other.referenceNo);
        }

        return false;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
