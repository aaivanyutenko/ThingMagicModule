package me.pantre.app.model;

import me.pantre.app.peripheral.model.TagReadData;

public abstract class InventoryReadItem {

    /**
     * Get new builder.
     */
    public static Builder builder() {
        return new AutoValue_InventoryReadItem.Builder();
    }

    public static InventoryReadItem create(final TagReadData tagReadData, final int realAntenna,
                                           final long readingCycleNumber, final Integer readPower, int plan, int shelf) {
        return builder()
                .setEpc(tagReadData.getEpc())
                .setPower(readPower)
                .setTMAntenna(tagReadData.getAntenna())
                .setRealAntenna(realAntenna)
                .setCreated(tagReadData.getTime())
                .setReadingCycleNumber(readingCycleNumber)
                .setRssi(tagReadData.getRssi())
                .setFrequency(tagReadData.getFrequency())
                .setPhase(tagReadData.getPhase())
                .setReadCount(tagReadData.getReadCount())
                .setTemperature(tagReadData.isTemperatureTag())
                .setPlan(plan)
                .setShelf(shelf)
                .build();

    }

    public abstract String getEpc();

    public abstract int getPower();

    /**
     * ThingMagic antenna number.
     */
    public abstract int getTMAntenna();

    /**
     * Real antenna.
     */
    public abstract int getRealAntenna();

    public abstract long getCreated();

    public abstract long getReadingCycleNumber();

    public abstract int getRssi();

    public abstract int getFrequency();

    public abstract int getPhase();

    public abstract int getReadCount();

    public abstract int getPlan();

    public abstract int getShelf();

    public abstract boolean isTemperature();


    /**
     * Builder to build entity.
     */
    public abstract static class Builder {
        public abstract Builder setEpc(final String epc);

        public abstract Builder setPower(final int power);

        public abstract Builder setTMAntenna(final int antenna);

        public abstract Builder setRealAntenna(final int antenna);

        public abstract Builder setCreated(final long created);

        public abstract Builder setReadingCycleNumber(final long readingCycleNumber);

        public abstract Builder setRssi(final int rssi);

        public abstract Builder setFrequency(final int frequency);

        public abstract Builder setPhase(final int phase);

        public abstract Builder setReadCount(final int readCount);

        public abstract Builder setPlan(final int plan);

        public abstract Builder setShelf(final int shelf);

        public abstract Builder setTemperature(boolean isTemperature);

        public abstract InventoryReadItem build();
    }

}
