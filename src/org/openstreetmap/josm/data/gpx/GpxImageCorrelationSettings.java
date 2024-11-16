// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.gpx;

import java.util.Objects;

/**
 * Correlation settings used by {@link GpxImageCorrelation}.
 * @since 18061
 */
public class GpxImageCorrelationSettings {

    private final long offset;
    private final boolean forceTags;
    private final String imgTimeSource;
    private final GpxImageDirectionPositionSettings directionPositionSettings;
    private final GpxImageExtendedSettings extendedSettings;

    /**
     * Constructs a new {@code GpxImageCorrelationSettings}.
     * @param offset offset in milliseconds
     * @param forceTags force tagging of all photos, otherwise prefs are used
     */
    public GpxImageCorrelationSettings(long offset, boolean forceTags) {
        this(offset, forceTags, "exifCamTime",
        new GpxImageDirectionPositionSettings(false, 0, false, 0, 0, 0),
        new GpxImageExtendedSettings(false, null)
        );
    }

    /**
     * Constructs a new {@code GpxImageCorrelationSettings}.
     * @param offset offset in milliseconds
     * @param forceTags force tagging of all photos, otherwise prefs are used
     * @param imgTimeSource select image clock source: 
     * "exifCamTime" for camera internal clock
     * "exifGpsTime for the gps clock of the camera
     */
    public GpxImageCorrelationSettings(long offset, boolean forceTags, String imgTimeSource) {
        this(offset, forceTags, imgTimeSource,
        new GpxImageDirectionPositionSettings(false, 0, false, 0, 0, 0),
        new GpxImageExtendedSettings(false, null)
        );
    }

    /**
     * Constructs a new {@code GpxImageCorrelationSettings}.
     * @param offset offset in milliseconds
     * @param forceTags force tagging of all photos, otherwise prefs are used
     * @param imgTimeSource select image clock source: 
     * "exifCamTime" for camera internal clock
     * "exifGpsTime for the gps clock of the camera
     * @param directionPositionSettings direction/position settings
     */
    public GpxImageCorrelationSettings(long offset, boolean forceTags, String imgTimeSource,
            GpxImageDirectionPositionSettings directionPositionSettings) {
        this(offset, forceTags, imgTimeSource, directionPositionSettings,
        new GpxImageExtendedSettings(false, null));
    }

    /**
     * Constructs a new {@code GpxImageCorrelationSettings}.
     * @param offset offset in milliseconds
     * @param forceTags force tagging of all photos, otherwise prefs are used
     * @param imgTimeSource select image clock source: 
     * "exifCamTime" for camera internal clock
     * "exifGpsTime for the gps clock of the camera
     * @param directionPositionSettings direction/position settings
     * @param extendedSettings blablabla
     */
    public GpxImageCorrelationSettings(long offset, boolean forceTags, String imgTimeSource,
            GpxImageDirectionPositionSettings directionPositionSettings,
            GpxImageExtendedSettings extendedSettings) {
        this.offset = offset;
        this.forceTags = forceTags;
        this.imgTimeSource = imgTimeSource;
        this.directionPositionSettings = Objects.requireNonNull(directionPositionSettings);
        this.extendedSettings = Objects.requireNonNull(extendedSettings);
    }
    /**
     * Returns the offset in milliseconds.
     * @return the offset in milliseconds
     */
    public long getOffset() {
        return offset;
    }

    /**
     * Determines if tagging of all photos must be forced, otherwise prefs are used
     * @return {@code true} if tagging of all photos must be forced, otherwise prefs are used
     */
    public boolean isForceTags() {
        return forceTags;
    }

    /**
     * Return the selected image clock source
     * @return the clock source
     * @since xxx
     */
    public String getImgTimeSource() {
        return imgTimeSource;
    }

    /**
     * Returns the direction/position settings.
     * @return the direction/position settings
     */
    public GpxImageDirectionPositionSettings getDirectionPositionSettings() {
        return directionPositionSettings;
    }

    /**
     * Returns the extended exif metadata settings.
     * @return the extended exif metadata settings
     */
    public GpxImageExtendedSettings getExtendedSettings() {
        return extendedSettings;
    }

    @Override
    public String toString() {
        return "[offset=" + offset + ", forceTags=" + forceTags
                + ", clock source=" + imgTimeSource
                + ", directionPositionSettings=" + directionPositionSettings
                + ", extendedSettings=" + extendedSettings + ']';
    }
}
