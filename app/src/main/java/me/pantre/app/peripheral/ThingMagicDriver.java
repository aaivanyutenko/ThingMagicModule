package me.pantre.app.peripheral;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.widget.Toast;

import com.thingmagic.ReaderException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.pantre.app.bean.peripheral.DragonFruitFacade;
import me.pantre.app.model.InventoryReadItem;
import me.pantre.app.model.RfidBand;
import me.pantre.app.peripheral.model.TagReadData;
import me.pantre.app.peripheral.model.TagReadDataCache;
import me.pantre.app.peripheral.model.TagTemperatureReadData;

/**
 * ThingMagic driver read data from antennas.
 * 0614c1d4dd4dad69	device
 * 061dc1d4dd4dad69	device
 * 121809d4d5acffc8	device
 * 1e0e11d4da2da0b0	device
 * adb -s 121809d4d5acffc8 uninstall me.pantre.app
 * adb -s 1e0e11d4da2da0b0 shell am force-stop me.pantre.app
 * adb -s 121809d4d5acffc8 shell am force-stop me.pantre.app
 * adb -s 121809d4d5acffc8 shell am force-stop com.pantrylabs.watchdog
 * adb -s 121809d4d5acffc8 shell dpm remove-active-admin com.pantrylabs.watchdog/android.app.admin.DeviceAdminReceiver
 * adb -s 121809d4d5acffc8 root
 * tempCodeBytes = [-9582, -29416]
 * init
 * uid=1000(system) pool-1-thread-1 identical 1 line
 * init
 * calibrationBytes = [0, 0, 0, 0]
 */
public class ThingMagicDriver {

    private static final boolean IS_LOGGING_ENABLED = true;
    /**
     * Maximum empty cycles count .
     */
    private static final int SHELVES_COUNT = 4;

    /**
     * License key.
     */
    private static final String LICENSE_KEY = "acb424f2ae5feb3494e7d52a73dbb026";

    /**
     * Read duration value for half and max duty cycles (in ms).
     */
    public static long READ_DURATION_IND_LONG = 500;

    /**
     * Antenna sleep value for half and max duty cycles (in ms).
     */
    private static final long ANTENNA_SLEEP_SHORT = 60,
            ANTENNA_SLEEP_LONG = 500;

    /**
     * EPC length filtering value
     **/
    private static final int EPC_VALID_LENGTH = 24;

    /**
     * ThingMagic reader wrapper. Help to read date from device.
     */
    private final DragonfruitThingMagicWrapper thingMagicReaderWrapper;
    private final DragonFruitFacade dragonFruitFacade;

    /**
     * Sleep after reading or not.
     */
    private final boolean shouldSleepAfterReading;
    /**
     * How many RFID chip has antennas.
     */
    private final int chipAntennasCount;
    /**
     * How many real antennas we have.
     */
    private final int realAntennasCount;

    /**
     * Value in ms for each individual antenna read.
     */
    private long readDurationInd = READ_DURATION_IND_LONG;
    /**
     * Time gap between different antennas for 860ms ~= 50% load
     */
    private long antennaSleep = ANTENNA_SLEEP_SHORT; //

    private long readingCycleNumber = 0;

    /**
     * Cache of tags found during reading cycle
     */
    private final TagReadDataCache tagReadCache = new TagReadDataCache();

    /**
     * Inventory map.
     */
    private final ConcurrentHashMap<String, InventoryReadItem> inventoryReadMap = new ConcurrentHashMap<>();

    /**
     * Store calibration data to avoid reread. They are predefined by manufacturer.
     */
    private final Map<String, TagTemperatureReadData> calibrationMap = new HashMap<>();

    public ThingMagicDriver(DragonFruitFacade dragonFruitFacade, final DragonfruitThingMagicWrapper thingMagicReaderWrapper,
                            final boolean shouldSleepAfterReading, final int chipAntennasCount, final int realAntennasCount) {
        System.out.printf("ThingMagicDriver started. Antennas: %d-%d", chipAntennasCount, realAntennasCount);
        System.out.println();
        this.dragonFruitFacade = dragonFruitFacade;
        this.thingMagicReaderWrapper = thingMagicReaderWrapper;

        this.shouldSleepAfterReading = shouldSleepAfterReading;
        this.chipAntennasCount = chipAntennasCount;
        this.realAntennasCount = realAntennasCount;

        // Initialize arrays.
        thingMagicReaderWrapper.createReadPlans(chipAntennasCount);
    }

