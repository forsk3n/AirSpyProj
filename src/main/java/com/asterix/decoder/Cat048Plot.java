package com.asterix.decoder;

/**
 * One decoded CAT048 target report (a single "plot").
 * Position is kept in polar form (rho/theta) as received; the geographic
 * lat/lon is derived later by joining with the radar's location.
 */
public class Cat048Plot {
    public int sac;
    public int sic;
    public String icaoAddress;   // 6 hex chars, or null if I048/220 absent
    public String callsign;      // trimmed, or null if I048/240 absent
    public String mode3A;        // 4 octal digits, or null
    public Double flightLevel;   // FL (hundreds of feet), or null
    public Integer trackNumber;  // I048/161 track number, or null
    public boolean hasPolar;     // true if I048/040 present
    public int rhoRaw;           // 1/256 NM units
    public int thetaRaw;         // 360/65536 degree units
    public double timeOfDay;     // seconds since midnight UTC (I048/140)

    /** How this target was detected, based on which identifying fields are present. */
    public enum SourceType { MODE_S, MODE_AC, PRIMARY }

    public SourceType sourceType() {
        if (icaoAddress != null) return SourceType.MODE_S;
        if (mode3A != null)      return SourceType.MODE_AC;
        return SourceType.PRIMARY;
    }

    /**
     * Stable identity for track aggregation. ICAO is globally unique; without it
     * we fall back to the sensor-scoped track number, then the sensor-scoped
     * Mode 3/A code. Pure primary blips with none of these return null and are
     * not aggregated into an aircraft row.
     */
    public String identityKey() {
        if (icaoAddress != null)  return "S:" + icaoAddress;
        if (trackNumber != null)  return "T:" + sac + "/" + sic + ":" + trackNumber;
        if (mode3A != null)       return "A:" + sac + "/" + sic + ":" + mode3A;
        return null;
    }

    public boolean hasIdentity() {
        return identityKey() != null;
    }
}
