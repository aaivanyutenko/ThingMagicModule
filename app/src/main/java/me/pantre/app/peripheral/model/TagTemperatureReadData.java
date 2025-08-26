package me.pantre.app.peripheral.model;

import java.nio.ByteBuffer;
import java.util.Arrays;

import me.pantre.app.util.PantryUtils;
import timber.log.Timber;

public class TagTemperatureReadData extends TagReadData {
    private static final boolean IS_LOGGING_ENABLED = true;
    /**
     * The size of data array with calibration values.
     */
    private static final int CALIBRATION_DATA_SIZE = 6;
    /**
     * The size of data with CRC.
     */
    private static final int CRC_DATA_SIZE = 2;
    /**
     * The size of data array with calibration values and CRC.
     */
    private static final int CALIBRATION_DATA_WITH_CRC_SIZE = CRC_DATA_SIZE + CALIBRATION_DATA_SIZE;
    /**
     * The size of data array with temperature code values.
     */
    private static final int TEMPERATURE_CODE_DATA_SIZE = 2;

    /**
     * The Temperature Code measured at the first calibration point.
     */
    private int code1;
    /**
     * The Temperature Code measured at the second calibration point.
     */
    private int code2;
    /**
     * The actual temperature measured at the first calibration point.
     * /* (First calibration temperature in decimal degrees C) X 10 + 800
     */
    private int temp1;
    /**
     * The actual temperature measured at the second calibration point.
     * /* (Second calibration temperature in decimal degrees C) X 10 + 800
     */
    private int temp2;

    /**
     * Temperature code value.
     */
    private int temperatureCode;

    /**
     * Calculates and returns temperature in decimal degrees.
     */
    public double getTemperature() {
        final double divider = 10;
        final double shifter = 800;

        return ((double) (temp2 - temp1) * (double) (temperatureCode - code1) / (double) (code2 - code1) + temp1 - shifter) / divider;
    }

    /**
     * Set calibration data with crc code.
     * <p>
     * data contains crc code and values of 3 words 9h, Ah, and Bh.
     */
    public boolean setCalibrationDataWithCRC(final byte[] data) {
        if (IS_LOGGING_ENABLED)
            Timber.v("Calibration raw data with CRC: %s", Arrays.toString(data));

        if (checkCalibrationDataWithCRC(data)) {
            final byte[] crcData = Arrays.copyOfRange(data, 0, CRC_DATA_SIZE);
            if (IS_LOGGING_ENABLED) Timber.v("CRC raw data: %s", Arrays.toString(crcData));


            final byte[] calibrationData = Arrays.copyOfRange(data, CRC_DATA_SIZE, CALIBRATION_DATA_WITH_CRC_SIZE);
            if (IS_LOGGING_ENABLED) Timber.v("CRC calibration data: %s", Arrays.toString(crcData));


            // Reverse calibration data to calculate CRC value.
            final byte[] reversedCalibrationData = Arrays.copyOf(calibrationData, calibrationData.length);
            for (int i = 0; i < reversedCalibrationData.length / 2; i++) {
                byte tmp = reversedCalibrationData[i];
                reversedCalibrationData[i] = reversedCalibrationData[reversedCalibrationData.length - i - 1];
                reversedCalibrationData[reversedCalibrationData.length - i - 1] = tmp;
            }

            final long crcValue = PantryUtils.byteArrayToLong(crcData);
            final int calcCrcValue = PantryUtils.crc16(reversedCalibrationData);
            if (IS_LOGGING_ENABLED)
                Timber.v("CRC value: %d, calc CRC value: %d", crcValue, calcCrcValue);


            if (crcValue == calcCrcValue) {
                return setCalibrationData(calibrationData);
            } else {
                if (IS_LOGGING_ENABLED)
                    Timber.w("CRC check failed. CRC value: %d, calc CRC value: %d", crcValue, calcCrcValue);
            }
        }

        return false;
    }


    /**
     * Set calibration data.
     * <p>
     * data contains values of 3 words 9h, Ah, and Bh.
     */
    public boolean setCalibrationData(final byte[] data) {
        if (IS_LOGGING_ENABLED) Timber.v("Calibration raw data: %s", Arrays.toString(data));

        if (checkCalibrationData(data)) {
            final long value = PantryUtils.byteArrayToLong(data);

            // 12 bit length, bits 90 - 9B
            code1 = (int) ((value & 0xFFF000000000L) >> 36);
            // 11 bit length, bits 9C - A6
            temp1 = (int) ((value & 0xFFE000000L) >> 25);
            // 12 bit length, bits A7 - B2
            code2 = (int) ((value & 0x1FFE000L) >> 13);
            // 11 bit length, bits B3 - BD
            temp2 = (int) ((value & 0x1FFCL) >> 2);

            setData(data);


            if (IS_LOGGING_ENABLED)
                Timber.d("Calibration data: code1=%d, temp1=%d, code2=%d, temp2=%d", code1, temp1, code2, temp2);
            return true;
        }

        return false;
    }

    /**
     * Set temperature code as bytes array.
     */
    public boolean setTemperatureCodeData(final byte[] data) {
        if (IS_LOGGING_ENABLED) Timber.v("Temperature code raw data: %s", Arrays.toString(data));
        if (checkTemperatureCodeData(data)) {
            final ByteBuffer wrapped = ByteBuffer.wrap(data);

            // The Temperature Code occupies the least-significant 12 bits of the word; the other bits should be 0
            final int mask = 0x0FFF;
            temperatureCode = wrapped.getShort() & mask;

            if (IS_LOGGING_ENABLED) Timber.d("Temperature code is %d", temperatureCode);

            return true;
        }

        return false;
    }

    /**
     * @return true if calibration data has right array.
     */
    private boolean checkCalibrationDataWithCRC(final byte[] data) {
        return data != null && data.length == CALIBRATION_DATA_WITH_CRC_SIZE;
    }

    /**
     * @return true if calibration data has right array.
     */
    private boolean checkCalibrationData(final byte[] data) {
        return data != null && data.length == CALIBRATION_DATA_SIZE;
    }

    /**
     * @return true if temperature code data has right array.
     */
    private boolean checkTemperatureCodeData(final byte[] data) {
        return data != null && data.length == TEMPERATURE_CODE_DATA_SIZE;
    }
}
