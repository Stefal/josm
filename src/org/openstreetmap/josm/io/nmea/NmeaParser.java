// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io.nmea;

import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.gpx.GpxConstants;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.date.DateUtils;

/**
 * Parses NMEA 0183 data. Based on information from
 * <a href="https://gpsd.gitlab.io/gpsd/NMEA.html">https://gpsd.gitlab.io/gpsd</a>.
 *
 * NMEA data is in printable ASCII form and may include information such as position,
 * speed, depth, frequency allocation, etc.
 * Typical messages might be 11 to a maximum of 79 characters in length.
 *
 * NMEA standard aims to support one-way serial data transmission from a single "talker"
 * to one or more "listeners". The type of talker is identified by a 2-character mnemonic.
 *
 * NMEA information is encoded through a list of "sentences".
 * @since 18787
 */
public class NmeaParser {

    /**
     * Course Over Ground and Ground Speed.
     * <p>
     * The actual course and speed relative to the ground
     */
    enum VTG {
        COURSE(1), COURSE_REF(2), // true course
        COURSE_M(3), COURSE_M_REF(4), // magnetic course
        SPEED_KN(5), SPEED_KN_UNIT(6), // speed in knots
        SPEED_KMH(7), SPEED_KMH_UNIT(8), // speed in km/h
        REST(9); // version-specific rest

        final int position;

        VTG(int position) {
            this.position = position;
        }
    }

    /**
     * Recommended Minimum Specific GNSS Data.
     * <p>
     * Time, date, position, course and speed data provided by a GNSS navigation receiver.
     * This sentence is transmitted at intervals not exceeding 2-seconds.
     * RMC is the recommended minimum data to be provided by a GNSS receiver.
     * All data fields must be provided, null fields used only when data is temporarily unavailable.
     */
    enum RMC {
        TIME(1),
        /** Warning from the receiver (A = data ok, V = warning) */
        RECEIVER_WARNING(2),
        WIDTH_NORTH(3), WIDTH_NORTH_NAME(4), // Latitude, NS
        LENGTH_EAST(5), LENGTH_EAST_NAME(6), // Longitude, EW
        SPEED(7), COURSE(8), DATE(9),           // Speed in knots
        MAGNETIC_DECLINATION(10), UNKNOWN(11),  // magnetic declination
        /**
         * Mode (A = autonom; D = differential; E = estimated; N = not valid; S = simulated)
         *
         * @since NMEA 2.3
         */
        MODE(12);

        final int position;

        RMC(int position) {
            this.position = position;
        }
    }

    /**
     * Global Positioning System Fix Data.
     * <p>
     * Time, position and fix related data for a GPS receiver.
     */
    enum GGA {
        TIME(1), LATITUDE(2), LATITUDE_NAME(3), LONGITUDE(4), LONGITUDE_NAME(5),
        /**
         * Quality (0 = invalid, 1 = GPS, 2 = DGPS, 6 = estimanted (@since NMEA 2.3))
         */
        QUALITY(6), SATELLITE_COUNT(7),
        HDOP(8), // HDOP (horizontal dilution of precision)
        HEIGHT(9), HEIGHT_UNTIS(10), // height above NN (above geoid)
        HEIGHT_2(11), HEIGHT_2_UNTIS(12), // height geoid - height ellipsoid (WGS84)
        GPS_AGE(13), // Age of differential GPS data
        REF(14); // REF station

        final int position;
        GGA(int position) {
            this.position = position;
        }
    }

    /**
     * GNSS DOP and Active Satellites.
     * <p>
     * GNSS receiver operating mode, satellites used in the navigation solution reported by the GGA or GNS sentence,
     * and DOP values.
     * If only GPS, GLONASS, etc. is used for the reported position solution the talker ID is GP, GL, etc.
     * and the DOP values pertain to the individual system. If GPS, GLONASS, etc. are combined to obtain the
     * reported position solution multiple GSA sentences are produced, one with the GPS satellites, another with
     * the GLONASS satellites, etc. Each of these GSA sentences shall have talker ID GN, to indicate that the
     * satellites are used in a combined solution and each shall have the PDOP, HDOP and VDOP for the
     * combined satellites used in the position.
     */
    enum GSA {
        AUTOMATIC(1),
        FIX_TYPE(2), // 1 = not fixed, 2 = 2D fixed, 3 = 3D fixed)
        // PRN numbers for max 12 satellites
        PRN_1(3), PRN_2(4), PRN_3(5), PRN_4(6), PRN_5(7), PRN_6(8),
        PRN_7(9), PRN_8(10), PRN_9(11), PRN_10(12), PRN_11(13), PRN_12(14),
        PDOP(15),   // PDOP (precision)
        HDOP(16),   // HDOP (horizontal precision)
        VDOP(17);   // VDOP (vertical precision)

