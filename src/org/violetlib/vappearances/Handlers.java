/*
 * Copyright (c) 2026 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vappearances;

import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.Collection;

/**
  An immutable collection of objects that may be invoked in response to some change.
*/

public final class Handlers
{
    public static @Nullable Handlers optionalOf(@Nullable Collection<ChangeListener> listeners,
                                                @Nullable Collection<Runnable> runners)
    {
        return listeners != null || runners != null ? new Handlers(listeners, runners) : null;
    }

    private final @Nullable Collection<ChangeListener> listeners;
    private final @Nullable Collection<Runnable> runners;

    public Handlers(@Nullable Collection<ChangeListener> listeners,
                    @Nullable Collection<Runnable> runners)
    {
        this.listeners = listeners;
        this.runners = runners;
    }

    public void invoke()
    {
        if (listeners != null && !listeners.isEmpty()) {
            ChangeEvent event = new ChangeEvent(VAppearances.class);
            for (ChangeListener listener : listeners) {
                listener.stateChanged(event);
            }
        }
        if (runners != null && !runners.isEmpty()) {
            for (Runnable runner : runners) {
                runner.run();
            }
        }
    }
}
