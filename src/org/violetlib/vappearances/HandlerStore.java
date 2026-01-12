package org.violetlib.vappearances;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**

*/

public class HandlerStore
{
    private final @NotNull Set<ChangeListener> changeListeners = new HashSet<>();
    private final @NotNull Set<Runnable> runners = new HashSet<>();

    public synchronized void addChangeListener(@NotNull ChangeListener listener)
    {
        changeListeners.add(listener);
    }

    public synchronized void removeChangeListener(@NotNull ChangeListener listener)
    {
        changeListeners.remove(listener);
    }

    public synchronized void addRunner(@NotNull Runnable runner)
    {
        runners.add(runner);
    }

    public synchronized void removeRunner(@NotNull Runnable runner)
    {
        runners.remove(runner);
    }

    public synchronized @Nullable Handlers getHandlers()
     {
         Collection<ChangeListener> ls = changeListeners.isEmpty() ? null : new ArrayList<>(changeListeners);
         Collection<Runnable> rs = runners.isEmpty() ? null : new ArrayList<>(runners);
         return Handlers.optionalOf(ls, rs);
     }

}
