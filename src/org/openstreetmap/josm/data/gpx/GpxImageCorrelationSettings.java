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
    private final GpxImageDirectionPositionSettings directionPositionSettings;
    private final GpxImageExtendedSettings extendedSettings;

    /**
     * Constructs a new {@code GpxImageCorrelationSettings}.
     * @param offset offset in milliseconds
     * @param forceTags force tagging of all photos, otherwise prefs are used
     */
    public GpxImageCorrelationSettings(long offset, boolean forceTags) {
        this(offset, forceTags,
        new GpxImageDirectionPositionSettings(false, 0, false, 0, 0, 0),
        new GpxImageExtendedSettings(false, null)
        );
    }

    /**
     * Constructs a new {@code GpxImageCorrelationSettings}.
     * @param offset offset in milliseconds
     * @param forceTags force tagging of all photos, otherwise prefs are used
     * @param directionPositionSettings direction/position settings
     */
    public GpxImageCorrelationSettings(long offset, boolean forceTags,
            GpxImageDirectionPositionSettings directionPositionSettings) {
        this(offset, forceTags, directionPositionSettings,
        new GpxImageExtendedSettings(false, null));
    }

    /**
     * Constructs a new {@code GpxImageCorrelationSettings}.
     * @param offset offset in milliseconds
     * @param forceTags force tagging of all photos, otherwise prefs are used
     * @param directionPositionSettings direction/position settings
     * @param extendedSettings blablabla
     */
    public GpxImageCorrelationSettings(long offset, boolean forceTags,
            GpxImageDirectionPositionSettings directionPositionSettings,
            GpxImageExtendedSettings extendedSettings) {
        this.offset = offset;
        this.forceTags = forceTags;
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
                + ", directionPositionSettings=" + directionPositionSettings
                + ", extendedSettings=" + extendedSettings + ']';
    }
}
