package pl.commercelink.web.dtos;

import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;

import java.time.LocalDate;

public class AddPaymentForm {

    private double bankAmount;
    private double processingFee;
    private boolean feeIncluded;
    private PaymentSource source;
    private PaymentDirection direction;
    private String referenceNo;
    private String name;
    private String bankTransactionNo;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate bankTransactionDate;

    public double getBankAmount() {
        return bankAmount;
    }

    public void setBankAmount(double bankAmount) {
        this.bankAmount = bankAmount;
    }

    public double getProcessingFee() {
        return processingFee;
    }

    public void setProcessingFee(double processingFee) {
        this.processingFee = processingFee;
    }

    public boolean isFeeIncluded() {
        return feeIncluded;
    }

    public void setFeeIncluded(boolean feeIncluded) {
        this.feeIncluded = feeIncluded;
    }

    public PaymentSource getSource() {
        return source;
    }

    public void setSource(PaymentSource source) {
        this.source = source;
    }

    public PaymentDirection getDirection() {
        return direction;
    }

    public void setDirection(PaymentDirection direction) {
        this.direction = direction;
    }

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
}
