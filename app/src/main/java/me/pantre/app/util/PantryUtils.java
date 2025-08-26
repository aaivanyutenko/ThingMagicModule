package me.pantre.app.util;

import java.util.Arrays;

public final class PantryUtils {
    /**
     * Transform to int value because java doesn't have an unsigned byte.
     */
    public static int byteToInt(final byte b) {
        final int mask = 0xFF;

        return (int) b & mask;
    }

    /**
     * Transform byte array to long.
     */
    public static long byteArrayToLong(final byte[] bytes) {
        final int shift = Byte.SIZE;
        long result = 0;

        if (bytes != null) {
            for (byte b : bytes) {
                result = (result << shift) + byteToInt(b);
            }
        }

        return result;
    }


    /**
     * @return CRC in int event if crc code is 16 bit length. Short value has one bit of sign.
     */
    // Algorithm from "AN002F38 Reading Magnus-S Sensors.pdf"
    public static int crc16(final byte[] data) {
        final boolean[] crc = new boolean[Short.SIZE];
        // Set all crc values to true;
        Arrays.fill(crc, true);

        for (int m = data.length - 1; m >= 0; m--) {
            byte b = data[m];

            int v = PantryUtils.byteToInt(b);
            for (int j = Byte.SIZE - 1; j >= 0; j--) {
                // Use higher bit of v and xor it with higher bit of crc
                final int mask = 0x80;
                boolean bit = crc[crc.length - 1] ^ ((v & mask) != 0);
                v = v << 1;

                // Shift left all bits and xor bits with index 5 and 12
                final int bit5 = 5;
                final int bit12 = 12;
                for (int i = crc.length - 1; i >= 1; i--) {
                    if (i == bit12 || i == bit5) {
                        crc[i] = crc[i - 1] ^ bit;
                    } else {
                        crc[i] = crc[i - 1];
                    }
                }
                crc[0] = bit;
            }
        }

        // Reverse result
        for (int i = 0; i < crc.length; i++) {
            crc[i] ^= true;
        }

        // Convert to int value.
        return Integer.parseInt(bitSetToString(crc), 2);
    }

    /**
     * Converts bit set to string.
     */
    public static String bitSetToString(final boolean[] bitSet) {
        StringBuilder sb = new StringBuilder();
        for (int i = bitSet.length - 1; i >= 0; i--) {
            sb.append(bitSet[i] ? "1" : "0");
        }

        return sb.toString();
    }
}
