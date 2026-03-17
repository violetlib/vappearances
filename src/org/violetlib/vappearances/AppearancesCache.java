/*
 * Copyright (c) 2025 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vappearances;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/* package private */ final class AppearancesCache
{
    private final @NotNull Map<String,VAppearance> cache = new HashMap<>();

    public final static class Result
    {
        public final @NotNull VAppearance appearance;
        public final boolean isNew;

        public Result(@NotNull VAppearance appearance, boolean isNew)
        {
            this.appearance = appearance;
            this.isNew = isNew;
        }
    }

    public synchronized void clear()
    {
        cache.clear();
    }

    public synchronized @NotNull Result get(@NotNull String appearanceName)
    {
        VAppearance appearance = cache.get(appearanceName);
        if (appearance != null) {
            return new Result(appearance, false);
        }
        boolean isDark = appearanceName.contains("Dark");
        appearance = VAppearanceImpl.create(appearanceName, isDark);
        cache.put(appearanceName, appearance);
        return new Result(appearance, true);
    }
}
