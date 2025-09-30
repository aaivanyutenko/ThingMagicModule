package me.pantre.app.peripheral;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.widget.Toast;

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
import timber.log.Timber;

/**
 * ThingMagic driver read data from antennas.
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
        Timber.i("ThingMagicDriver started. Antennas: %d-%d", chipAntennasCount, realAntennasCount);
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
                Timber.i("Trying to connect ThingMagic");
                thingMagicReaderWrapper.connect(LICENSE_KEY, RfidBand.US902, isOldThingMagicModule);
            }

            if (thingMagicReaderWrapper.isConnected()) {
                Timber.i("Already connected to ThingMagic");

                // Show a toast.
                final Handler h = new Handler(context.getMainLooper());
                h.post(() -> Toast.makeText(context, "PD3 ready", Toast.LENGTH_LONG).show());

                startReading();
            } else {
                // Connection failed. Giving up.
                Timber.w("TM connection failed. Giving up.");
                connectionFailed = true;
            }

        } catch (Exception e) {
            Timber.e(e, "TM connection caught exception. Giving up. (%s)", e.getMessage());
            connectionFailed = true;
        }

        if (!connectionFailed) {
            // TAB-642 start with half duty cycle
            setHalfDutyCycle();
        }
    }

    private void startReading() {
        Timber.d("Started reading");
        setupPreferences();
        //noinspection InfiniteLoopStatement
        while (true) {
            if (IS_LOGGING_ENABLED)
                Timber.i("inside startReading(). readingCycleNumber=%d", readingCycleNumber);

            // Create new inventory map
            final Map<String, InventoryReadItem> invReadItemMapForCycle = new HashMap<>(inventoryReadMap);

            try { // Catch all unpredictable exceptions
                for (int shelf = 1; shelf <= SHELVES_COUNT; shelf++) {
                    dragonFruitFacade.setShelf(shelf);
                    readPlans(shelf, readingCycleNumber + 1, invReadItemMapForCycle);
                }
            } catch (Throwable e) {
                Timber.e(e, "Exception during inventory cycle");
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
            Timber.d("setupPreferences error:");
            Timber.e(e);
        }
    }

    private void readTemperatureTags() {
        if (IS_LOGGING_ENABLED)
            Timber.d("Read flag is set. Initialize reading tag temperature flow.");

        List<TagReadData> temperatureTagsData = tagReadCache.getTemperatureTags();

        if (temperatureTagsData.isEmpty()) {
            if (IS_LOGGING_ENABLED)
                Timber.d("Cannot find any temperature tags in tagReadCache. Nothing to do.");
            return;
        }

        try { // Catch all unpredictable exceptions
            for (final TagReadData temperatureTagData : temperatureTagsData) {
                if (IS_LOGGING_ENABLED)
                    Timber.v("Read tag temperature[ antenna: %d multiplier: %d rssi %d ]", temperatureTagData.getAntenna(), temperatureTagData.getAntennaMultiplier(), temperatureTagData.getRssi());
                final List<Integer> shelvesList = List.of(temperatureTagData.getAntennaMultiplier());
                for (int shelf = 1; shelf <= SHELVES_COUNT; shelf++) {
                    if (shelvesList.contains(shelf)) {
                        dragonFruitFacade.setShelf(shelf);
                        readTemperature(temperatureTagData.getEpc(), temperatureTagData.getAntenna());
                    }
                }
            }
        } catch (Throwable e) {
            Timber.e(e, "Exception during temperature reading");
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
    private void readTemperature(final String epc, final int antenna) {
        TagTemperatureReadData tagTemperatureReadData = null;
        try {
            tagTemperatureReadData = readTagTemperature(epc, antenna, readDurationInd);
        } catch (Exception e) {
            Timber.e(e, "ThingMagic temperature read exception");
        }

        if (tagTemperatureReadData != null && tagTemperatureReadData.getTemperature() != 0) {
            System.out.println("tagTemperatureReadData.getTemperature() = " + tagTemperatureReadData.getTemperature());
        }
    }

    /**
     * Read temperature from RFMicron tag.
     * IMPORTANT: We have a pool of read data object and handle them manually.
     */
    private TagTemperatureReadData readTagTemperature(final String epc, final int antenna, final long readDuration) throws Exception {
        Timber.d("readTagTemperature() called with: epc = [" + epc + "], antenna = [" + antenna + "], readDuration = [" + readDuration + "]");
        final TagReadData[] tagReads = thingMagicReaderWrapper.readTemperatureCode(antenna, readDuration, epc);


        byte[] temperatureCodeData = null;
        for (final TagReadData tagReadData : tagReads) {
            if (tagReadData.isTemperatureTag() && epc.equals(tagReadData.getEpc())) {
                temperatureCodeData = tagReadData.getData();
            }
            thingMagicReaderWrapper.returnObject(tagReadData);
        }

        if (temperatureCodeData == null) {
            if (IS_LOGGING_ENABLED) Timber.w("Can't read temperature tag.");
            return null;
        }

        final TagTemperatureReadData calibrationReadData = readCalibrationTagTemperature(epc, antenna, readDuration);
        if (calibrationReadData == null) {
            if (IS_LOGGING_ENABLED) Timber.w("Can't read calibration data.");

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
            if (IS_LOGGING_ENABLED) Timber.v("Read calibration data for epc %s", epc);

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
            dragonFruitFacade.mainActivity.onTagReads(tagReads);
            if (IS_LOGGING_ENABLED)
                Timber.d("Done with read - shelf number: %d, num of tags read: %d, read time: %d", antennaMultiplier, tagReads.length, System.currentTimeMillis() - timeBeforeRead);

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
                    Timber.w("EPC ignored: %s", epc);
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
                        Timber.v("Found a tag temperature[ antenna: %d multiplier: %d rssi %d ]", tagReadData.getAntenna(), antennaMultiplier, tagReadData.getRssi());
//                    }
                } else {
                    // To simplify GC work return object back to the pool
                    thingMagicReaderWrapper.returnObject(tagReadData);
                }
            }

            if (IS_LOGGING_ENABLED) Timber.v("Min RSSI of the tags read above: %d", minRssi);
        } catch (Exception e) {
            Timber.e(e, "ThingMagic read exception. Antenna multiplier %d", antennaMultiplier);
        }
    }

    /**
     * Configure device to half duty cycle.
     */
    public void setHalfDutyCycle() {
        readDurationInd = READ_DURATION_IND_LONG;
        antennaSleep = ANTENNA_SLEEP_LONG;

        thingMagicReaderWrapper.setReadPower(thingMagicReaderWrapper.getMaxReadPower());

        Timber.i("TM durations were changed: antennaSleep=%d, readDurationInd=%d", antennaSleep, readDurationInd);
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
}
