package me.pantre.app.peripheral.model;

import androidx.annotation.NonNull;

import com.thingmagic.DefaultPooledObjectFactory;
import com.thingmagic.TagData;

import java.util.Arrays;

public class TagReadData {
    /**
     * Each kiosk will have a different temperature tag. To identify temperature tag we will use such prefix.
     * Example epc: `000000000000000000001263` or `000000000000000000001285`
     */
    private static final String TEMPERATURE_EPC_PREFIX = "00000000";
    private static final String TEMPERATURE_SKU1_PREFIX = "00004716";
    private static final String TEMPERATURE_SKU2_PREFIX = "00004717";

    private String epc;
    private int antenna;
    private long time;
    private int rssi;
    private int frequency;
    private int phase;
    private int readCount;
    private byte[] data;
    private byte[] tidMemData;
    private int antennaMultiplier;
    private TagData tagData;


    /**
     * @return true if is temperature tag.
     */
    public boolean isTemperatureTag() {
        return epc != null && (epc.startsWith(TEMPERATURE_EPC_PREFIX)
                || epc.startsWith(TEMPERATURE_SKU1_PREFIX)
                || epc.startsWith(TEMPERATURE_SKU2_PREFIX));
    }

    @NonNull
    @Override
    public String toString() {
        return "TagReadData={"
                + "epc=" + epc + ','
                + "rssi=" + rssi + ','
                + "readCount=" + readCount + ','
                + "antenna=" + antenna + ','
                + "antennaMultiplier=" + antennaMultiplier + ','
                + "frequency=" + frequency + ','
                + "phase=" + phase + ','
                + "data=" + (data == null ? "[]" : Arrays.toString(data))
                + "}";
    }

    public String getEpc() {
        return epc;
    }

    public void setEpc(String epc) {
        this.epc = epc;
    }

    public int getAntenna() {
        return antenna;
    }

    public void setAntenna(int antenna) {
        this.antenna = antenna;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public int getPhase() {
        return phase;
    }

    public void setPhase(int phase) {
        this.phase = phase;
    }

    public int getReadCount() {
        return readCount;
    }

    public void setReadCount(int readCount) {
        this.readCount = readCount;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getAntennaMultiplier() {
        return antennaMultiplier;
    }

    public void setAntennaMultiplier(int antennaMultiplier) {
        this.antennaMultiplier = antennaMultiplier;
    }

    public void setTag(TagData tagData) {
        this.tagData = tagData;
    }

    public TagData getTag() {
        return tagData;
    }

    public void setTIDMemData(byte[] tidMemData) {
        this.tidMemData = tidMemData;
    }

    public byte[] getTidMemData() {
        return tidMemData;
    }

    /**
     * Factory to create TagReadData.
     */
    public static final class PooledObjectFactory extends DefaultPooledObjectFactory<TagReadData> {

        @Override
        public TagReadData makeObject() {
            return new TagReadData();
        }

        @Override
        public void passivateObject(final TagReadData o) {
            if (o != null) {
                o.epc = null;
                o.antenna = 0;
                o.time = 0;
                o.rssi = 0;
                o.frequency = 0;
                o.phase = 0;
                o.readCount = 0;
                o.data = null;
                o.antennaMultiplier = 0;
            }
        }
    }

}
