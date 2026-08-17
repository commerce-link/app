package pl.commercelink.marketplace;

interface MarketplaceExportRunReader {

    boolean handlesFileFormatOf(String key);

    MarketplaceExportRunDocument parse(String key, byte[] fileContent);

    static String runIdFrom(String key) {
        String fileName = key.substring(key.lastIndexOf('/') + 1);
        int extension = fileName.lastIndexOf('.');
        return extension < 0 ? fileName : fileName.substring(0, extension);
    }
}
