package pl.commercelink.web.rma;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.marketplace.api.MarketplaceReturnStatus;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class RmaRowMapperTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
    RmaRowMapper mapper;

    @BeforeEach
    void polishBundle() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        mapper = new RmaRowMapper(messages, Locale.forLanguageTag("pl"));
    }

    private static RMA rma(RMAStatus status, LocalDateTime createdAt) {
        RMA rma = new RMA();
        rma.setRmaId("e3b0c442-98fc-4c14-9afb-f4c8996fb924");
        rma.setOrderId("8c1d0291-0000-4000-8000-000000000000");
        rma.setEmail("anna.nowak@gmail.com");
        rma.setStatus(status);
        rma.setCreatedAt(createdAt);
        rma.setShippingInsurance(1249.0);
        ShippingDetails details = new ShippingDetails();
        details.setName("Anna");
        details.setSurname("Nowak");
        details.setCity("Kraków");
        rma.setShippingDetails(details);
        return rma;
    }

    @Test
    void aShopRmaShowsShortNumbersClientValueAndAge() {
        // when
        RmaRow row = mapper.map(rma(RMAStatus.New, LocalDateTime.of(2026, 10, 10, 9, 0)), TODAY);

        // then
        assertThat(row.number()).isEqualTo("e3b0c442");
        assertThat(row.href()).isEqualTo("/dashboard/rma/e3b0c442-98fc-4c14-9afb-f4c8996fb924");
        assertThat(row.sourceText()).isEqualTo("Sklep");
        assertThat(row.reference()).isNull();
        assertThat(row.clientName()).isEqualTo("Anna Nowak");
        assertThat(row.clientCity()).isEqualTo("Kraków");
        assertThat(row.email()).isEqualTo("anna.nowak@gmail.com");
        assertThat(row.orderNumber()).isEqualTo("8c1d0291");
        assertThat(row.statusLabel()).isEqualTo("Nowy");
        assertThat(row.statusTone()).isEqualTo("is-warn");
        assertThat(row.createdText()).isEqualTo("10.10.2026");
        assertThat(row.ageText()).isEqualTo("3 dni");
        assertThat(row.ageTone()).isNull();
        assertThat(row.valueText()).contains("1 249,00");
    }

    @Test
    void anOldActiveRmaIsFlaggedAndAClosedOneShowsNoAge() {
        // when
        RmaRow old = mapper.map(rma(RMAStatus.Processing, LocalDateTime.of(2026, 9, 29, 9, 0)), TODAY);
        RmaRow today = mapper.map(rma(RMAStatus.Approved, LocalDateTime.of(2026, 10, 13, 9, 0)), TODAY);
        RmaRow closed = mapper.map(rma(RMAStatus.Completed, LocalDateTime.of(2026, 9, 1, 9, 0)), TODAY);

        // then
        assertThat(old.ageText()).isEqualTo("14 dni");
        assertThat(old.ageTone()).isEqualTo("is-warn");
        assertThat(today.ageText()).isEqualTo("dziś");
        assertThat(closed.ageText()).isNull();
        assertThat(closed.statusTone()).isEqualTo("is-ok");
    }

    @Test
    void aMarketplaceReturnShowsItsReferenceStatusAndLastShipment() {
        // given
        RMA rma = rma(RMAStatus.WaitingForItems, LocalDateTime.of(2026, 10, 12, 9, 0));
        rma.setMarketplace("Allegro");
        rma.setExternalReturnId("ret-1");
        rma.setExternalReturnReference("ZW-4K7Q");
        rma.setExternalReturnStatus(MarketplaceReturnStatus.IN_TRANSIT);
        Shipment first = new Shipment();
        first.setCarrier("DPD");
        first.setShippedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        first.setDeliveredAt(LocalDateTime.of(2026, 10, 2, 9, 0));
        Shipment last = new Shipment();
        last.setCarrier("InPost");
        last.setShippedAt(LocalDateTime.of(2026, 10, 12, 10, 0));
        rma.setShipments(List.of(first, last));

        // when
        RmaRow row = mapper.map(rma, TODAY);

        // then
        assertThat(row.sourceText()).isEqualTo("Allegro");
        assertThat(row.reference()).isEqualTo("ZW-4K7Q");
        assertThat(row.marketplaceText()).isEqualTo("Allegro: W drodze");
        assertThat(row.trackText()).isEqualTo("InPost · w drodze");
        assertThat(row.statusTone()).isEqualTo("is-info");
    }

    @Test
    void aDeliveredShipmentShowsTheDay() {
        // given
        RMA rma = rma(RMAStatus.ItemsReceived, LocalDateTime.of(2026, 10, 10, 9, 0));
        Shipment shipment = new Shipment();
        shipment.setShippedAt(LocalDateTime.of(2026, 10, 11, 9, 0));
        shipment.setDeliveredAt(LocalDateTime.of(2026, 10, 12, 9, 0));
        rma.setShipments(List.of(shipment));

        // when
        RmaRow row = mapper.map(rma, TODAY);

        // then
        assertThat(row.trackText()).isEqualTo("Przesyłka · doręczona 12.10");
    }

    @Test
    void aBareRmaStillRenders() {
        // given
        RMA bare = new RMA();
        bare.setRmaId("a1");

        // when
        RmaRow row = mapper.map(bare, TODAY);

        // then
        assertThat(row.number()).isEqualTo("a1");
        assertThat(row.clientName()).isNull();
        assertThat(row.orderNumber()).isNull();
        assertThat(row.statusLabel()).isEqualTo("Nowy");
        assertThat(row.statusTone()).isEqualTo("is-warn");
        assertThat(row.createdText()).isNull();
        assertThat(row.ageText()).isNull();
        assertThat(row.trackText()).isNull();
        assertThat(row.valueText()).isNull();
    }

    @Test
    void withoutShippingDetailsTheEmailNamesTheClient() {
        // given
        RMA rma = rma(RMAStatus.New, LocalDateTime.of(2026, 10, 10, 9, 0));
        rma.setShippingDetails(null);

        // when
        RmaRow row = mapper.map(rma, TODAY);

        // then
        assertThat(row.clientName()).isEqualTo("anna.nowak@gmail.com");
        assertThat(row.email()).isNull();
    }
}
