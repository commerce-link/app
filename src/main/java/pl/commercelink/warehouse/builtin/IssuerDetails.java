package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import pl.commercelink.invoicing.api.BillingParty;

@DynamoDBDocument
public class IssuerDetails {

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
    @DynamoDBAttribute(attributeName = "taxId")
    private String taxId;

    public IssuerDetails() {
    }

    static IssuerDetails from(BillingParty billing) {
        IssuerDetails details = new IssuerDetails();
        details.setCompanyName(billing.company());
        details.setStreetAndNumber(billing.streetAndNumber());
        details.setPostalCode(billing.postalCode());
        details.setCity(billing.city());
        details.setCountry(billing.country());
        details.setTaxId(billing.taxNo());
        return details;
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

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }
}
