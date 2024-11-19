// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.gpx;

/**
 * Time sources available in the image Exif metadata.
 * @since xxx
 */
public enum TimeSource {
    /**Time from the camera internal clock (RTC)*/
    EXIFCAMTIME,
    /**Time from the camera gps clock*/
    EXIFGPSTIME
}
