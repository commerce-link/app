package pl.commercelink.marketplace;

import pl.commercelink.starter.util.ConversionUtil;

import java.nio.charset.StandardCharsets;

class JsonMarketplaceExportRunReader implements MarketplaceExportRunReader {

    @Override
    public boolean handlesFileFormatOf(String key) {
        return key.endsWith(".json");
    }

    @Override
    public MarketplaceExportRunDocument parse(String key, byte[] fileContent) {
        return ConversionUtil.fromJson(
                new String(fileContent, StandardCharsets.UTF_8), MarketplaceExportRunDocument.class);
    }
}