        final int position;
        GSA(int position) {
            this.position = position;
        }
    }

    /**
     * GST - GNSS Pseudorange Noise Statistics
     * <p>
     * RMS and Standard deviation estimated values.
     */
    enum GST {
        TIME(1),
        RMS_DEVIATION(2),        // Total RMS standard deviation of ranges inputs to the navigation solution
        STDDEV_MAJOR(3),         // Standard deviation (meters) of semi-major axis of error ellipse
        STDDEV_MINOR(4),         // Standard deviation (meters) of semi-minor axis of error ellipse
        STDDEV_MAJOR_BEARING(5), // Orientation of semi-major axis of error ellipse (true north degrees)
        STDDEV_LAT(6),           // Standard deviation (meters) of latitude error
        STDDEV_LONG(7),          // Standard deviation (meters) of longitude error
        STDDEV_HEIGHT(8);        // Standard deviation (meters) of altitude error
        final int position;
        GST(int position) {
            this.position = position;
        }
    }

    /**
     * Geographic Position - Latitude/Longitude.
     * <p>
     * Latitude and Longitude of vessel position, time of position fix and status.
     */
    enum GLL {
        LATITUDE(1), LATITUDE_NS(2), // Latitude, NS
        LONGITUDE(3), LONGITUDE_EW(4), // Latitude, EW
        UTC(5), // Universal Time Coordinated
        STATUS(6), // Status: A = Data valid, V = Data not valid
        /**
         * Mode (A = autonom; D = differential; E = estimated; N = not valid; S = simulated)
         * @since NMEA 2.3
         */
        MODE(7);

        final int position;
        GLL(int position) {
            this.position = position;
        }
    }

    private static final Pattern DATE_TIME_PATTERN = Pattern.compile("(\\d{12})(\\.\\d+)?");

    private final SimpleDateFormat rmcTimeFmt = new SimpleDateFormat("ddMMyyHHmmss.SSS", Locale.ENGLISH);

    private Instant readTime(String p) throws IllegalDataException {
        // NMEA defines time with "a variable number of digits for decimal-fraction of seconds"
        // This variable decimal fraction cannot be parsed by SimpleDateFormat
        Matcher m = DATE_TIME_PATTERN.matcher(p);
        if (m.matches()) {
            String date = m.group(1);
            double milliseconds = 0d;
            if (m.groupCount() > 1 && m.group(2) != null) {
                milliseconds = 1000d * Double.parseDouble("0" + m.group(2));
            }
            // Add milliseconds on three digits to match SimpleDateFormat pattern
            date += String.format(".%03d", (int) milliseconds);
            Date d = rmcTimeFmt.parse(date, new ParsePosition(0));
            if (d != null)
                return d.toInstant();
        }
        throw new IllegalDataException("Date is malformed: '" + p + "'");
    }

    protected Collection<WayPoint> waypoints = new ArrayList<>();
    protected String pTime;
    protected String pDate;
    /* Waypoint currently in work */
    protected WayPoint pWp;
    /* number of successfully parsed sentences */
    protected int success;
    protected int malformed;
    protected int checksumErrors;
    protected int noChecksum;
    protected int unknown;
    protected int zeroCoord;

    /**
     * Number of unknown sentences
     * @return the number of unknown sentences encountered
     */
    public int getParserUnknown() {
        return unknown;
    }

    /**
     * Number of empty coordinates
     * @return the number of coordinates which have been zero
     */
    public int getParserZeroCoordinates() {
        return zeroCoord;
    }

    /**
     * Number of checksum errors
     * @return the number of sentences with checksum errors
     */
    public int getParserChecksumErrors() {
        return checksumErrors+noChecksum;
    }

    /**
     * Number of malformed errors
     * @return the number of malformed sentences
     */
    public int getParserMalformed() {
        return malformed;
    }

    /**
     * Number of successful coordinates
     * @return the number of successfully read coordinates
     */
    public int getSuccess() {
        return success;
    }