    /**
     * Connect to the device.
     */
    public void connect(final Context context) {
        boolean connectionFailed = false;

        try {
            boolean isOldThingMagicModule = thingMagicReaderWrapper.initializeUsbDevice(context);
            if (thingMagicReaderWrapper.deviceHasPermission) {
                thingMagicReaderWrapper.createReader();
                System.out.printf("Trying to connect ThingMagic");
                System.out.println();
                thingMagicReaderWrapper.connect(LICENSE_KEY, RfidBand.US902, isOldThingMagicModule);
            }

            if (thingMagicReaderWrapper.isConnected()) {
                System.out.printf("Already connected to ThingMagic");
                System.out.println();

                // Show a toast.
                final Handler h = new Handler(context.getMainLooper());
                h.post(() -> Toast.makeText(context, "PD3 ready", Toast.LENGTH_LONG).show());

                startReading();
            } else {
                // Connection failed. Giving up.
                System.out.println("TM connection failed. Giving up.");
                connectionFailed = true;
            }

        } catch (Exception e) {
            e.printStackTrace();
            connectionFailed = true;
        }

        if (!connectionFailed) {
            // TAB-642 start with half duty cycle
            setHalfDutyCycle();
        }
    }

    static byte ocrssiMin = 3;
    static byte ocrssiMax = 31;

