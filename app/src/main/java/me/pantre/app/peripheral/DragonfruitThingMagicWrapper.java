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
import com.thingmagic.TransportListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import me.pantre.app.model.RfidBand;
import me.pantre.app.peripheral.model.TagReadData;


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

    private static final int USER_MEM_TEMP_CODE_ADDR = 0xA0;  // 1 word
    private static final int USER_MEM_CALIBRATION_ADDR = 1; // 4 words

    private static final String TM_URI_STRING = "tmr:///dev";
    private static final String ACTION_USB_PERMISSION = "com.thingmagic.rfidreader.services.USB_PERMISSION";
    private static final int READ_POWER = 3000; // 30 dBm

    public Reader thingMagicReader;
    private ReadPlan[] readPlanAntInd;
    private Integer readPower = -1;
    private SingleThreadPooledObject<TagReadData> tagReadDataPool;
    boolean deviceHasPermission;

    public void createReadPlans(int chipAntennasCount) {
        readPlanAntInd = new ReadPlan[chipAntennasCount];
        for (int i = 0; i < chipAntennasCount; i++) {
            readPlanAntInd[i] = createSimpleReadPlan(i + 1);
        }
    }

    private SimpleReadPlan createSimpleReadPlan(int antennaId) {
        return new SimpleReadPlan(new int[]{antennaId}, TagProtocol.GEN2, null, null, 0);
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
                    System.out.printf("isFtDevice = %s%n", isFtDevice);
                    System.out.println();
                    System.out.printf("hasPermission = %s", hasPermission);
                    System.out.println();
                    System.out.printf("getInterfaceCount = %s", getInterfaceCount);
                    System.out.println();
                    int addUsbDevice = ftD2xx.addUsbDevice(device);
                    System.out.printf("addUsbDevice = %s", addUsbDevice);
                    System.out.println();
                    FT_Device ftdev = ftD2xx.openByUsbDevice(context, device);
                    System.out.printf("ftdev = %s", ftdev);
                    System.out.println();
                    new AndroidUsbReflection(manager, ftdev, device, device.getDeviceClass());
                } catch (D2xxManager.D2xxException e) {
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
            thingMagicReader.addTransportListener(Reader.simpleTransportListener);
            if (isConnected()) {
                setupReaderParameters(licenseKey, rfidBand, isOldThingMagicModule);
            }
        } catch (Exception e) {
            e.printStackTrace();
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
        System.out.printf("Region: %s", thingMagicReader.paramGet(TMConstants.TMR_PARAM_REGION_ID));
        System.out.println();
        System.out.printf("Hardware: %s", thingMagicReader.paramGet(TMConstants.TMR_PARAM_VERSION_HARDWARE));
        System.out.println();
        System.out.printf("Serial: %s", thingMagicReader.paramGet(TMConstants.TMR_PARAM_VERSION_SERIAL));
        System.out.println();
        System.out.printf("Model: %s", thingMagicReader.paramGet(TMConstants.TMR_PARAM_VERSION_MODEL));
        System.out.println();
        System.out.printf("Software: %s", thingMagicReader.paramGet(TMConstants.TMR_PARAM_VERSION_SOFTWARE));
        System.out.println();
        System.out.printf("Command timeout: %s", thingMagicReader.paramGet(TMConstants.TMR_PARAM_COMMANDTIMEOUT));
        System.out.println();
        System.out.printf("Transport timeout: %s", thingMagicReader.paramGet(TMConstants.TMR_PARAM_TRANSPORTTIMEOUT));
        System.out.println();
    }

    private void setupReaderDefaults() throws Exception {
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_TAGREADDATA_UNIQUEBYANTENNA, false);
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_TAGREADDATA_RECORDHIGHESTRSSI, true);
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_ENABLE_READ_FILTERING, true);
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_COMMANDTIMEOUT, 4000);
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_TRANSPORTTIMEOUT, 14000);
//        thingMagicReader.paramSet(TMConstants.TMR_PARAM_POWERMODE, Reader.Region);
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
                System.out.printf("Read plan is %s", readPlanAntInd[readPlanIndex].toString());
            System.out.println();
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
        result.setTag(tagReadData.getTag());
        result.setTIDMemData(tagReadData.getTIDMemData());

        return result;
    }

    public void returnObject(final TagReadData o) {
        tagReadDataPool.returnObject(o);
    }

    public static String shortsToHexString(short[] shorts) {
        if (shorts == null || shorts.length == 0) {
            return "";
        }
        StringBuilder hex = new StringBuilder();
        for (short s : shorts) {
            // Convert short to two bytes (big-endian)
            hex.append(String.format("%02X", (s >> 8) & 0xFF)); // High byte
            hex.append(String.format("%02X", s & 0xFF));        // Low byte
        }
        return hex.toString();
    }

    public static String bytesToHexString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public TagReadData[] readTemperatureCode(final int antenna, final long readDuration, TagReadData tagReadData) throws Exception {
        final Gen2.Select gen2Select = new Gen2.Select(false, Gen2.Bank.USER, TEMPERATURE_SENSOR_BIT_POINTER, 0, new byte[]{});
        final TagOp onChipTempRead = new Gen2.ReadData(Gen2.Bank.RESERVED, TEMPERATURE_CODE_WORD_ADDRESS, (byte) 1);
//        Object response = thingMagicReader.executeTagOp(onChipTempRead, gen2Select);
//        System.out.println("response = " + shortsToHexString((short[]) response));
//
//        // Keep weight high to make power cycle longer.
        final SimpleReadPlan readPlan = new SimpleReadPlan(new int[]{antenna}, TagProtocol.GEN2, gen2Select, onChipTempRead, 3000, true);
        thingMagicReader.paramSet(TMConstants.TMR_PARAM_READ_PLAN, readPlan);
//        thingMagicReader.paramSet(TMConstants.TMR_PARAM_GEN2_T4, 3000);
        TagReadData[] result = read(readDuration);
        System.out.println("readTemperatureCode result = " + Arrays.toString(result));
//
//        // Read temperature code from the tag.
//        String epc = tagReadData.getEpc();
//        String[] epcPrefixes = {"00000000", "00004716", "00004717"};
//        SensorTagType type = detectTagType(epc);
//        System.out.println("SensorTagType(" + epc + ") = " + type);
//        final TagFilter select = new Gen2.Select(false, Gen2.Bank.USER, 0xE0, 0, new byte[]{});
//
//        short[] tidData = thingMagicReader.readTagMemWords(tagReadData.getTag(), Gen2.Bank.TID.rep, 0, 6);
//        int mdid = ((tidData[1] & 0x01) << 8) | (tidData[2] & 0xFF);
//        mdid = mdid >> 7;
//        System.out.println("tidData = " + shortsToHexString(tidData));
//        System.out.println("tidData (mdid) = " + mdid);
//        String tmnKey = String.format("%02X-%02X%02X", mdid, tidData[2] & 0xFF, tidData[3] & 0xFF);
//        System.out.println("tidData (mdid) = " + tmnKey);
//        System.out.println("getTidMemData = " + bytesToHexString(tagReadData.getTidMemData()));

//        byte[] userData = thingMagicReader.readTagMemBytes(
//                tagReadData.getTag(),
//                Gen2.Bank.RESERVED.rep,
//                0x0E,  // Start address
//                2   // Number of bytes for temperature
//        );
//        System.out.println("tempRaw = " + Arrays.toString(userData));
//        int tempRaw = ((userData[0] & 0xFF) << 8) | (userData[1] & 0xFF);
//        float t = parseTemperature(tempRaw);
//        System.out.println("temperature (tid) = " + t);

//        Gen2.ReadData readOp = new Gen2.ReadData(
//                Gen2.Bank.USER,    // Memory bank
//                0x0E,              // Starting word address for temperature
//                (byte) 1           // Number of words to read (1 word = 2 bytes)
//        );
//        Gen2.TagData targetTag = new Gen2.TagData(tagReadData.getEpc());
//        TagFilter filter = new Gen2.Select(false, Gen2.Bank.EPC, 32, tagReadData.getEpc().length() * 4, targetTag.epcBytes());
//        Object response = thingMagicReader.executeTagOp(readOp, filter);
//        System.out.println("response = " + shortsToHexString((short[]) response));

//        Object tidObject = thingMagicReader.executeTagOp(new Gen2.ReadData(Gen2.Bank.RESERVED, 0xE, (byte) 1), select);
//        System.out.println("tidObject = " + shortsToHexString((short[]) tidObject));
//
//        // Read temperature code (1 word)
//        Object tempCodeBytes = thingMagicReader.executeTagOp(
//                new Gen2.ReadData(Gen2.Bank.EPC, 2, (byte) 8), select);
//        if (tempCodeBytes instanceof short[]) {
//            System.out.println("tempCodeBytes = " + Arrays.toString((short[]) tempCodeBytes));
//            MagnusTemperature temperature = parseMagnusS3Data((short[]) tempCodeBytes, epc);
//            System.out.println("parseMagnusS3Data: temperature = " + temperature);
//        } else {
//            System.out.println("tempCodeBytes = " + tempCodeBytes);
//        }

        // Read calibration block (4 words)
//        short[] calibrationBytes = (short[]) thingMagicReader.executeTagOp(
//                new Gen2.ReadData(Gen2.Bank.USER, USER_MEM_CALIBRATION_ADDR, (byte) 4), select);
//        System.out.println("calibrationBytes = " + Arrays.toString(calibrationBytes));
//        final TagOp onChipTempRead = new Gen2.ReadData(Gen2.Bank.RESERVED, TEMPERATURE_CODE_WORD_ADDRESS, (byte) 1);
//
//        // Keep weight high to make power cycle longer.
//        final SimpleReadPlan readPlan = new SimpleReadPlan(new int[]{antenna}, TagProtocol.GEN2, select, onChipTempRead, TEMPERATURE_WEIGHT, true);
//        thingMagicReader.paramSet(TMConstants.TMR_PARAM_READ_PLAN, readPlan);
//        return read(readDuration);
        return result;
    }

    private float parseTemperature(int rawValue) {
        // E282 specific conversion - adjust based on datasheet
        // Common format: signed 16-bit value with resolution (e.g., 0.1°C)
        if (rawValue > 32767) {
            rawValue -= 65536; // Handle negative temperatures
        }
        return rawValue / 10.0f; // Adjust divisor based on resolution
    }

    public static class MagnusTemperature {
        public String fullEpc;          // Complete EPC with temperature
        public String baseEpc;          // Base EPC (ID without temp)
        public double temperature;      // Temperature in Celsius
        public int rawTempCode;        // Raw temperature code
        public int rssi;               // Signal strength
        public long timestamp;         // Read timestamp

        public MagnusTemperature(String fullEpc, String baseEpc, double temperature,
                                 int rawTempCode, int rssi, long timestamp) {
            this.fullEpc = fullEpc;
            this.baseEpc = baseEpc;
            this.temperature = temperature;
            this.rawTempCode = rawTempCode;
            this.rssi = rssi;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return String.format("Magnus S3 [%s] Temp: %.1f°C (raw: 0x%04X) RSSI: %d dBm",
                    baseEpc, temperature, rawTempCode, rssi);
        }
    }

    /**
     * Parse Magnus S3 data from read operation
     */
    private MagnusTemperature parseMagnusS3Data(short[] data, String originalEpc) {
        if (data.length < 6) {
            System.out.println("Insufficient data received: " + data.length + " words");
            return null;
        }

        // Reconstruct full EPC from data
        StringBuilder epcBuilder = new StringBuilder();
        for (short word : data) {
            epcBuilder.append(String.format("%04X", word & 0xFFFF));
        }

        String fullEpc = epcBuilder.toString();
        return parseTemperatureFromEPC(fullEpc, 0);
    }

    /**
     * Parse Magnus S3 temperature from EPC string
     * Magnus S3 structure: [Base EPC][Temperature Code][Checksum]
     */
    private MagnusTemperature parseTemperatureFromEPC(String epc, int rssi) {
        if (epc == null || epc.length() < 24) {
            System.out.printf("EPC too short: %s", epc);
            System.out.println();
            return null;
        }

        try {
            // Magnus S3 typical EPC structure (24 hex chars = 96 bits):
            // - First 16 chars (64 bits): Base EPC/ID
            // - Next 4 chars (16 bits): Temperature data
            // - Last 4 chars (16 bits): Moisture/other sensors or checksum

            String baseEpc = epc.substring(0, Math.min(16, epc.length()));

            // Temperature is typically in bytes 8-9 (chars 16-19)
            if (epc.length() >= 20) {
                String tempHex = epc.substring(16, 20);
                int tempCode = Integer.parseInt(tempHex, 16);

                // Convert to temperature using Magnus S3 formula
                double temperature = convertMagnusS3Temperature(tempCode);

                // Sanity check
                if (temperature >= -40 && temperature <= 85) {
                    return new MagnusTemperature(
                            epc,
                            baseEpc,
                            temperature,
                            tempCode,
                            rssi,
                            System.currentTimeMillis()
                    );
                } else {
                    System.out.println("Temperature out of range: " + temperature);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Convert Magnus S3 temperature code to Celsius
     * Magnus S3 uses a 16-bit temperature encoding
     * Formula: Temperature = (code - 2731.5) / 10
     * This gives temperature in Celsius with 0.1°C resolution
     */
    private double convertMagnusS3Temperature(int tempCode) {
        // Method 1: Standard Magnus S3 formula
        // Temperature in Kelvin * 10, subtract 2731.5 to get Celsius
        double temp1 = (tempCode - 2731.5) / 10.0;

        // Method 2: Alternative formula for some Magnus S3 variants
        // Direct encoding: (code * 0.0625) - 40
        double temp2 = (tempCode * 0.0625) - 40.0;

        // Method 3: Simple linear mapping
        // Some variants use: (code / 10) - 273.15
        double temp3 = (tempCode / 10.0) - 273.15;

        // Use Method 1 (most common), but log others for debugging
        System.out.printf("Temp conversions - M1: %.2f, M2: %.2f, M3: %.2f", temp1, temp2, temp3);
        System.out.println();

        // Return the most reasonable value
        if (temp1 >= -40 && temp1 <= 85) return temp1;
        if (temp2 >= -40 && temp2 <= 85) return temp2;
        if (temp3 >= -40 && temp3 <= 85) return temp3;

        return temp1; // Default to method 1
    }

    public enum SensorTagType {
        SL900A,      // AMS sensor tag
        MAGNUS_S3,   // Magnus S3 sensor tag
        AXZON,       // Axzon sensor tag
        GENERIC      // Generic approach
    }

    /**
     * Detect tag type automatically
     */
    public SensorTagType detectTagType(String epc) {
        // Try different memory locations to detect tag type
        TagFilter filter = new Gen2.Select(false, Gen2.Bank.EPC, 32,
                epc.length() * 4,
                hexStringToByteArray(epc));

        try {
            // Try SL900A signature read
            Gen2.ReadData readOp = new Gen2.ReadData(Gen2.Bank.USER, 0x00, (byte) 2);
            thingMagicReader.executeTagOp(readOp, filter);
            System.out.println("Detected as SL900A or compatible");
            return SensorTagType.SL900A;
        } catch (Exception e) {
            // Not SL900A, try others
        }

        try {
            // Try RESERVED bank (Axzon tags)
            Gen2.ReadData readOp = new Gen2.ReadData(Gen2.Bank.RESERVED, 0x0A, (byte) 1);
            thingMagicReader.executeTagOp(readOp, filter);
            System.out.println("Detected as Axzon or compatible");
            return SensorTagType.AXZON;
        } catch (Exception e) {
            // Not Axzon
        }

        return SensorTagType.GENERIC;
    }

    public TagReadData[] readTemperatureCalibration(final String epc, final int antenna, final long readDuration) throws Exception {
        // Read 3 words 9h, Ah, Bh. Do not read 8h it includes CRC code which we do not use right now.
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
        if (power.intValue() == readPower.intValue()) {
            // Value already set.
            return;
        }
        try {
            readPower = power;
            thingMagicReader.paramSet(TMConstants.TMR_PARAM_RADIO_READPOWER, 800);

            System.out.printf("TM power was changed: %d", readPower);
            System.out.println();
        } catch (ReaderException e) {
            e.printStackTrace();
        }
    }
}