    /**
     * List of collected coordinates
     * When parsing a stream the last entry may be still incomplete
     * @return the collection of points collected
     */
    public Collection<WayPoint> getWaypoints() {
        return Collections.unmodifiableCollection(waypoints);
    }

    /**
     * Return list of collected coordinates and drop old data
     * When parsing a stream the last entry may be still incomplete and usually
     * will not be dropped
     * @return the collection of points collected
     */
    public Collection<WayPoint> getAndDropWaypoints() {
        Collection<WayPoint> r = getWaypoints();
        dropOldWaypoints();
        return r;
    }

    /**
     * Get rid of older data no longer needed, drops everything except current
     * coordinate
     */
    public void dropOldWaypoints() {
        waypoints.clear();
        if (pWp != null)
            waypoints.add(pWp);
    }

    /**
     * Constructs a new {@code NmeaParser}
     */
    public NmeaParser() {
        rmcTimeFmt.setTimeZone(DateUtils.UTC);
        pDate = "010100"; // TODO date problem
    }

    /**
     * Determines if the given address denotes the given NMEA sentence formatter of a known talker.
     * @param address first tag of an NMEA sentence
     * @param formatter sentence formatter mnemonic code
     * @return {@code true} if the {@code address} denotes the given NMEA sentence formatter of a known talker
     */
    static boolean isSentence(String address, Sentence formatter) {
        return Arrays.stream(TalkerId.values())
                .anyMatch(talker -> address.equals('$' + talker.name() + formatter.name()));
    }

