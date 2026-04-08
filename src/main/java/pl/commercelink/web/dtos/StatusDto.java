package pl.commercelink.web.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StatusDto {
    @JsonProperty("status")
    private String status;

    public StatusDto(String status) {
        this.status = status;
    }
}
