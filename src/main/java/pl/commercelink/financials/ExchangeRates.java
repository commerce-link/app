package pl.commercelink.financials;

import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

/*
    Generate by AI
 */
public class ExchangeRates {

    private static final String NBP_API_URL = "http://api.nbp.pl/api/exchangerates/tables/C";

    private final RestTemplate restTemplate;

    public ExchangeRates() {
        this.restTemplate = new RestTemplate();
    }

    public Map<String, Double> getCurrentBuyRates() {
        return getCurrentRates("bid");
    }

    public Map<String, Double> getCurrentSellRates() {
        return getCurrentRates("ask");
    }

    private Map<String, Double> getCurrentRates(String rateType) {
        String url = UriComponentsBuilder.fromHttpUrl(NBP_API_URL)
                .queryParam("format", "json")
                .toUriString();

        NbpResponse[] response = restTemplate.getForObject(url, NbpResponse[].class);
        Map<String, Double> rates = new HashMap<>();

        if (response != null && response.length > 0) {
            for (NbpRate rate : response[0].getRates()) {
                rates.put(rate.getCode(), rateType.equals("bid") ? rate.getBid() : rate.getAsk());
            }
        }

        return rates;
    }

    private static class NbpResponse {
        private String table;
        private String no;
        private String effectiveDate;
        private NbpRate[] rates;

        public String getTable() {
            return table;
        }

        public String getNo() {
            return no;
        }

        public String getEffectiveDate() {
            return effectiveDate;
        }

        public NbpRate[] getRates() {
            return rates;
        }
    }

    private static class NbpRate {
        private String currency;
        private String code;
        private double bid;
        private double ask;

        public String getCurrency() {
            return currency;
        }

        public String getCode() {
            return code;
        }

        public double getBid() {
            return bid;
        }

        public double getAsk() {
            return ask;
        }
    }
}