    /**
     * Parses split up sentences into WayPoints which are stored
     * in the collection in the NMEAParserState object.
     * @param s data to parse
     * @return {@code true} if the input made sense,  {@code false} otherwise.
     */
    public boolean parseNMEASentence(String s) throws IllegalDataException {
        try {
            if (s.isEmpty()) {
                throw new IllegalArgumentException("s is empty");
            }

            // checksum check:
            // the bytes between the $ and the * are xored
            // if there is no * or other meanities it will throw
            // and result in a malformed packet.
            String[] chkstrings = s.split("\\*", -1);
            if (chkstrings.length > 1) {
                byte[] chb = chkstrings[0].getBytes(StandardCharsets.UTF_8);
                int chk = 0;
                for (int i = 1; i < chb.length; i++) {
                    chk ^= chb[i];
                }
                if (Integer.parseInt(chkstrings[1].substring(0, 2), 16) != chk) {
                    checksumErrors++;
                    pWp = null;
                    return false;
                }
            } else {
                // Since there is no checksum, we remove any line endings
                chkstrings[0] = chkstrings[0].replaceAll("\\r|\\n", "");
                noChecksum++;
            }
            // now for the content
            String[] e = chkstrings[0].split(",", -1);
            String accu;

            WayPoint currentwp = pWp;
            String currentDate = pDate;

            // handle the packet content
            if (isSentence(e[0], Sentence.GGA)) {
                // Position
                LatLon latLon = parseLatLon(
                        e[GGA.LATITUDE_NAME.position],
                        e[GGA.LONGITUDE_NAME.position],
                        e[GGA.LATITUDE.position],
                        e[GGA.LONGITUDE.position]
                );
                if (latLon == null) {
                    throw new IllegalDataException("Malformed lat/lon");
                }

                if (LatLon.ZERO.equals(latLon)) {
                    zeroCoord++;
                    return false;
                }

                // time
                accu = e[GGA.TIME.position];
                Instant instant = readTime(currentDate+accu);

                if ((pTime == null) || (currentwp == null) || !pTime.equals(accu)) {
                    // this node is newer than the previous, create a new waypoint.
                    // no matter if previous WayPoint was null, we got something better now.
                    pTime = accu;
                    currentwp = new WayPoint(latLon);
                }
                if (!currentwp.attr.containsKey("time")) {
                    // As this sentence has no complete time only use it
                    // if there is no time so far
                    currentwp.setInstant(instant);
                }
                // elevation
                accu = e[GGA.HEIGHT_UNTIS.position];
                if ("M".equals(accu)) {
                    // Ignore heights that are not in meters for now
                    accu = e[GGA.HEIGHT.position];
                    if (!accu.isEmpty()) {
                        Double.parseDouble(accu);
                        // if it throws it's malformed; this should only happen if the
                        // device sends nonstandard data.
                        if (!accu.isEmpty()) { // FIX ? same check
                            currentwp.put(GpxConstants.PT_ELE, accu);
                        }
                    }
                }
                // number of satellites
                accu = e[GGA.SATELLITE_COUNT.position];
                int sat = 0;
                if (!accu.isEmpty()) {
                    sat = Integer.parseInt(accu);
                    currentwp.put(GpxConstants.PT_SAT, accu);
                }
                // h-dilution
                accu = e[GGA.HDOP.position];
                if (!accu.isEmpty()) {
                    currentwp.put(GpxConstants.PT_HDOP, Float.valueOf(accu));
                }
                // fix
                accu = e[GGA.QUALITY.position];
                if (!accu.isEmpty()) {
                    int fixtype = Integer.parseInt(accu);
                    switch (fixtype) {
                    case 0:
                        currentwp.put(GpxConstants.PT_FIX, "none");
                        break;
                    case 1:
                        if (sat < 4) {
                            currentwp.put(GpxConstants.PT_FIX, "2d");
                        } else {
                            currentwp.put(GpxConstants.PT_FIX, "3d");
                        }
                        break;
                    case 2:
                        currentwp.put(GpxConstants.PT_FIX, "dgps");
                        break;
                    case 3:
                        currentwp.put(GpxConstants.PT_FIX, "pps");
                        break;
                    case 4:
                        currentwp.put(GpxConstants.PT_FIX, "rtk");
                        break;
                    case 5:
                        currentwp.put(GpxConstants.PT_FIX, "float rtk");
                        break;
                    case 6:
                        currentwp.put(GpxConstants.PT_FIX, "estimated");
                        break;
                    case 7:
                        currentwp.put(GpxConstants.PT_FIX, "manual");
                        break;
                    case 8:
                        currentwp.put(GpxConstants.PT_FIX, "simulated");
                        break;
                    default:
                        break;
                    }
                }
                // Age of differential correction
                if (GGA.GPS_AGE.position < e.length) {
                    accu = e[GGA.GPS_AGE.position];
                    if (!accu.isEmpty() && currentwp != null) {
                        currentwp.put(GpxConstants.PT_AGEOFDGPSDATA, Float.valueOf(accu));
                    }
                }
                // reference ID
                if (GGA.REF.position < e.length) {
                    accu = e[GGA.REF.position];
                    if (!accu.isEmpty()) {
                        currentwp.put(GpxConstants.PT_DGPSID, accu);
                    }
                }
            } else if (isSentence(e[0], Sentence.VTG)) {
                // COURSE
                accu = e[VTG.COURSE_REF.position];
                if ("T".equals(accu)) {
                    // other values than (T)rue are ignored
                    accu = e[VTG.COURSE.position];
                    if (!accu.isEmpty() && currentwp != null) {
                        Double.parseDouble(accu);
                        currentwp.put(GpxConstants.PT_COURSE, accu);
                    }
                }
                // SPEED
                accu = e[VTG.SPEED_KMH_UNIT.position];
                if (accu.startsWith("K")) {
                    accu = e[VTG.SPEED_KMH.position];
                    if (!accu.isEmpty() && currentwp != null) {
                        double speed = Double.parseDouble(accu);
                        currentwp.put("speed", Double.toString(speed)); // speed in km/h
                    }
                }
            } else if (isSentence(e[0], Sentence.GSA)) {
                // vdop
                accu = e[GSA.VDOP.position];
                if (!accu.isEmpty() && currentwp != null) {
                    currentwp.put(GpxConstants.PT_VDOP, Float.valueOf(accu));
                }
                // hdop
                accu = e[GSA.HDOP.position];
                if (!accu.isEmpty() && currentwp != null) {
                    currentwp.put(GpxConstants.PT_HDOP, Float.valueOf(accu));
                }
                // pdop
                accu = e[GSA.PDOP.position];
                if (!accu.isEmpty() && currentwp != null) {
                    currentwp.put(GpxConstants.PT_PDOP, Float.valueOf(accu));
                }
                // GST Sentence
            } else if (isSentence(e[0], Sentence.GST)) {
                // std horizontal deviation
                accu = e[GST.STDDEV_MAJOR.position];
                if (!accu.isEmpty() && currentwp != null) {
                    currentwp.put(GpxConstants.PT_STD_HDEV, Float.valueOf(accu));
                }
                // std vertical deviation
                accu = e[GST.STDDEV_HEIGHT.position];
                if (!accu.isEmpty() && currentwp != null) {
                    currentwp.put(GpxConstants.PT_STD_VDEV, Float.valueOf(accu));
                }
            } else if (isSentence(e[0], Sentence.RMC)) {
                // coordinates
                LatLon latLon = parseLatLon(
                        e[RMC.WIDTH_NORTH_NAME.position],
                        e[RMC.LENGTH_EAST_NAME.position],
                        e[RMC.WIDTH_NORTH.position],
                        e[RMC.LENGTH_EAST.position]
                );
                if (LatLon.ZERO.equals(latLon)) {
                    zeroCoord++;
                    return false;
                }
                // time
                currentDate = e[RMC.DATE.position];
                String time = e[RMC.TIME.position];

                Instant instant = readTime(currentDate+time);

                if (pTime == null || currentwp == null || !pTime.equals(time)) {
                    // this node is newer than the previous, create a new waypoint.
                    pTime = time;
                    currentwp = new WayPoint(latLon);
                }
                // time: this sentence has complete time so always use it.
                currentwp.setInstant(instant);
                // speed
                accu = e[RMC.SPEED.position];
                if (!accu.isEmpty() && !currentwp.attr.containsKey("speed")) {
                    double speed = Double.parseDouble(accu);
                    speed *= 0.514444444 * 3.6; // to km/h
                    currentwp.put("speed", Double.toString(speed));
                }
                // course
                accu = e[RMC.COURSE.position];
                if (!accu.isEmpty() && !currentwp.attr.containsKey("course")) {
                    Double.parseDouble(accu);
                    currentwp.put(GpxConstants.PT_COURSE, accu);
                }

                // TODO fix?
                // * Mode (A = autonom; D = differential; E = estimated; N = not valid; S = simulated)
                // *
                // * @since NMEA 2.3
                //
                //MODE(12);
            } else if (isSentence(e[0], Sentence.GLL)) {
                // coordinates
                LatLon latLon = parseLatLon(
                        e[GLL.LATITUDE_NS.position],
                        e[GLL.LONGITUDE_EW.position],
                        e[GLL.LATITUDE.position],
                        e[GLL.LONGITUDE.position]
                );
                if (LatLon.ZERO.equals(latLon)) {
                    zeroCoord++;
                    return false;
                }
                // only consider valid data
                if (!"A".equals(e[GLL.STATUS.position])) {
                    return false;
                }

                // RMC sentences contain a full date while GLL sentences contain only time,
                // so create new waypoints only of the NMEA file does not contain RMC sentences
                if (pTime == null || currentwp == null) {
                    currentwp = new WayPoint(latLon);
                }
            } else {
                unknown++;
                return false;
            }
            pDate = currentDate;
            if (pWp != currentwp) {
                if (pWp != null) {
                    pWp.getInstant();
                }
                pWp = currentwp;
                waypoints.add(currentwp);
                success++;
                return true;
            }
            return true;

        } catch (IllegalArgumentException | IndexOutOfBoundsException | IllegalDataException ex) {
            if (malformed < 5) {
                Logging.warn(ex);
            } else {
                Logging.debug(ex);
            }
            malformed++;
            pWp = null;
            return false;
        }
    }

