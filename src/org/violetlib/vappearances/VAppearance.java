/*
 * Copyright (c) 2018 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vappearances;

import java.awt.*;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

/**
 * An object that represents a particular system appearance along with the system color values at a particular time.
 * A {@code VAppearance} object is immutable.
 */

public interface VAppearance {

    /**
     * Return the system name of the appearance. Currently, four names are possible in macOS:
     * <ul>
     * <li>
     * {@code NSAppearanceNameAqua} – the original, light Aqua appearance, standard in macOS 10.10–10.13.
     * </li>
     * <li>
     * {@code NSAppearanceNameDarkAqua} – the dark system appearance introduced in macOS 10.14.
     * </li>
     * <li>
     * {@code NSAppearanceNameVibrantLight} – a light vibrant appearance, used in specific situations.
     * </li>
     * <li>
     * {@code NSAppearanceNameVibrantdark} – a dark vibrant appearance, used in specific situations.
     * </li>
     * </ul>
     * </ul>
     * <p>
     * Note that there can be many {@code VAppearance} objects with the same name, but at any one time, there is one
     * that represents the current state of the system as it pertains to appearance and system colors.
     * The relevant system state includes the selected system appearance, the accent
     * color, the highlight color, and the increased contrast accessibility option.
     *
     * @return the appearance name.
     */

    @NotNull String getName();

    /**
     * Identify a dark system appearance.
     * @return true if and only if this appearance is a dark appearance.
     */

    boolean isDark();

    /**
     * Identify a high contrast system appearance. A high contrast system appearance indicates that the user has
     * enabled the accessibility option to increase contrast in the UI.
     * @return true if and only if this appearance is a high contrast appearance.
     */

    boolean isHighContrast();

    /**
     * Return a non-modifiable map that provides the values of known system colors.
     */

    @NotNull Map<String,Color> getColors();
}
