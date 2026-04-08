package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.invoicing.api.BillingParty;

@DynamoDBDocument
public class BillingDetails {
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "surname")
    private String surname;
    @DynamoDBAttribute(attributeName = "companyName")
    private String companyName;
    @DynamoDBAttribute(attributeName = "streetAndNumber")
    private String streetAndNumber;
    @DynamoDBAttribute(attributeName = "postalCode")
    private String postalCode;
    @DynamoDBAttribute(attributeName = "city")
    private String city;
    @DynamoDBAttribute(attributeName = "country")
    private String country;
    @DynamoDBAttribute(attributeName = "email")
    private String email;
    @DynamoDBAttribute(attributeName = "phone")
    private String phone;
    @DynamoDBAttribute(attributeName = "taxId")
    private String taxId;

    // required by dynamodb
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getStreetAndNumber() {
        return streetAndNumber;
    }

    public void setStreetAndNumber(String streetAndNumber) {
        this.streetAndNumber = streetAndNumber;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    @DynamoDBIgnore
    public boolean isProperlyFilled() {
        boolean commonFieldsPresent = StringUtils.isNotBlank(getStreetAndNumber()) &&
                StringUtils.isNotBlank(getPostalCode()) &&
                StringUtils.isNotBlank(getCity()) &&
                StringUtils.isNotBlank(getCountry()) &&
                StringUtils.isNotBlank(getEmail());

        if (hasTaxId()) {
            return commonFieldsPresent &&
                    StringUtils.isNotBlank(getCompanyName()) &&
                    StringUtils.isNotBlank(getTaxId());
        } else {
            return commonFieldsPresent && StringUtils.isNotBlank(getName());
        }
    }

    @DynamoDBIgnore
    public boolean hasTaxId() {
        return taxId != null && !taxId.isEmpty();
    }

    @DynamoDBIgnore
    public static BillingDetails _default() {
        BillingDetails billingDetails = new BillingDetails();
        billingDetails.setCountry("PL");
        return billingDetails;
    }

    @DynamoDBIgnore
    public BillingDetails copy() {
        BillingDetails copy = new BillingDetails();
        copy.setName(this.name);
        copy.setSurname(this.surname);
        copy.setCompanyName(this.companyName);
        copy.setStreetAndNumber(this.streetAndNumber);
        copy.setPostalCode(this.postalCode);
        copy.setCity(this.city);
        copy.setCountry(this.country);
        copy.setEmail(this.email);
        copy.setPhone(this.phone);
        copy.setTaxId(this.taxId);
        return copy;
    }

    @DynamoDBIgnore
    public BillingParty toBillingParty() {
        return new BillingParty(
                null,
                name,
                surname,
                companyName,
                streetAndNumber,
                postalCode,
                city,
                country,
                taxId,
                null
        );
    }
}
