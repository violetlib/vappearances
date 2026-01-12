/*
 * Copyright (c) 2018-2026 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vappearances;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
  This object represents a system appearance (identified by an appearance name)
  and provides access to its attributes.
*/

/* package private */ class VAppearanceImpl
  implements VAppearance
{
    public static @NotNull VAppearanceImpl create(@NotNull String name, boolean isDark)
    {
        return new VAppearanceImpl(name, isDark);
    }

    private final @NotNull String name;
    private final boolean isDark;

    private VAppearanceImpl(@NotNull String name, boolean isDark)
    {
        this.name = name;
        this.isDark = isDark;
    }

    @Override
    public @NotNull String getName()
    {
        return name;
    }

    @Override
    public boolean isDark()
    {
        return isDark;
    }

    @Override
    public boolean isHighContrast()
    {
        try {
            AppearanceSettings settings = VAppearances.getAppearanceSettings();
            return settings.isIncreaseContrast();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public boolean isTinted()
    {
        try {
            AppearanceSettings settings = VAppearances.getAppearanceSettings();
            return settings.getTintedOption() > 0;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public @NotNull AppearanceSettings getSettings()
    {
        try {
            return VAppearances.getAppearanceSettings();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public @NotNull Map<String,Color> getColors()
    {
        Map<String,Color> colors = VAppearances.getSystemColorsForAppearance(name);
        return colors != null ? colors : new HashMap<>();
    }

    @Override
    public @NotNull String toString()
    {
        String s = name;
        if (isDark) {
            s = s + " [Dark]";
        }
        return s;
    }
}
