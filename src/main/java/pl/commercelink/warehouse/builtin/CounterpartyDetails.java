package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import pl.commercelink.invoicing.api.BillingParty;

@DynamoDBDocument
public class CounterpartyDetails {

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
    @DynamoDBAttribute(attributeName = "taxId")
    private String taxId;

    public CounterpartyDetails() {
    }

    public static CounterpartyDetails from(BillingParty seller) {
        CounterpartyDetails details = new CounterpartyDetails();
        details.setName(seller.name());
        details.setSurname(seller.surname());
        details.setCompanyName(seller.company());
        details.setStreetAndNumber(seller.streetAndNumber());
        details.setPostalCode(seller.postalCode());
        details.setCity(seller.city());
        details.setCountry(seller.country());
        details.setTaxId(seller.taxNo());
        return details;
    }

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

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }
}
