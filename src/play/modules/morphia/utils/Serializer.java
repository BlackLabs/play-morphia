package play.modules.morphia.utils;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Helper class to help serialize java object like BitSet
 * 
 * The code dealing with BitSet/byte[] conversion comes from
 * http://stackoverflow.com/questions/1378171/writing-a-bitset-to-a-file-in-java
 * 
 * @author greenl
 */
public class Serializer extends com.google.code.morphia.mapping.Serializer {
    public static BitSet asBitSet(byte[] ba) {
        if (ba == null)
            return new BitSet(0);
        BitSet bs = new BitSet(ba.length * 8);
        for (int i = 0; i < bs.size(); i++) {
            if (isBitOn_(i, ba)) {
                bs.set(i);
            }
        }
        return bs;
    }

    public static byte[] fromBitSet(BitSet bs) {
        if (null == bs) return new byte[0];
        int sz = bs.size();
        if (sz == 0)
            return new byte[0];

        // Find highest bit
        int hiBit = -1;
        for (int i = 0; i < sz; i++) {
            if (bs.get(i))
                hiBit = i;
        }

        int n = (hiBit + 8) / 8;
        byte[] bytes = new byte[n];
        if (n == 0)
            return bytes;

        Arrays.fill(bytes, (byte) 0);
        for (int i = 0; i < n * 8; i++) {
            if (bs.get(i))
                setBit_(i, bytes);
        }

        return bytes;
    }

    private static final int BIT_MASK_[] = { 0x80, 0x40, 0x20, 0x10, 0x08, 0x04, 0x02, 0x01 };

    private static void setBit_(int bit, byte[] bytes) {
        int size = bytes == null ? 0 : bytes.length * 8;

        if (bit >= size)
            throw new ArrayIndexOutOfBoundsException("Byte array too small");

        bytes[bit / 8] |= BIT_MASK_[bit % 8];
    }

    private static boolean isBitOn_(int bit, byte[] bytes) {
        int size = bytes == null ? 0 : bytes.length * 8;

        if (bit >= size)
            return false;

        return (bytes[bit / 8] & BIT_MASK_[bit % 8]) != 0;
    }
}
