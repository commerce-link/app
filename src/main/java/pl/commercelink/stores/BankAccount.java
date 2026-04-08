package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;

import java.util.UUID;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBDocument
public class BankAccount {

    @DynamoDBAttribute(attributeName = "id")
    private String id;
    @DynamoDBAttribute(attributeName = "bankName")
    private String bankName;
    @DynamoDBAttribute(attributeName = "iban")
    private String iban;
    @DynamoDBAttribute(attributeName = "accountHolder")
    private String accountHolder;
    @DynamoDBAttribute(attributeName = "swiftCode")
    private String swiftCode;
    @DynamoDBAttribute(attributeName = "currency")
    private String currency;
    @DynamoDBAttribute(attributeName = "default")
    private boolean _default;

    public BankAccount() {
        this.id = UUID.randomUUID().toString();
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return isNotBlank(id) && isNotBlank(bankName) && isNotBlank(iban) && isNotBlank(accountHolder) && isNotBlank(swiftCode) && isNotBlank(currency);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getIban() { return iban; }

    public void setIban(String iban) { this.iban = iban;}

    public String getAccountHolder() {
        return accountHolder;
    }

    public void setAccountHolder(String accountName) {
        this.accountHolder = accountName;
    }

    public String getSwiftCode() {
        return swiftCode;
    }

    public void setSwiftCode(String swiftCode) {
        this.swiftCode = swiftCode;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean is_default() {
        return _default;
    }

    public void set_default(boolean isDefault) {
        this._default = isDefault;
    }
}
