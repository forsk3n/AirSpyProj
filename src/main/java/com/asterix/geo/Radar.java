package com.asterix.geo;

/** A surveillance sensor position, keyed elsewhere by SAC/SIC. */
public record Radar(int sac, int sic, double lat, double lon, double elevationMeters) {

    /** Basic sanity: reject the obviously corrupt rows in the source table. */
    public boolean isValid() {
        return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }
}
