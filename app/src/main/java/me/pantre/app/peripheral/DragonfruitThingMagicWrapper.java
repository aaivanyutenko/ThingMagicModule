package me.pantre.app.peripheral;

import static com.thingmagic.ReaderUtil.hexStringToByteArray;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import androidx.annotation.NonNull;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;
import com.thingmagic.AndroidUsbReflection;
import com.thingmagic.Gen2;
import com.thingmagic.ReadPlan;
import com.thingmagic.Reader;
import com.thingmagic.ReaderException;
import com.thingmagic.SimpleReadPlan;
import com.thingmagic.SingleThreadPooledObject;
import com.thingmagic.TMConstants;
import com.thingmagic.TagFilter;
import com.thingmagic.TagOp;
import com.thingmagic.TagProtocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import me.pantre.app.model.RfidBand;
import me.pantre.app.peripheral.model.TagReadData;
import timber.log.Timber;


public class DragonfruitThingMagicWrapper {
    int TEMPERATURE_SENSOR_BIT_POINTER = 0xE0;
    int TEMPERATURE_CODE_WORD_ADDRESS = 0xE;
    int TEMPERATURE_CALIBRATION_WORD_ADDRESS = 0x8;
    byte TEMPERATURE_CALIBRATION_DATA_LENGTH = 4;
    int TEMPERATURE_WEIGHT = 3000;
    private static final boolean IS_LOGGING_ENABLED = true;

    private static final int NEW_THING_MAGIC_VENDOR_ID = 1027;
    private static final int NEW_THING_MAGIC_PRODUCT_ID = 24597;
    public static final int THING_MAGIC_VENDOR_ID = 8200;
    public static final int THING_MAGIC_PRODUCT_ID = 4100;

    private static final int USER_MEM_TEMP_CODE_ADDR = 0xA0;
    private static final int USER_MEM_CALIBRATION_ADDR = 1;

    private static final String TM_URI_STRING = "tmr:///dev";
    private static final String ACTION_USB_PERMISSION = "com.thingmagic.rfidreader.services.USB_PERMISSION";
    private static final int READ_POWER = 3000;

    private Reader thingMagicReader;
    private ReadPlan[] readPlanAntInd;
    private Integer readPower = -1;
    private SingleThreadPooledObject<TagReadData> tagReadDataPool;
    boolean deviceHasPermission;

    public void createReadPlans(int chipAntennasCount) {
        readPlanAntInd = new ReadPlan[chipAntennasCount];
        for (int i = 0; i < chipAntennasCount; i++) {
            readPlanAntInd[i] = new SimpleReadPlan(new int[]{i + 1}, TagProtocol.GEN2, null, null, 0);
        }
    }

    public int getReadPlanCount() {
        return readPlanAntInd == null ? 0 : readPlanAntInd.length;
    }

    public void createReader() throws Exception {
        thingMagicReader = Reader.create(TM_URI_STRING);
    }

