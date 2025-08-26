package me.pantre.app.model;

public enum RfidBand {
    US902("NA");

    private final String thingMagicRegionCode;

    RfidBand(String thingMagicRegionCode) {
        this.thingMagicRegionCode = thingMagicRegionCode;
    }

    public String getThingMagicRegionCode() {
        return thingMagicRegionCode;
    }
}
