package com.asterix.decoder;

import org.springframework.stereotype.Component;

/**
 * ASTERIX decoder for the recording format used by this receiver.
 *
 * Wire format (verified against real captures):
 *   file  = [ frame ]*
 *   frame = uint16LE length | ASTERIX-data-block(length bytes)
 *   block = CAT(1) | LEN(2 BE) | FSPEC | data items...
 *
 * Only CAT048 is fully field-decoded (that is where ICAO address, Mode 3/A,
 * callsign, flight level and measured position live). CAT034/008 are counted
 * and their SAC/SIC read from the mandatory Data Source Identifier (FRN1),
 * which is the first item in every category.
 */
@Component
public class AsterixDecoder {

    // 6-bit ICAO character table used by I048/240 (Aircraft Identification).
    private static final String IA5 =
            " ABCDEFGHIJKLMNOPQRSTUVWXYZ_____ !\"#$%&'()*+,-./0123456789:;<=>?";

    // CAT048 User Application Profile. Each entry: {itemName, lengthRule}.
    // Length rules: digits = fixed length; "X" = extendable (FX-terminated,
    // 1-octet groups); "R:n" = repetitive (1 REP octet + REP*n); "C130"/"C120"
    // = compound items; "E" = explicit (first octet holds total length).
    private static final String[][] UAP = {
        {"010", "2"},  {"140", "3"},  {"020", "X"},   {"040", "4"},
        {"070", "2"},  {"090", "2"},  {"130", "C130"},{"220", "3"},
        {"240", "6"},  {"250", "R:8"},{"161", "2"},   {"042", "4"},
        {"200", "4"},  {"170", "X"},  {"210", "4"},   {"030", "X"},
        {"080", "2"},  {"100", "4"},  {"110", "2"},   {"120", "C120"},
        {"230", "2"},  {"RE",  "E"},  {"SP",  "E"}
    };

    public DecodeResult decode(byte[] data) {
        DecodeResult r = new DecodeResult();
        int off = 0;
        while (off + 2 <= data.length) {
            int frameLen = (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8);
            off += 2;
            if (frameLen == 0 || off + frameLen > data.length) break; // truncated tail
            int base = off;
            off += frameLen;
            r.totalBlocks++;
            r.bytes += frameLen + 2;
            try {
                decodeBlock(data, base, r);
                r.okBlocks++;
            } catch (Exception ex) {
                r.errorBlocks++;
            }
        }
        return r;
    }

