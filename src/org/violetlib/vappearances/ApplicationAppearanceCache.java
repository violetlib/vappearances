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

import java.util.function.Supplier;

/* package private */ final class ApplicationAppearanceCache
{
    private @Nullable String applicationAppearanceName;

    public synchronized @Nullable String getCachedAppearanceName(@Nullable Supplier<String> supplier)
    {
        if (applicationAppearanceName != null) {
            return applicationAppearanceName;
        }
        if (supplier != null) {
            applicationAppearanceName = supplier.get();
        }
        return applicationAppearanceName;
    }

    public synchronized void setAppearanceName(@NotNull String name)
    {
        applicationAppearanceName = name;
    }
}
