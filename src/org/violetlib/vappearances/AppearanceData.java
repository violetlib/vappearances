/*
 * Copyright (c) 2026 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vappearances;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Map;

/**
  Data associated with a particular appearance in a given context.
*/

public final class AppearanceData
{
    public static @NotNull AppearanceData of(@NotNull Map<String,Color> systemColors)
    {
        return new AppearanceData(systemColors);
    }

    public final @NotNull Map<String,Color> systemColors;

    public AppearanceData(@NotNull Map<String,Color> systemColors)
    {
        this.systemColors = systemColors;
    }
}
