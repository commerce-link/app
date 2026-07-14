package pl.commercelink.taxonomy;

import java.util.List;

public final class ProductGroups {

    public static final String COMPUTERS = "Computers";
    public static final String SMARTPHONES_TABLETS = "SmartphonesTablets";
    public static final String PRINTERS_SCANNERS = "PrintersScanners";
    public static final String PC_COMPONENTS = "PcComponents";
    public static final String PERIPHERALS = "Peripherals";
    public static final String FURNITURE = "Furniture";
    public static final String SERVICES = "Services";

    public static final List<String> ALL = List.of(COMPUTERS, SMARTPHONES_TABLETS, PRINTERS_SCANNERS,
            PC_COMPONENTS, PERIPHERALS, FURNITURE, SERVICES);

    private ProductGroups() {
    }
}