    private void decodeBlock(byte[] b, int base, DecodeResult r) {
        int cat = b[base] & 0xFF;
        r.perCategory.merge(cat, 1L, Long::sum);

        int p = base + 3; // skip CAT(1) + LEN(2)

        // ---- FSPEC ----
        boolean[] present = new boolean[UAP.length];
        int frn = 0;
        while (true) {
            int octet = b[p++] & 0xFF;
            for (int bit = 7; bit >= 1; bit--) {
                if (frn < present.length) present[frn] = ((octet >> bit) & 1) != 0;
                frn++;
            }
            if ((octet & 1) == 0) break; // FX bit clear -> FSPEC ends
        }

        int sac = -1, sic = -1;
        int[] pos040 = null, pos070 = null, pos090 = null, pos220 = null, pos240 = null, pos140 = null, pos161 = null;

        for (int i = 0; i < UAP.length; i++) {
            if (i >= frn || !present[i]) continue;
            String name = UAP[i][0];
            String kind = UAP[i][1];
            int len = itemLength(b, p, kind);
            switch (name) {
                case "010" -> { sac = b[p] & 0xFF; sic = b[p + 1] & 0xFF; }
                case "140" -> pos140 = new int[]{p, len};
                case "040" -> pos040 = new int[]{p, len};
                case "070" -> pos070 = new int[]{p, len};
                case "090" -> pos090 = new int[]{p, len};
                case "161" -> pos161 = new int[]{p, len};
                case "220" -> pos220 = new int[]{p, len};
                case "240" -> pos240 = new int[]{p, len};
                default -> { /* other items: length-skipped only */ }
            }
            p += len;
        }

        if (sac >= 0) {
            int key = DecodeResult.sourceKey(sac, sic);
            r.perSourceReceived.merge(key, 1L, Long::sum);
            r.perSourceProcessed.merge(key, 1L, Long::sum);
        }

        // Any CAT048 target report with a position or an identity becomes a plot.
        // This includes Mode-S tracks (with ICAO), Mode A/C tracks (Mode 3/A, no
        // ICAO) and primary detections (position only) — the last identified by
        // the tracker's track number when present.
        if (cat == 48 && (pos040 != null || pos220 != null || pos070 != null)) {
            Cat048Plot plot = new Cat048Plot();
            plot.sac = sac;
            plot.sic = sic;
            if (pos220 != null) plot.icaoAddress = String.format("%02X%02X%02X",
                    b[pos220[0]] & 0xFF, b[pos220[0] + 1] & 0xFF, b[pos220[0] + 2] & 0xFF);
            if (pos240 != null) plot.callsign = callsign(b, pos240[0]);
            if (pos070 != null) plot.mode3A = mode3A(word(b, pos070[0]));
            if (pos090 != null) plot.flightLevel = flightLevel(word(b, pos090[0]));
            if (pos161 != null) plot.trackNumber = word(b, pos161[0]) & 0x0FFF;
            if (pos140 != null) plot.timeOfDay = timeOfDay(b, pos140[0]);
            if (pos040 != null) {
                plot.hasPolar = true;
                plot.rhoRaw = word(b, pos040[0]);
                plot.thetaRaw = word(b, pos040[0] + 2);
            }
            r.plots.add(plot);
        }
    }

    // ---- item length resolution ----
    private int itemLength(byte[] b, int p, String kind) {
        switch (kind) {
            case "X": { // extendable: read octets while FX bit set
                int s = p;
                while ((b[p] & 1) != 0) p++;
                p++;
                return p - s;
            }
            case "C130": { // compound: 1-octet primary subfield + one octet per set bit
                int prim = b[p] & 0xFF;
                return 1 + Integer.bitCount(prim & 0xFE);
            }
            case "C120": { // radial doppler speed: CAL(2) and/or RDS(1+REP*6)
                int s = p;
                int prim = b[p] & 0xFF; p++;
                if ((prim & 0x80) != 0) p += 2;
                if ((prim & 0x40) != 0) { int rep = b[p] & 0xFF; p++; p += rep * 6; }
                return p - s;
            }
            case "E": // explicit length: first octet = total length incl. itself
                return b[p] & 0xFF;
            default:
                if (kind.startsWith("R:")) {
                    int n = Integer.parseInt(kind.substring(2));
                    int rep = b[p] & 0xFF;
                    return 1 + rep * n;
                }
                return Integer.parseInt(kind);
        }
    }

    // ---- field decoders ----
    private static int word(byte[] b, int p) {
        return ((b[p] & 0xFF) << 8) | (b[p + 1] & 0xFF);
    }

    private static String callsign(byte[] b, int p) {
        long bits = 0; int nb = 0; StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            bits = (bits << 8) | (b[p + i] & 0xFF); nb += 8;
            while (nb >= 6) {
                nb -= 6;
                int c = (int) ((bits >> nb) & 0x3F);
                sb.append(c < IA5.length() ? IA5.charAt(c) : '?');
            }
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String mode3A(int w) {
        int v = w & 0x0FFF;
        return "" + ((v >> 9) & 7) + ((v >> 6) & 7) + ((v >> 3) & 7) + (v & 7);
    }

    private static double flightLevel(int w) {
        int v = w & 0x3FFF;
        if ((v & 0x2000) != 0) v -= 0x4000; // 14-bit two's complement, 1/4 FL units
        return v / 4.0;
    }

    private static double timeOfDay(byte[] b, int p) {
        int v = ((b[p] & 0xFF) << 16) | ((b[p + 1] & 0xFF) << 8) | (b[p + 2] & 0xFF);
        return v / 128.0; // seconds since midnight UTC
    }
}