    public boolean initializeUsbDevice(Context context) {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            if (device.getVendorId() == THING_MAGIC_VENDOR_ID && device.getProductId() == THING_MAGIC_PRODUCT_ID) {
                requestUsbPermission(context, manager, device);
                new AndroidUsbReflection(manager, null, device, device.getDeviceClass());
                return true;
            } else if (device.getVendorId() == NEW_THING_MAGIC_VENDOR_ID && device.getProductId() == NEW_THING_MAGIC_PRODUCT_ID) {
                requestUsbPermission(context, manager, device);
                try {
                    D2xxManager ftD2xx = D2xxManager.getInstance(context);
                    boolean isFtDevice = ftD2xx.isFtDevice(device);
                    boolean hasPermission = manager.hasPermission(device);
                    int getInterfaceCount = device.getInterfaceCount();
                    Timber.d("isFtDevice = %s", isFtDevice);
                    Timber.d("hasPermission = %s", hasPermission);
                    Timber.d("getInterfaceCount = %s", getInterfaceCount);
                    int addUsbDevice = ftD2xx.addUsbDevice(device);
                    Timber.d("addUsbDevice = %s", addUsbDevice);
                    FT_Device ftdev = ftD2xx.openByUsbDevice(context, device);
                    Timber.d("ftdev = %s", ftdev);
                    new AndroidUsbReflection(manager, ftdev, device, device.getDeviceClass());
                } catch (D2xxManager.D2xxException e) {
                    Timber.e(e);
                    new AndroidUsbReflection(manager, null, device, device.getDeviceClass());
                }
                return false;
            }
        }
        return true;
    }

    private void requestUsbPermission(Context context, UsbManager manager, UsbDevice device) {
        deviceHasPermission = manager.hasPermission(device);
        if (!manager.hasPermission(device)) {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    context.getApplicationContext(),
                    0,
                    new Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
            );
            manager.requestPermission(device, permissionIntent);
        }
    }

    public void connect(String licenseKey, RfidBand rfidBand, boolean isOldThingMagicModule) {
        tagReadDataPool = new SingleThreadPooledObject<>(new TagReadData.PooledObjectFactory());
        try {
            thingMagicReader.connect();
            if (isConnected()) {
                setupReaderParameters(licenseKey, rfidBand, isOldThingMagicModule);
            }
        } catch (Exception e) {
            Timber.e(e, "Error connecting to ThingMagic reader");
        }
    }

    private void setupReaderParameters(String licenseKey, RfidBand rfidBand, boolean isOldThingMagicModule) throws Exception {
        setupRegion(rfidBand.getThingMagicRegionCode());
        if (isOldThingMagicModule) {
            thingMagicReader.paramSet(TMConstants.TMR_PARAM_LICENSE_KEY, hexStringToByteArray(licenseKey));
        }
        setReadPower(READ_POWER);
        logReaderInfo();
        setupReaderDefaults();
    }

    private void setupRegion(String regionCode) throws Exception {
        Reader.Region[] supportedRegions = (Reader.Region[]) thingMagicReader.paramGet(TMConstants.TMR_PARAM_REGION_SUPPORTEDREGIONS);
        if (supportedRegions != null) {
            for (Reader.Region region : supportedRegions) {
                if (region.name().equals(regionCode)) {
                    thingMagicReader.paramSet(TMConstants.TMR_PARAM_REGION_ID, region);
                    return;
                }
            }
        }
    }

    private void logReaderInfo() throws Exception {
        Timber.d("Region: %s", thingMagicReader.paramGet(TMConstants.TMR_PARAM_REGION_ID));
        Timber.d("Hardware: %s", thingMagicReader.paramGet(TMConstants.TMR_PARAM_VERSION_HARDWARE));
        Timber.d("Serial: %s", thingMagicReader.paramGet(TMConstants.TMR_PARAM_VERSION_SERIAL));
        Timber.d("Model: %s", thingMagicReader.paramGet(TMConstants.TMR_PARAM_VERSION_MODEL));
        Timber.d("Software: %s", thingMagicReader.paramGet(TMConstants.TMR_PARAM_VERSION_SOFTWARE));
        Timber.d("Command timeout: %s", thingMagicReader.paramGet(TMConstants.TMR_PARAM_COMMANDTIMEOUT));
        Timber.d("Transport timeout: %s", thingMagicReader.paramGet(TMConstants.TMR_PARAM_TRANSPORTTIMEOUT));
    }

    private void setupReaderDefaults() throws Exception {
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_TAGREADDATA_UNIQUEBYANTENNA, false);
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_TAGREADDATA_RECORDHIGHESTRSSI, true);
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_ENABLE_READ_FILTERING, true);
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_COMMANDTIMEOUT, 2000);
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_TRANSPORTTIMEOUT, 7000);
    }

    public void paramSetTari(String value) throws Exception {
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_GEN2_TARI, Gen2.Tari.valueOf(value));
    }

    public void paramSetBlf(String value) throws Exception {
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_GEN2_BLF, Gen2.LinkFrequency.valueOf(value));
    }

    public void paramSetTagEncoding(String value) throws Exception {
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_GEN2_TAGENCODING, Gen2.TagEncoding.valueOf(value));
    }

    public void paramSetQAlgorithm(String value) throws Exception {
        if (value.equals("dynamic")) {
            thingMagicReader.paramSet(TMConstants.TMR_PARAM_GEN2_Q, new Gen2.DynamicQ());
        } else if (value.startsWith("static")) {
            int initial = Integer.parseInt(value.substring(7));
            thingMagicReader.paramSet(TMConstants.TMR_PARAM_GEN2_Q, new Gen2.StaticQ(initial));
        }
    }

    public void paramSetSession(String value) throws Exception {
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_GEN2_SESSION, Gen2.Session.valueOf(value));
    }

    public void paramSetTarget(String value) throws Exception {
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_GEN2_TARGET, Gen2.Target.valueOf(value));
    }

    public boolean isConnected() {
        return thingMagicReader != null;
    }

    public void paramSetReadPlan(final int readPlanIndex) throws Exception {
        if (readPlanAntInd != null && readPlanIndex < readPlanAntInd.length) {
            thingMagicReader.paramSet(TMConstants.TMR_PARAM_READ_PLAN, readPlanAntInd[readPlanIndex]);
            if (IS_LOGGING_ENABLED)
                Timber.v("Read plan is %s", readPlanAntInd[readPlanIndex].toString());
        }
    }

    public TagReadData[] read(final long duration) throws Exception {
        final List<TagReadData> result = new ArrayList<>();
        final com.thingmagic.TagReadData[] tagReads = thingMagicReader.read(duration);
        for (com.thingmagic.TagReadData tagReadData : tagReads) {
            result.add(transformTagReadData(tagReadData));
        }
        return result.toArray(new TagReadData[0]);
    }

    private TagReadData transformTagReadData(final com.thingmagic.TagReadData tagReadData) {
        final TagReadData result = tagReadDataPool.borrowObject();
        result.setEpc(tagReadData.epcString());
        result.setAntenna(tagReadData.getAntenna());
        result.setTime(tagReadData.getTime());
        result.setRssi(tagReadData.getRssi());
        result.setFrequency(tagReadData.getFrequency());
        result.setPhase(tagReadData.getPhase());
        result.setReadCount(tagReadData.getReadCount());
        result.setData(tagReadData.getData());
        return result;
    }

    public void returnObject(final TagReadData o) {
        tagReadDataPool.returnObject(o);
    }

    public TagReadData[] readTemperatureCode(final int antenna, final long readDuration, String epc) throws Exception {
        SensorTagType type = detectTagType(epc);
        Timber.d("SensorTagType(%s) = %s", epc, type);

        final TagFilter select = new Gen2.Select(false, Gen2.Bank.EPC, 32, epc.length() * 4, hexStringToByteArray(epc));

        try {
            Object tempCodeBytes = thingMagicReader.executeTagOp(
                    new Gen2.ReadData(Gen2.Bank.USER, TEMPERATURE_CODE_WORD_ADDRESS, (byte) 1),
                    select
            );

            if (tempCodeBytes instanceof short[]) {
                short[] words = (short[]) tempCodeBytes;
                Timber.d("tempCodeBytes = %s", Arrays.toString(words));
                if (words.length > 0) {
                    MagnusTemperature temperature = parseMagnusS3Data(words, epc);
                    Timber.d("parseMagnusS3Data: temperature = %s", temperature);
                } else {
                    Timber.d("Insufficient data received: %d words", words.length);
                }
            } else if (tempCodeBytes == null) {
                Timber.d("tempCodeBytes = null");
            } else {
                Timber.d("tempCodeBytes unexpected type: %s", tempCodeBytes.getClass().getName());
            }

        } catch (Exception e) {
            Timber.e(e, "ThingMagic temperature read exception");
        }

        return new TagReadData[0];
    }

    public static class MagnusTemperature {
        public String fullEpc;
        public String baseEpc;
        public double temperature;
        public int rawTempCode;
        public int rssi;
        public long timestamp;

        public MagnusTemperature(String fullEpc, String baseEpc, double temperature,
                                 int rawTempCode, int rssi, long timestamp) {
            this.fullEpc = fullEpc;
            this.baseEpc = baseEpc;
            this.temperature = temperature;
            this.rawTempCode = rawTempCode;
            this.rssi = rssi;
            this.timestamp = timestamp;
        }

        @NonNull
        @Override
        public String toString() {
            return String.format("Magnus S3 [%s] Temp: %.1f°C (raw: 0x%04X) RSSI: %d dBm",
                    baseEpc, temperature, rawTempCode, rssi);
        }
    }

    private MagnusTemperature parseMagnusS3Data(short[] data, String originalEpc) {
        if (data.length < 6) {
            Timber.d("Insufficient data received: %d words", data.length);
            return null;
        }
        StringBuilder epcBuilder = new StringBuilder();
        for (short word : data) {
            epcBuilder.append(String.format("%04X", word & 0xFFFF));
        }
        return parseTemperatureFromEPC(epcBuilder.toString(), 0);
    }

    private MagnusTemperature parseTemperatureFromEPC(String epc, int rssi) {
        if (epc == null || epc.length() < 24) {
            Timber.d("EPC too short: %s", epc);
            return null;
        }
        try {
            String baseEpc = epc.substring(0, 16);
            if (epc.length() >= 20) {
                String tempHex = epc.substring(16, 20);
                int tempCode = Integer.parseInt(tempHex, 16);
                double temperature = convertMagnusS3Temperature(tempCode);
                if (temperature >= -40 && temperature <= 85) {
                    return new MagnusTemperature(epc, baseEpc, temperature, tempCode, rssi, System.currentTimeMillis());
                } else {
                    Timber.d("Temperature out of range: %.2f", temperature);
                }
            }
        } catch (Exception e) {
            Timber.e(e, "Error parsing EPC: %s", epc);
        }
        return null;
    }

    private double convertMagnusS3Temperature(int tempCode) {
        double temp1 = (tempCode - 2731.5) / 10.0;
        double temp2 = (tempCode * 0.0625) - 40.0;
        double temp3 = (tempCode / 10.0) - 273.15;
        Timber.d("Temp conversions - M1: %.2f, M2: %.2f, M3: %.2f", temp1, temp2, temp3);
        if (temp1 >= -40 && temp1 <= 85) return temp1;
        if (temp2 >= -40 && temp2 <= 85) return temp2;
        if (temp3 >= -40 && temp3 <= 85) return temp3;
        return temp1;
    }

    public enum SensorTagType {SL900A, MAGNUS_S3, AXZON, GENERIC}

    public SensorTagType detectTagType(String epc) {
        TagFilter filter = new Gen2.Select(false, Gen2.Bank.EPC, 32, epc.length() * 4, hexStringToByteArray(epc));
        try {
            Gen2.ReadData readOp = new Gen2.ReadData(Gen2.Bank.USER, 0x00, (byte) 2);
            thingMagicReader.executeTagOp(readOp, filter);
            Timber.d("Detected as SL900A or compatible");
            return SensorTagType.SL900A;
        } catch (Exception e) {
        }
        try {
            Gen2.ReadData readOp = new Gen2.ReadData(Gen2.Bank.RESERVED, 0x0A, (byte) 1);
            thingMagicReader.executeTagOp(readOp, filter);
            Timber.d("Detected as Axzon or compatible");
            return SensorTagType.AXZON;
        } catch (Exception e) {
        }
        return SensorTagType.GENERIC;
    }

    public TagReadData[] readTemperatureCalibration(final String epc, final int antenna, final long readDuration) throws Exception {
        final TagOp onChipTempRead = new Gen2.ReadData(Gen2.Bank.USER, TEMPERATURE_CALIBRATION_WORD_ADDRESS, TEMPERATURE_CALIBRATION_DATA_LENGTH);
        final SimpleReadPlan readPlan = new SimpleReadPlan(new int[]{antenna}, TagProtocol.GEN2, new Gen2.TagData(epc), onChipTempRead, 0);
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_READ_PLAN, readPlan);
        return read(readDuration);
    }

    public Integer getMaxReadPower() {
        return READ_POWER;
    }

    public Integer getReadPower() {
        return READ_POWER;
    }

    public void setReadPower(@NonNull final Integer power) {
        if (power.intValue() == readPower.intValue()) return;
        try {
            readPower = power;
            thingMagicReader.paramSet(TMConstants.TMR_PARAM_RADIO_READPOWER, READ_POWER);
            Timber.i("TM power was changed: %d", readPower);
        } catch (ReaderException e) {
            Timber.e(e);
        }
    }
}

