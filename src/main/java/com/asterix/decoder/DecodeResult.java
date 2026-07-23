package com.asterix.decoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Aggregated output of decoding one bitstream file. */
public class DecodeResult {
    public long totalBlocks;      // ASTERIX data blocks seen (= "packets received")
    public long okBlocks;         // decoded without error
    public long errorBlocks;      // decode raised an exception / desync
    public long bytes;            // bytes consumed

    /** blocks per ASTERIX category (34, 48, 8, ...) */
    public final Map<Integer, Long> perCategory = new TreeMap<>();
    /** blocks per receiver, keyed (SAC<<8|SIC) — everything that arrived */
    public final Map<Integer, Long> perSourceReceived = new TreeMap<>();
    /** blocks per receiver that were successfully decoded */
    public final Map<Integer, Long> perSourceProcessed = new TreeMap<>();

    public final List<Cat048Plot> plots = new ArrayList<>();

    public static int sourceKey(int sac, int sic) {
        return ((sac & 0xFF) << 8) | (sic & 0xFF);
    }
    public static int sacOf(int key) { return (key >> 8) & 0xFF; }
    public static int sicOf(int key) { return key & 0xFF; }
}
