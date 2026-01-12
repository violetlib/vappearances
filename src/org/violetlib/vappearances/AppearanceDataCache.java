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

import java.util.function.Function;
import java.util.function.Supplier;

/* package private */ final class AppearanceDataCache
{
    private @Nullable AppearancesData cache;

    public synchronized @Nullable AppearanceSettings getCachedSettings(@Nullable Supplier<AppearanceSettings> supplier)
    {
        if (cache != null) {
            return cache.getSettings();
        }
        if (supplier != null) {
            AppearanceSettings settings = supplier.get();
            if (settings != null) {
                cache = AppearancesData.of(settings);
            }
            return settings;
        }
        return null;
    }

    public synchronized void install(@NotNull String appearanceName,
                                     @NotNull AppearanceSettings settings,
                                     @NotNull AppearanceData data)
    {
        if (cache == null || !cache.getSettings().equals(settings)) {
            cache = AppearancesData.of(settings);
        }
        cache = cache.bind(appearanceName, data);
    }

    public synchronized @Nullable AppearanceData get(@NotNull String appearanceName,
                                                     @NotNull Function<String,AppearanceData> source)
    {
        if (cache != null) {
            AppearanceData data = cache.getData(appearanceName);
            if (data != null) {
                return data;
            }
            data = source.apply(appearanceName);
            if (data != null) {
                cache = cache.bind(appearanceName, data);
                return data;
            }
        }
        return null;
    }
}
