package me.pantre.app.peripheral;

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
import com.thingmagic.ReaderUtil;
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

    private static final int USER_MEM_TEMP_CODE_ADDR = 0xA0;  // 1 word
    private static final int USER_MEM_CALIBRATION_ADDR = 1; // 4 words

    private static final String TM_URI_STRING = "tmr:///dev";
    private static final String ACTION_USB_PERMISSION = "com.thingmagic.rfidreader.services.USB_PERMISSION";
    private static final int READ_POWER = 3000; // 30 dBm

    private Reader thingMagicReader;
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
            thingMagicReader.paramSet(TMConstants.TMR_PARAM_LICENSE_KEY, ReaderUtil.hexStringToByteArray(licenseKey));
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
        // Read temperature code from the tag.
        String[] epcPrefixes = {"00000000", "00004716", "00004717"};
        final TagFilter select = new Gen2.Select(false, Gen2.Bank.EPC, 32, epc.length() * 4, toByteArray(epc));

        // Read temperature code (1 word)
        Object tempCodeBytes = thingMagicReader.executeTagOp(
                new Gen2.ReadData(Gen2.Bank.USER, USER_MEM_TEMP_CODE_ADDR, (byte) 1), select);
        if (tempCodeBytes instanceof short[]) {
            System.out.println("tempCodeBytes = " + Arrays.toString((short[]) tempCodeBytes));
        } else {
            System.out.println("tempCodeBytes = " + tempCodeBytes);
        }

        // Read calibration block (4 words)
        short[] calibrationBytes = (short[]) thingMagicReader.executeTagOp(
                new Gen2.ReadData(Gen2.Bank.USER, USER_MEM_CALIBRATION_ADDR, (byte) 4), select);
        System.out.println("calibrationBytes = " + Arrays.toString(calibrationBytes));
//        final TagOp onChipTempRead = new Gen2.ReadData(Gen2.Bank.RESERVED, TEMPERATURE_CODE_WORD_ADDRESS, (byte) 1);
//
//        // Keep weight high to make power cycle longer.
//        final SimpleReadPlan readPlan = new SimpleReadPlan(new int[]{antenna}, TagProtocol.GEN2, select, onChipTempRead, TEMPERATURE_WEIGHT, true);
//        thingMagicReader.paramSet(TMConstants.TMR_PARAM_READ_PLAN, readPlan);
//        return read(readDuration);
        return null;
    }

    private byte[] toByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
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
            thingMagicReader.paramSet(TMConstants.TMR_PARAM_RADIO_READPOWER, READ_POWER);

            Timber.i("TM power was changed: %d", readPower);
        } catch (ReaderException e) {
            Timber.e(e);
        }
    }
}
