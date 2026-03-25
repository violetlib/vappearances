/*
 * Copyright (c) 2026 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vappearances;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
  An immutable description of known appearance data.
  The data is valid only for the specified appearance settings.
*/

public final class AppearancesData
{
    public static @NotNull AppearancesData of(@NotNull AppearanceSettings settings)
    {
        return new AppearancesData(settings, new HashMap<>());
    }

    private final @NotNull AppearanceSettings settings;
    private final @NotNull Map<String,AppearanceData> map;

    private AppearancesData(@NotNull AppearanceSettings settings, @NotNull Map<String,AppearanceData> map)
    {
        this.settings = settings;
        this.map = map;
    }

    public @NotNull AppearanceSettings getSettings()
    {
        return settings;
    }

    public @Nullable AppearanceData getData(@NotNull String appearanceName)
    {
        return map.get(appearanceName);
    }

    public @NotNull AppearancesData bind(@NotNull String appearanceName, @NotNull AppearanceData data)
    {
        Map<String,AppearanceData> newMap = new HashMap<>(map);
        newMap.put(appearanceName, data);
        return new AppearancesData(settings, Collections.unmodifiableMap(newMap));
    }
}
