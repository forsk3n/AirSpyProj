package com.asterix.geo;

/** Polar-to-geographic conversion for CAT048 measured position. */
public final class GeoUtil {

    private static final double EARTH_R = 6_371_000.0; // meters
    private static final double NM_TO_M = 1852.0;
    private static final double FT_TO_M = 0.3048;

    private GeoUtil() {}

    /**
     * Convert a plot's polar position (relative to its radar) into lat/lon.
     *
     * @param radar     reporting sensor
     * @param rhoRaw    slant range in 1/256 NM units (I048/040 first word)
     * @param thetaRaw  azimuth in 360/65536 degree units (I048/040 second word)
     * @param flightLevel  aircraft FL (hundreds of feet); used for slant-range
     *                     correction. Pass null to treat range as ground range.
     * @return {lat, lon} in degrees
     */
    public static double[] toLatLon(Radar radar, int rhoRaw, int thetaRaw, Double flightLevel) {
        double slant = (rhoRaw / 256.0) * NM_TO_M;

        double ground = slant;
        if (flightLevel != null) {
            double aircraftAlt = flightLevel * 100.0 * FT_TO_M;
            double dh = aircraftAlt - radar.elevationMeters();
            double g2 = slant * slant - dh * dh;
            if (g2 > 0) ground = Math.sqrt(g2);
        }

        double bearing = Math.toRadians(thetaRaw * 360.0 / 65536.0);
        double lat1 = Math.toRadians(radar.lat());
        double lon1 = Math.toRadians(radar.lon());
        double d = ground / EARTH_R;

        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(d)
                + Math.cos(lat1) * Math.sin(d) * Math.cos(bearing));
        double lon2 = lon1 + Math.atan2(
                Math.sin(bearing) * Math.sin(d) * Math.cos(lat1),
                Math.cos(d) - Math.sin(lat1) * Math.sin(lat2));

        return new double[]{Math.toDegrees(lat2), Math.toDegrees(lon2)};
    }
}
