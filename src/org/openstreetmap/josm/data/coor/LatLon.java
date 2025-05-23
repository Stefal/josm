// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.coor;

import java.awt.geom.Area;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.tools.Utils;

/**
 * LatLon are unprojected latitude / longitude coordinates.
 * <br>
 * <b>Latitude</b> specifies the north-south position in degrees
 * where valid values are in the [-90,90] and positive values specify positions north of the equator.
 * <br>
 * <b>Longitude</b> specifies the east-west position in degrees
 * where valid values are in the [-180,180] and positive values specify positions east of the prime meridian.
 * <br>
 * <img alt="lat/lon" src="https://upload.wikimedia.org/wikipedia/commons/6/62/Latitude_and_Longitude_of_the_Earth.svg">
 * <br>
 * This class is immutable.
 *
 * @author Imi
 */
public class LatLon extends Coordinate implements ILatLon {

    private static final long serialVersionUID = 1L;

    /**
     * Minimum difference in location to not be represented as the same position.
     * The API returns 7 decimals.
     */
    public static final double MAX_SERVER_PRECISION = ILatLon.MAX_SERVER_PRECISION;
    /**
     * The inverse of the server precision
     * @see #MAX_SERVER_PRECISION
     */
    public static final double MAX_SERVER_INV_PRECISION = 1e7;

    /**
     * The (0,0) coordinates.
     * @since 6178
     */
    public static final LatLon ZERO = new LatLon(0, 0);

    /** North pole. */
    public static final LatLon NORTH_POLE = new LatLon(90, 0);
    /** South pole. */
    public static final LatLon SOUTH_POLE = new LatLon(-90, 0);

    /**
     * The normal number format for server precision coordinates
     */
    public static final DecimalFormat cDdFormatter;
    /**
     * The number format used for high precision coordinates
     */
    public static final DecimalFormat cDdHighPrecisionFormatter;
    static {
        // Don't use the localized decimal separator. This way we can present
        // a comma separated list of coordinates.
        cDdFormatter = (DecimalFormat) NumberFormat.getInstance(Locale.UK);
        cDdFormatter.applyPattern("###0.0######");
        cDdHighPrecisionFormatter = (DecimalFormat) NumberFormat.getInstance(Locale.UK);
        cDdHighPrecisionFormatter.applyPattern("###0.0##########");
    }

    /**
     * Replies true if lat is in the range [-90,90]
     *
     * @param lat the latitude
     * @return true if lat is in the range [-90,90]
     */
    public static boolean isValidLat(double lat) {
        return lat >= -90d && lat <= 90d;
    }

    /**
     * Replies true if lon is in the range [-180,180]
     *
     * @param lon the longitude
     * @return true if lon is in the range [-180,180]
     */
    public static boolean isValidLon(double lon) {
        return lon >= -180d && lon <= 180d;
    }

    /**
     * Make sure longitude value is within <code>[-180, 180]</code> range.
     * @param lon the longitude in degrees
     * @return lon plus/minus multiples of <code>360</code>, as needed to get
     * in <code>[-180, 180]</code> range
     */
    public static double normalizeLon(double lon) {
        if (lon >= -180 && lon <= 180)
            return lon;
        else {
            lon = lon % 360.0;
            if (lon > 180) {
                return lon - 360;
            } else if (lon < -180) {
                return lon + 360;
            }
            return lon;
        }
    }

    /**
     * Replies true if lat is in the range [-90,90] and lon is in the range [-180,180]
     *
     * @return true if lat is in the range [-90,90] and lon is in the range [-180,180]
     */
    public boolean isValid() {
        return isValidLat(lat()) && isValidLon(lon());
    }

    /**
     * Clamp the lat value to be inside the world.
     * @param value The value
     * @return The value clamped to the world.
     */
    public static double toIntervalLat(double value) {
        return Utils.clamp(value, -90, 90);
    }

    /**
     * Returns a valid OSM longitude [-180,+180] for the given extended longitude value.
     * For example, a value of -181 will return +179, a value of +181 will return -179.
     * @param value A longitude value not restricted to the [-180,+180] range.
     * @return a valid OSM longitude [-180,+180]
     */
    public static double toIntervalLon(double value) {
        if (isValidLon(value))
            return value;
        else {
            int n = (int) (value + Math.signum(value)*180.0) / 360;
            return value - n*360.0;
        }
    }