    private static LatLon parseLatLon(String ns, String ew, String dlat, String dlon) {
        String widthNorth = dlat.trim();
        String lengthEast = dlon.trim();

        // return a zero latlon instead of null so it is logged as zero coordinate
        // instead of malformed sentence
        if (widthNorth.isEmpty() && lengthEast.isEmpty()) return LatLon.ZERO;

        // The format is xxDDLL.LLLL
        // xx optional whitespace
        // DD (int) degres
        // LL.LLLL (double) latidude
        int latdegsep = widthNorth.indexOf('.') - 2;
        if (latdegsep < 0) return null;

        int latdeg = Integer.parseInt(widthNorth.substring(0, latdegsep));
        double latmin = Double.parseDouble(widthNorth.substring(latdegsep));
        if (latdeg < 0) {
            latmin *= -1.0;
        }
        double lat = latdeg + latmin / 60;
        if ("S".equals(ns)) {
            lat = -lat;
        }

        int londegsep = lengthEast.indexOf('.') - 2;
        if (londegsep < 0) return null;

        int londeg = Integer.parseInt(lengthEast.substring(0, londegsep));
        double lonmin = Double.parseDouble(lengthEast.substring(londegsep));
        if (londeg < 0) {
            lonmin *= -1.0;
        }
        double lon = londeg + lonmin / 60;
        if ("W".equals(ew)) {
            lon = -lon;
        }
        return new LatLon(lat, lon);
    }
}
