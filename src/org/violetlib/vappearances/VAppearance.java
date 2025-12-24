/*
 * Copyright (c) 2018-2025 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vappearances;

import java.awt.Color;
import java.util.Map;

import org.jetbrains.annotations.*;

/**
  An object that represents a particular system appearance and provides access to its current system color values and
  the appearance settings those colors are based on.
*/

public interface VAppearance
{
    /**
      Return the system name of the appearance. Currently, four names are possible in macOS:
      <ul>
      <li>
      {@code NSAppearanceNameAqua} – the original, light Aqua appearance, standard in macOS 10.10–10.13.
      </li>
      <li>
      {@code NSAppearanceNameDarkAqua} – the dark system appearance introduced in macOS 10.14.
      </li>
      <li>
      {@code NSAppearanceNameVibrantLight} – a light vibrant appearance, used in specific situations.
      </li>
      <li>
      {@code NSAppearanceNameVibrantDark} – a dark vibrant appearance, used in specific situations.
      </li>
      </ul>
      </ul>

      @return the appearance name.
    */

    @NotNull String getName();

    /**
      Identify a dark system appearance.

      @return true if and only if this appearance is a dark appearance.
    */

    boolean isDark();

    /**
      Identify a high contrast system appearance. A high contrast system appearance indicates that the user has enabled
      the accessibility option to increase contrast in the UI.

      @return true if and only if this appearance is a high contrast appearance.
    */

    boolean isHighContrast();

    /**
      Identify a tinted system appearance. This option was introduced in macOS 26. As of macOS 26.2, the tinted
      option and the high contrast option are mutually exclusive.

      @return true if and only if the tinted option exists and is selected.
    */

    boolean isTinted();

    /**
      Return the current appearance settings that may influence the system color values.
    */

    @NotNull AppearanceSettings getSettings();

    /**
      Return a non-modifiable map that provides the current values of the system colors associated with this appearance.
    */

    @NotNull Map<String,Color> getColors();
}
