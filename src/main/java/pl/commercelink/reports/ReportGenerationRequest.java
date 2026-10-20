package pl.commercelink.reports;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The days are ISO text so the message does not depend on how the queue's mapper writes dates. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ReportGenerationRequest {
    private String storeId;
    private String reportType;
    private String dateFrom;
    private String dateTo;
}