    /**
     * Constructs a new object representing the given latitude/longitude.
     * @param lat the latitude, i.e., the north-south position in degrees
     * @param lon the longitude, i.e., the east-west position in degrees
     */
    public LatLon(double lat, double lon) {
        super(lon, lat);
    }

    /**
     * Creates a new LatLon object for the given coordinate
     * @param coor The coordinates to copy from.
     */
    public LatLon(ILatLon coor) {
        super(coor.lon(), coor.lat());
    }

    @Override
    public double lat() {
        return y;
    }

    @Override
    public double lon() {
        return x;
    }

    /**
     * Determines if this lat/lon is within the given bounding box.
     * @param b bounding box
     * @return <code>true</code> if this is within the given bounding box.
     */
    public boolean isWithin(Bounds b) {
        return b.contains(this);
    }

    /**
     * Check if this is contained in given area or area is null.
     *
     * @param a Area
     * @return <code>true</code> if this is contained in given area or area is null.
     */
    public boolean isIn(Area a) {
        return a == null || a.contains(x, y);
    }

    /**
     * Returns this lat/lon pair in human-readable format.
     *
     * @return String in the format "lat=1.23456 deg, lon=2.34567 deg"
     */
    public String toDisplayString() {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(5);
        return "lat=" + nf.format(lat()) + "\u00B0, lon=" + nf.format(lon()) + '\u00B0';
    }

    /**
     * Interpolate between this and a other latlon. If you don't care about the return type, use {@link ILatLon#interpolate(ILatLon, double)}
     * instead.
     * @param ll2 The other lat/lon object
     * @param proportion The proportion to interpolate
     * @return a new latlon at this position if proportion is 0, at the other position it proportion is 1 and linearly interpolated otherwise.
     */
    public LatLon interpolate(LatLon ll2, double proportion) {
        ILatLon interpolated = ILatLon.super.interpolate(ll2, proportion);
        if (interpolated instanceof LatLon) {
            return (LatLon) interpolated;
        }
        return new LatLon(interpolated);
    }

    /**
     * Get the center between two lat/lon points
     * @param ll2 The other {@link LatLon}
     * @return The center at the average coordinates of the two points. Does not take the 180° meridian into account.
     */
    public LatLon getCenter(LatLon ll2) {
        // The JIT will inline this for us, it is as fast as the normal /2 approach
        return interpolate(ll2, .5);
    }

    /**
     * Returns the euclidean distance from this {@code LatLon} to a specified {@code LatLon}.
     *
     * @param ll the specified coordinate to be measured against this {@code LatLon}
     * @return the euclidean distance from this {@code LatLon} to a specified {@code LatLon}
     * @since 6166
     */
    public double distance(final LatLon ll) {
        return super.distance(ll);
    }

    /**
     * Returns the square of the euclidean distance from this {@code LatLon} to a specified {@code LatLon}.
     *
     * @param ll the specified coordinate to be measured against this {@code LatLon}
     * @return the square of the euclidean distance from this {@code LatLon} to a specified {@code LatLon}
     * @since 6166
     */
    public double distanceSq(final LatLon ll) {
        return super.distanceSq(ll);
    }

    @Override
    public String toString() {
        return "LatLon[lat="+lat()+",lon="+lon()+']';
    }

    /**
     * Returns the value rounded to OSM precisions, i.e. to {@link #MAX_SERVER_PRECISION}.
     * @param value lat/lon value
     *
     * @return rounded value
     */
    public static double roundToOsmPrecision(double value) {
        return Math.round(value * MAX_SERVER_INV_PRECISION) / MAX_SERVER_INV_PRECISION;
    }

    /**
     * Replies a clone of this lat LatLon, rounded to OSM precisions, i.e. to {@link #MAX_SERVER_PRECISION}
     *
     * @return a clone of this lat LatLon
     */
    public LatLon getRoundedToOsmPrecision() {
        return new LatLon(
                roundToOsmPrecision(lat()),
                roundToOsmPrecision(lon())
                );
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        LatLon that = (LatLon) obj;
        return Double.compare(that.x, x) == 0 &&
               Double.compare(that.y, y) == 0;
    }
}
