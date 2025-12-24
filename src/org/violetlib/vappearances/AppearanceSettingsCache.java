/*
 * Copyright (c) 2025 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vappearances;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/* package private */ final class AppearanceSettingsCache
{
    private @Nullable AppearanceSettings cache;

    public synchronized void invalidate()
    {
        cache = null;
    }

    public synchronized @NotNull AppearanceSettings get(@NotNull Supplier<AppearanceSettings> source)
    {
        if (cache == null) {
            cache = source.get();
        }
        return cache;
    }
}