    private void startReading() throws ReaderException {
        System.out.println("Started reading");
        setupPreferences();
        //noinspection InfiniteLoopStatement
        while (true) {
//            Gen2.Select tempsensorEnable = Common.createGen2Select(4, 5, Gen2.Bank.USER, 0xE0, 0, new byte[]{});
//            Gen2.Select ocrssiMinFilter = Common.createGen2Select(4, 0, Gen2.Bank.USER, 0xD0, 8, new byte[]{(byte) (0x20 | (ocrssiMin - 1))});
//            Gen2.Select ocrssiMaxFilter = Common.createGen2Select(4, 2, Gen2.Bank.USER, 0xD0, 8, new byte[]{ocrssiMax});
//            MultiFilter selects = new MultiFilter(new Gen2.Select[]{tempsensorEnable, ocrssiMinFilter, ocrssiMaxFilter});
//            // parameters to read all three sensor codes at once
//            Gen2.ReadData operation = new Gen2.ReadData(Gen2.Bank.RESERVED, 0xC, (byte) 3);
//
//            // create configuration
//            SimpleReadPlan config = new SimpleReadPlan(Common.antennas, TagProtocol.GEN2, selects, operation, 1000);
//
//            thingMagicReaderWrapper.thingMagicReader.paramSet(TMConstants.TMR_PARAM_READ_PLAN, config);
//            thingMagicReaderWrapper.thingMagicReader.paramSet(TMConstants.TMR_PARAM_GEN2_T4, 3000);  // CW delay in microseconds
//            thingMagicReaderWrapper.thingMagicReader.paramSet(TMConstants.TMR_PARAM_GEN2_SESSION, Common.session);
//            thingMagicReaderWrapper.thingMagicReader.paramSet(TMConstants.TMR_PARAM_GEN2_Q, new Gen2.DynamicQ());
//
//            // attempt to read sensor tags
//            com.thingmagic.TagReadData[] results = thingMagicReaderWrapper.thingMagicReader.read(Common.readTime);
//
//            // optimize settings for reading an individual tag's memory
//            thingMagicReaderWrapper.thingMagicReader.paramSet(TMConstants.TMR_PARAM_GEN2_T4, 300);
//            thingMagicReaderWrapper.thingMagicReader.paramSet(TMConstants.TMR_PARAM_GEN2_SESSION, Gen2.Session.S0);
//            thingMagicReaderWrapper.thingMagicReader.paramSet(TMConstants.TMR_PARAM_GEN2_Q, new Gen2.StaticQ(0));
//
//            if (results.length == 0) {
//                System.out.println("No tag(s) found");
//            }
//            for (com.thingmagic.TagReadData tag : results) {
//                String epc = tag.epcString();
//                System.out.println("* EPC: " + epc);
//                short[] dataWords = Common.convertByteArrayToShortArray(tag.getData());
//                if (dataWords.length == 0) {
//                    continue;
//                }
//                int moistureCode = dataWords[0];
//                int ocrssiCode = dataWords[1];
//                int temperatureCode = dataWords[2];
//
//                // On-Chip RSSI Sensor
//                System.out.println("  - On-Chip RSSI: " + ocrssiCode);
//
//                // Moisture Sensor
//                String moistureStatus;
//                if (ocrssiCode < 5) {
//                    moistureStatus = "power too low";
//                } else if (ocrssiCode > 21) {
//                    moistureStatus = "power too high";
//                } else {
//                    moistureStatus = moistureCode + " at " + tag.getFrequency() + " kHz";
//                }
//                System.out.println("  - Moisture: " + moistureStatus);
//
//                // Temperature Sensor
//                String temperatureStatus;
//                if (ocrssiCode < 5) {
//                    temperatureStatus = "power too low";
//                } else if (ocrssiCode > 18) {
//                    temperatureStatus = "power too high";
//                } else if (temperatureCode < 1000 || 3500 < temperatureCode) {
//                    temperatureStatus = "bad read";
//                } else {
//                    try {
//                        // read, decode and apply calibration one tag at a time
//                        short[] calibrationWords = Common.readMemBlockByEpc(thingMagicReaderWrapper.thingMagicReader, tag, Gen2.Bank.USER, 8, 4);
//                        TemperatureCalibration cal = new TemperatureCalibration(calibrationWords);
//                        if (cal.valid) {
//                            double temperatureValue = cal.slope * temperatureCode + cal.offset;
//                            temperatureStatus = String.format("%.02f degC", temperatureValue);
//                        } else {
//                            temperatureStatus = "invalid calibration";
//                        }
//                    } catch (RuntimeException e) {
//                        temperatureStatus = "failed to read calibration";
//                    }
//                }
//                System.out.println("  - Temperature: " + temperatureStatus);
//            }
//            System.out.println();

            if (IS_LOGGING_ENABLED)
                System.out.printf("inside startReading(). readingCycleNumber=%d", readingCycleNumber);
            System.out.println();

            // Create new inventory map
            final Map<String, InventoryReadItem> invReadItemMapForCycle = new HashMap<>(inventoryReadMap);

            try { // Catch all unpredictable exceptions
                for (int shelf = 1; shelf <= SHELVES_COUNT; shelf++) {
                    dragonFruitFacade.setShelf(shelf);
                    readPlans(shelf, readingCycleNumber + 1, invReadItemMapForCycle);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }

            // Increment cycle number.
            readingCycleNumber++;

            // Apply readings.
            inventoryReadMap.clear();
            inventoryReadMap.putAll(invReadItemMapForCycle);

            // Read through all antennas is done, propagate events.

            readTemperatureTags();
        }
    }

    private void setupPreferences() {
        try {
            thingMagicReaderWrapper.paramSetTari("TARI_25US");
            thingMagicReaderWrapper.paramSetBlf("LINK250KHZ");
            thingMagicReaderWrapper.paramSetTagEncoding("M4");
            thingMagicReaderWrapper.paramSetQAlgorithm("dynamic");
            thingMagicReaderWrapper.paramSetSession("S0");
            thingMagicReaderWrapper.paramSetTarget("A");
        } catch (Exception e) {
            System.out.println("setupPreferences error:");
        }
    }

    private void readTemperatureTags() {
        if (IS_LOGGING_ENABLED)
            System.out.println("Read flag is set. Initialize reading tag temperature flow.");

        List<TagReadData> temperatureTagsData = tagReadCache.getTemperatureTags();

        if (temperatureTagsData.isEmpty()) {
            if (IS_LOGGING_ENABLED)
                System.out.println("Cannot find any temperature tags in tagReadCache. Nothing to do.");
            return;
        }

        try { // Catch all unpredictable exceptions
            for (final TagReadData temperatureTagData : temperatureTagsData) {
                if (IS_LOGGING_ENABLED)
                    System.out.printf("Read tag temperature[ antenna: %d multiplier: %d rssi %d ]", temperatureTagData.getAntenna(), temperatureTagData.getAntennaMultiplier(), temperatureTagData.getRssi());
                System.out.println();
                final List<Integer> shelvesList = List.of(temperatureTagData.getAntennaMultiplier());
                for (int shelf = 1; shelf <= SHELVES_COUNT; shelf++) {
                    if (shelvesList.contains(shelf)) {
                        dragonFruitFacade.setShelf(shelf);
                        readTemperature(temperatureTagData, temperatureTagData.getAntenna());
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * Read plans.
     */
    private void readPlans(final int antennaMultiplier, final long readingCycleNumber,
                           final Map<String, InventoryReadItem> inventoryReadMap) throws Exception {
        for (int i = 0; i < thingMagicReaderWrapper.getReadPlanCount(); i++) {
            thingMagicReaderWrapper.paramSetReadPlan(i);
            read(readDurationInd, antennaMultiplier, readingCycleNumber, inventoryReadMap, i);

            SystemClock.sleep(getAntennaSleep());
        }
    }

    /**
     * Read temperature from RFMicron tag.
     */
    private void readTemperature(final TagReadData tagReadData, final int antenna) {
        TagTemperatureReadData tagTemperatureReadData = null;
        try {
            tagTemperatureReadData = readTagTemperature(tagReadData, antenna, readDurationInd);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (tagTemperatureReadData != null && tagTemperatureReadData.getTemperature() != 0) {
            System.out.println("tagTemperatureReadData.getTemperature() = " + tagTemperatureReadData.getTemperature());
        }
    }

    /**
     * Read temperature from RFMicron tag.
     * IMPORTANT: We have a pool of read data object and handle them manually.
     */
    private TagTemperatureReadData readTagTemperature(final TagReadData tagReadData, final int antenna, final long readDuration) throws Exception {
        System.out.println("readTagTemperature() called with: epc = [" + tagReadData.getEpc() + "], antenna = [" + antenna + "], readDuration = [" + readDuration + "]");
        String epc = tagReadData.getEpc();
        final TagReadData[] tagReads = thingMagicReaderWrapper.readTemperatureCode(antenna, readDuration, tagReadData);


        byte[] temperatureCodeData = null;
        for (final TagReadData trd : tagReads) {
            if (tagReadData.isTemperatureTag() && trd.getEpc().equals(epc)) {
                temperatureCodeData = trd.getData();
            }
            thingMagicReaderWrapper.returnObject(tagReadData);
        }

        if (temperatureCodeData == null) {
            if (IS_LOGGING_ENABLED) System.out.println("Can't read temperature tag.");
            return null;
        }

        final TagTemperatureReadData calibrationReadData = readCalibrationTagTemperature(epc, antenna, readDuration);
        if (calibrationReadData == null) {
            if (IS_LOGGING_ENABLED) System.out.println("Can't read calibration data.");

            return null;
        }

        final TagTemperatureReadData result = new TagTemperatureReadData();
        if (result.setCalibrationData(calibrationReadData.getData())
                && result.setTemperatureCodeData(temperatureCodeData)) {
            return result;
        }

        return null;
    }


    /**
     * Read calibration data or get from the cache.
     * IMPORTANT: We have a pool of read data object and handle them manually.
     */
    private TagTemperatureReadData readCalibrationTagTemperature(final String epc, final int antenna, final long readDuration) throws Exception {
        if (!calibrationMap.containsKey(epc)) {
            if (IS_LOGGING_ENABLED) System.out.printf("Read calibration data for epc %s", epc);
            System.out.println();

            final TagReadData[] tagReads = thingMagicReaderWrapper.readTemperatureCalibration(epc, antenna, readDuration);

            final TagTemperatureReadData tagTemperatureReadData = new TagTemperatureReadData();
            for (final TagReadData tagReadData : tagReads) {
                if (tagReadData.isTemperatureTag() && epc.equals(tagReadData.getEpc())
                        && tagTemperatureReadData.setCalibrationDataWithCRC(tagReadData.getData())) {
                    calibrationMap.put(epc, tagTemperatureReadData);
                }
                thingMagicReaderWrapper.returnObject(tagReadData);
            }
        }

        return calibrationMap.get(epc);
    }


    /**
     * Read data.
     * IMPORTANT: We have a pool of read data object and handle them manually.
     */
    private void read(final long readOnMs, final int antennaMultiplier,
                      final long readingCycleNumber,
                      final Map<String, InventoryReadItem> inventoryReadMap, int plan) {
        try {
            final long timeBeforeRead = System.currentTimeMillis();
            final TagReadData[] tagReads = thingMagicReaderWrapper.read(readOnMs);
            if (IS_LOGGING_ENABLED) {
                System.out.printf("(hello) Done with read - shelf number: %d, num of tags read: %d, read time: %d\n", antennaMultiplier, tagReads.length, System.currentTimeMillis() - timeBeforeRead);
                for (TagReadData tagRead : tagReads) {
                    System.out.printf("\t(hello) tag = %s\n", tagRead.toString());
                }
            }

            int minRssi = -10;
            String epc;

            for (final TagReadData tagReadData : tagReads) {
                epc = tagReadData.getEpc();

                final int realAntenna = ((tagReadData.getAntenna() - 1) * (realAntennasCount / chipAntennasCount) + antennaMultiplier);
                // Count statistics at the first and include all data which we get form ThingMagic.
                if (tagReadData.getRssi() < minRssi) {
                    minRssi = tagReadData.getRssi();
                }

                tagReadData.setAntennaMultiplier(antennaMultiplier);

                if (epc.length() != EPC_VALID_LENGTH) { // Discard EPC that is not of length 24
                    System.out.printf("EPC ignored: %s", epc);
                    System.out.println();
                } else {

                    final InventoryReadItem existingItem = inventoryReadMap.get(epc);

                    if (existingItem == null
                            || readingCycleNumber > existingItem.getReadingCycleNumber()
                            || tagReadData.getRssi() > existingItem.getRssi()) {

                        final InventoryReadItem inventoryReadItem = InventoryReadItem.create(
                                tagReadData,
                                realAntenna,
                                readingCycleNumber,
                                thingMagicReaderWrapper.getReadPower(),
                                plan,
                                antennaMultiplier
                        );

                        inventoryReadMap.put(epc, inventoryReadItem);
                    }

                }

                // Update reading cycle to clear obsolete data
                tagReadCache.updateReadingCycle(readingCycleNumber);

                // Handle temperature tag
                if (tagReadData.isTemperatureTag()) {
                    tagReadCache.add(tagReadData);
//                    if (BuildConfig.DEBUG) {
                    if (IS_LOGGING_ENABLED)
                        System.out.printf("Found a tag temperature[ antenna: %d multiplier: %d rssi %d ]", tagReadData.getAntenna(), antennaMultiplier, tagReadData.getRssi());
                    System.out.println();
//                    }
                } else {
                    // To simplify GC work return object back to the pool
                    thingMagicReaderWrapper.returnObject(tagReadData);
                }
            }

            if (IS_LOGGING_ENABLED) System.out.printf("Min RSSI of the tags read above: %d", minRssi);
            System.out.println();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Configure device to half duty cycle.
     */
    public void setHalfDutyCycle() {
        readDurationInd = READ_DURATION_IND_LONG;
        antennaSleep = ANTENNA_SLEEP_LONG;

        thingMagicReaderWrapper.setReadPower(thingMagicReaderWrapper.getMaxReadPower());

        System.out.printf("TM durations were changed: antennaSleep=%d, readDurationInd=%d", antennaSleep, readDurationInd);
        System.out.println();
    }

    /**
     * If should sleep return antenna sleep, else 0.
     */
    private long getAntennaSleep() {
        if (shouldSleepAfterReading) {
            return antennaSleep;
        }

        return 0;
    }

    /**
     * @return true if device is connected.
     */
    public boolean isConnected() {
        return thingMagicReaderWrapper.isConnected();
    }

    static class TemperatureCalibration {
        public boolean valid = false;
        public int crc;
        public int code1;
        public double temp1;
        public int code2;
        public double temp2;
        public int ver;
        public double slope;
        public double offset;

        public TemperatureCalibration(short[] calWords) {
            // convert register contents to variables
            decode(calWords[0], calWords[1], calWords[2], calWords[3]);

            // calculate CRC-16 over non-CRC bytes to compare with stored CRC-16
            byte[] calBytes = Common.convertShortArrayToByteArray(new short[]{calWords[1], calWords[2], calWords[3]});
            int crcCalc = crc16(calBytes);

            // determine if calibration is valid
            if ((ver == 0) && (crc == crcCalc)) {
                slope = .1 * (temp2 - temp1) / (double) (code2 - code1);
                offset = .1 * (temp1 - 800) - (slope * (double) code1);
                valid = true;
            } else {
                valid = false;
            }
        }

        private void decode(short reg8, short reg9, short regA, short regB) {
            ver = regB & 0x0003;
            temp2 = (regB >> 2) & 0x07FF;
            code2 = ((regA << 3) & 0x0FF8) | ((regB >> 13) & 0x0007);
            temp1 = ((reg9 << 7) & 0x0780) | ((regA >> 9) & 0x007F);
            code1 = (reg9 >> 4) & 0x0FFF;
            crc = reg8 & 0xFFFF;
        }

        // EPC Gen2 CRC-16 Algorithm
        // Poly = 0x1021; Initial Value = 0xFFFF; XOR Output;
        private int crc16(byte[] inputBytes) {
            int crcVal = 0xFFFF;
            for (byte inputByte : inputBytes) {
                crcVal = (crcVal ^ (inputByte << 8));
                for (int i = 0; i < 8; i++) {
                    if ((crcVal & 0x8000) == 0x8000) {
                        crcVal = (crcVal << 1) ^ 0x1021;
                    } else {
                        crcVal = (crcVal << 1);
                    }
                }
                crcVal = crcVal & 0xFFFF;
            }
            crcVal = (crcVal ^ 0xFFFF);
            return crcVal;
        }
    }
}

