/*
 * Copyright (c) 2025-2026 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vappearances;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Objects;

/**
  An immutable collection of system settings whose values can impact the system color values for a given
  appearance.
*/

public final class AppearanceSettings
{
    public static @NotNull AppearanceSettings create(boolean increaseContrast,
                                                     boolean reduceTransparency,
                                                     int tintedOption,
                                                     int accentColorIndex,
                                                     @Nullable String highlightColorName,
                                                     @Nullable Color customHighlightColor)
    {
        return new AppearanceSettings(increaseContrast, reduceTransparency, tintedOption,
          accentColorIndex, highlightColorName, customHighlightColor);
    }

    /**
      Integer values for Accent Color and standard Highlight Colors
    */

    public static final int MULTICOLOR = -2;
    public static final int GRAPHITE = -1;
    public static final int RED = 0;
    public static final int ORANGE = 1;
    public static final int YELLOW = 2;
    public static final int GREEN = 3;
    public static final int BLUE = 4;
    public static final int PURPLE = 5;
    public static final int PINK = 6;

    private final boolean increaseContrast;
    private final boolean reduceTransparency;
    private final int tintedOption;
    private final int accentColorIndex;
    private final @Nullable String highlightColorName;
    private final @Nullable Color customHighlightColor;

    private AppearanceSettings(boolean increaseContrast,
                               boolean reduceTransparency,
                               int tintedOption,
                               int accentColorIndex,
                               @Nullable String highlightColorName,
                               @Nullable Color customHighlightColor)
    {
        this.increaseContrast = increaseContrast;
        this.reduceTransparency = reduceTransparency;
        this.tintedOption = tintedOption;
        this.accentColorIndex = accentColorIndex;
        this.highlightColorName = highlightColorName;
        this.customHighlightColor = customHighlightColor;
    }

    /**
      Return the value of the increaseContrast setting.
    */
    public boolean isIncreaseContrast()
    {
        return increaseContrast;
    }

    /**
      Return the value of the reduceTransparency setting.
    */
    public boolean isReduceTransparency()
    {
        return reduceTransparency;
    }

    /**
      Return the tinted option value. This setting was introduced in macOS 26. Expected values are 0 or 1.
    */

    public int getTintedOption()
    {
        return tintedOption;
    }

    /**
      Return an identification of the accent color.
    */
    public int getAccentColorIndex()
    {
        return accentColorIndex;
    }

    /**
      Return the standard name of the accent color.
    */
    public @NotNull String getAccentColorOptionName()
    {
        switch (accentColorIndex) {
            case MULTICOLOR: return "Multicolor";
            case GRAPHITE: return "Graphite";
            case RED: return "Red";
            case ORANGE: return "Orange";
            case YELLOW: return "Yellow";
            case GREEN: return "Green";
            case BLUE: return "Blue";
            case PURPLE: return "Purple";
            case PINK: return "Pink";
            default: return "#" + accentColorIndex;
        }
    }

    /**
      Return the name of the highlight color, if standard, or null if the highlight color is a custom color or set to
      match the accent color.
    */
    public @Nullable String getHighlightColorName()
    {
        return highlightColorName;
    }

    /**
      Return the highlight color, if the highlight color is a custom color or set to match the accent color. Otherwise,
      return null.
    */
    public @Nullable Color getCustomHighlightColor()
    {
        return customHighlightColor;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AppearanceSettings that = (AppearanceSettings) o;
        return increaseContrast == that.increaseContrast
          && reduceTransparency == that.reduceTransparency
          && tintedOption == that.tintedOption
          && accentColorIndex == that.accentColorIndex
          && Objects.equals(highlightColorName, that.highlightColorName)
          && Objects.equals(customHighlightColor, that.customHighlightColor);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(increaseContrast, reduceTransparency, tintedOption,
          accentColorIndex, highlightColorName, customHighlightColor);
    }

    @Override
    public @NotNull String toString()
    {
        StringBuilder sb = new StringBuilder();
        if (increaseContrast) {
            sb.append(" IncreaseContrast");
        }
        if (reduceTransparency) {
            sb.append(" ReduceTransparency");
        }
        sb.append(" Tinted=");
        sb.append(tintedOption);
        sb.append(" Accent Color=");
        sb.append(getAccentColorOptionName());
        String highlightColorName = getHighlightColorName();
        if (highlightColorName != null) {
            sb.append(" Highlight Color=");
            sb.append(highlightColorName);
        }
        Color highlightColor = getCustomHighlightColor();
        if (highlightColor != null) {
            sb.append(" Highlight Color=");
            sb.append(toString(highlightColor));
        }
        return sb.toString().trim();
    }

    private static @NotNull String toString(@NotNull Color c)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(c.getRed());
        sb.append(" ");
        sb.append(c.getGreen());
        sb.append(" ");
        sb.append(c.getBlue());
        sb.append(" ");
        sb.append(c.getAlpha());
        return sb.toString();
    }
}
