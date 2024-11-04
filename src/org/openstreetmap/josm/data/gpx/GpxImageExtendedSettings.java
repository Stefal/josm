// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.gpx;

/**
 * Image extended exif metadata settings used by {@link GpxImageCorrelationSettings}.
 * @since xxx
 */
public class GpxImageExtendedSettings {

    private final boolean setImageGpsDatum;
    private final String imageGpsDatum;
    
    /**
     * Construcs a new {@code GpxImageExtendedSettings}.
     * @param setImageGpsDatum determines if images Gps datum must be set
     * @param imageGpsDatum determines the gps coordinates datum value to be set
     */
    public GpxImageExtendedSettings(
        boolean setImageGpsDatum, String imageGpsDatum) {
            this.setImageGpsDatum = setImageGpsDatum;
            this.imageGpsDatum = imageGpsDatum;
        }
    
    /**
     * Determines if image gps datum must be set
     * @return
     */
    public boolean isSetImageGpsDatum() {
        return setImageGpsDatum;
    }
    /**
     * Return the gps coordinates datum code.
     * @return the datum code
     */
    public String getImageGpsDatum() {
        return imageGpsDatum;
    }
}
