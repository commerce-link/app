package pl.commercelink.web.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ObjectIdInvoiceNoDto {
    @JsonProperty("id")
    private String id;
    @JsonProperty("invoiceNo")
    private String invoiceNo;

    private ObjectIdInvoiceNoDto() {
    }

    public ObjectIdInvoiceNoDto(String id) {
        this.id = id;
    }

    public ObjectIdInvoiceNoDto(String id, String invoiceNo) {
        this.id = id;
        this.invoiceNo = invoiceNo;
    }

    public String getId() {
        return id;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }
}
