
package me.pantre.app.model;

import androidx.annotation.NonNull;

final class AutoValue_InventoryReadItem extends InventoryReadItem {

    private final String epc;
    private final int power;
    private final int TMAntenna;
    private final int realAntenna;
    private final long created;
    private final long readingCycleNumber;
    private final int rssi;
    private final int frequency;
    private final int phase;
    private final int readCount;
    private final int plan;
    private final int shelf;
    private final boolean temperature;

    private AutoValue_InventoryReadItem(
            String epc,
            int power,
            int TMAntenna,
            int realAntenna,
            long created,
            long readingCycleNumber,
            int rssi,
            int frequency,
            int phase,
            int readCount,
            int plan,
            int shelf,
            boolean temperature) {
        this.epc = epc;
        this.power = power;
        this.TMAntenna = TMAntenna;
        this.realAntenna = realAntenna;
        this.created = created;
        this.readingCycleNumber = readingCycleNumber;
        this.rssi = rssi;
        this.frequency = frequency;
        this.phase = phase;
        this.readCount = readCount;
        this.plan = plan;
        this.shelf = shelf;
        this.temperature = temperature;
    }

    @Override
    public String getEpc() {
        return epc;
    }

    @Override
    public int getPower() {
        return power;
    }

    @Override
    public int getTMAntenna() {
        return TMAntenna;
    }

    @Override
    public int getRealAntenna() {
        return realAntenna;
    }

    @Override
    public long getCreated() {
        return created;
    }

    @Override
    public long getReadingCycleNumber() {
        return readingCycleNumber;
    }

    @Override
    public int getRssi() {
        return rssi;
    }

    @Override
    public int getFrequency() {
        return frequency;
    }

    @Override
    public int getPhase() {
        return phase;
    }

    @Override
    public int getReadCount() {
        return readCount;
    }

    @Override
    public int getPlan() {
        return plan;
    }

    @Override
    public int getShelf() {
        return shelf;
    }

    @Override
    public boolean isTemperature() {
        return temperature;
    }

    @NonNull
    @Override
    public String toString() {
        return "InventoryReadItem{"
                + "epc=" + epc + ", "
                + "power=" + power + ", "
                + "TMAntenna=" + TMAntenna + ", "
                + "realAntenna=" + realAntenna + ", "
                + "created=" + created + ", "
                + "readingCycleNumber=" + readingCycleNumber + ", "
                + "rssi=" + rssi + ", "
                + "frequency=" + frequency + ", "
                + "phase=" + phase + ", "
                + "readCount=" + readCount + ", "
                + "plan=" + plan + ", "
                + "shelf=" + shelf + ", "
                + "temperature=" + temperature
                + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof InventoryReadItem) {
            InventoryReadItem that = (InventoryReadItem) o;
            return (this.epc.equals(that.getEpc()))
                    && (this.power == that.getPower())
                    && (this.TMAntenna == that.getTMAntenna())
                    && (this.realAntenna == that.getRealAntenna())
                    && (this.created == that.getCreated())
                    && (this.readingCycleNumber == that.getReadingCycleNumber())
                    && (this.rssi == that.getRssi())
                    && (this.frequency == that.getFrequency())
                    && (this.phase == that.getPhase())
                    && (this.readCount == that.getReadCount())
                    && (this.plan == that.getPlan())
                    && (this.shelf == that.getShelf())
                    && (this.temperature == that.isTemperature());
        }
        return false;
    }

    @Override
    public int hashCode() {
        int h = 1;
        h *= 1000003;
        h ^= this.epc.hashCode();
        h *= 1000003;
        h ^= this.power;
        h *= 1000003;
        h ^= this.TMAntenna;
        h *= 1000003;
        h ^= this.realAntenna;
        h *= 1000003;
        h ^= Long.hashCode(this.created);
        h *= 1000003;
        h ^= Long.hashCode(this.readingCycleNumber);
        h *= 1000003;
        h ^= this.rssi;
        h *= 1000003;
        h ^= this.frequency;
        h *= 1000003;
        h ^= this.phase;
        h *= 1000003;
        h ^= this.readCount;
        h *= 1000003;
        h ^= this.plan;
        h *= 1000003;
        h ^= this.shelf;
        h *= 1000003;
        h ^= this.temperature ? 1231 : 1237;
        return h;
    }

    static final class Builder extends InventoryReadItem.Builder {
        private String epc;
        private Integer power;
        private Integer TMAntenna;
        private Integer realAntenna;
        private Long created;
        private Long readingCycleNumber;
        private Integer rssi;
        private Integer frequency;
        private Integer phase;
        private Integer readCount;
        private Integer plan;
        private Integer shelf;
        private Boolean temperature;

        Builder() {
        }

        @Override
        public InventoryReadItem.Builder setEpc(String epc) {
            this.epc = epc;
            return this;
        }

        @Override
        public InventoryReadItem.Builder setPower(int power) {
            this.power = power;
            return this;
        }

        @Override
        public InventoryReadItem.Builder setTMAntenna(int TMAntenna) {
            this.TMAntenna = TMAntenna;
            return this;
        }

        @Override
        public InventoryReadItem.Builder setRealAntenna(int realAntenna) {
            this.realAntenna = realAntenna;
            return this;
        }

        @Override
        public InventoryReadItem.Builder setCreated(long created) {
            this.created = created;
            return this;
        }

        @Override
        public InventoryReadItem.Builder setReadingCycleNumber(long readingCycleNumber) {
            this.readingCycleNumber = readingCycleNumber;
            return this;
        }

        @Override
        public InventoryReadItem.Builder setRssi(int rssi) {
            this.rssi = rssi;
            return this;
        }

        @Override
        public InventoryReadItem.Builder setFrequency(int frequency) {
            this.frequency = frequency;
            return this;
        }

        @Override
        public InventoryReadItem.Builder setPhase(int phase) {
            this.phase = phase;
            return this;
        }

        @Override
        public InventoryReadItem.Builder setReadCount(int readCount) {
            this.readCount = readCount;
            return this;
        }

        @Override
        public InventoryReadItem.Builder setPlan(int plan) {
            this.plan = plan;
            return this;
        }

        @Override
        public InventoryReadItem.Builder setShelf(int shelf) {
            this.shelf = shelf;
            return this;
        }

        @Override
        public InventoryReadItem.Builder setTemperature(boolean temperature) {
            this.temperature = temperature;
            return this;
        }

        @Override
        public InventoryReadItem build() {
            String missing = "";
            if (epc == null) {
                missing += " epc";
            }
            if (power == null) {
                missing += " power";
            }
            if (TMAntenna == null) {
                missing += " TMAntenna";
            }
            if (realAntenna == null) {
                missing += " realAntenna";
            }
            if (created == null) {
                missing += " created";
            }
            if (readingCycleNumber == null) {
                missing += " readingCycleNumber";
            }
            if (rssi == null) {
                missing += " rssi";
            }
            if (frequency == null) {
                missing += " frequency";
            }
            if (phase == null) {
                missing += " phase";
            }
            if (readCount == null) {
                missing += " readCount";
            }
            if (plan == null) {
                missing += " plan";
            }
            if (shelf == null) {
                missing += " shelf";
            }
            if (temperature == null) {
                missing += " temperature";
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Missing required properties:" + missing);
            }
            return new AutoValue_InventoryReadItem(
                    this.epc,
                    this.power,
                    this.TMAntenna,
                    this.realAntenna,
                    this.created,
                    this.readingCycleNumber,
                    this.rssi,
                    this.frequency,
                    this.phase,
                    this.readCount,
                    this.plan,
                    this.shelf,
                    this.temperature);
        }
    }

}
