/*
 * Copyright (c) 2018-2025 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vappearances;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
  This class keeps track of macOS appearances and provides access to their attributes. The attributes of an appearance
  of interest to Java applications are the values of system colors, which are context dependent.

  <p>
  On macOS 10.14 and later, there are four standard appearances, but only two appear to be used: one for light mode and
  one for dark mode. Previously, there was just one standard appearance. The standard appearances are identified using
  standard names.

  <p>
  The system color values depend upon the selected appearance. Some system color values also depend on the selected
  accent color option, the selected highlight color option, and whether the increase contrast accessibility option is
  enabled. This context is represented as an instance of {@link AppearanceSettings AppearanceSettings}.

  <p>
  AppKit applications are notified of changes to system colors in two ways. Some changes use a system color change
  notification or an accessibility option change notification. Others invoke the viewDidChangeEffectiveAppearance method
  on NSView instances. To ensure that this class has the current data, the implementation of this NSView method should
  provide the NSAppearance object to the native function VAppearances_updateAppearance(NSAppearance *).

  <p>
  AppKit uses instances of NSAppearance in a tricky way. At any one time, there is a current NSAppearance object for
  each of the standard appearance names. However, at various times, new instances are created that replace the previous
  instances. This trickery is not important to Java, as Java identifies appearances by their standard names. The current
  NSAppearance is determined as needed using the name.
*/

public class VAppearances
{
    /**
      A change event that identifies the appearance whose definition may have changed.
    */
    public static final class AppearanceChangeEvent
      extends ChangeEvent
    {
        private final @NotNull VAppearance appearance;

        public AppearanceChangeEvent(@NotNull VAppearance appearance)
        {
            super(VAppearances.class);

            this.appearance = appearance;
        }

        /**
          Return the appearance whose definition may have changed.
        */

        public @NotNull VAppearance getAppearance()
        {
            return appearance;
        }
    }

    private static final @NotNull AppearancesCache appearancesByName
      = new AppearancesCache();
    private static final @NotNull Set<ChangeListener> changeListeners
      = Collections.synchronizedSet(new HashSet<>());
    private static final @NotNull Set<ChangeListener> effectiveAppearanceChangeListeners
      = Collections.synchronizedSet(new HashSet<>());

    private VAppearances()
    {
    }

    /*
      The following appearance names are defined in macOS 10.14 and later.
      The high contrast appearances are not available until they are used and apparently are not available by name.
      The dark Aqua appearances and the high contrast appearances are not available prior to macOS 10.14.
      I have not seen any use of the vibrant appearances.
    */

    /** The name of the light Aqua appearance */
    public final static @NotNull String aquaAppearance = "NSAppearanceNameAqua";
    /** The name of the dark Aqua appearance */
    public final static @NotNull String darkAquaAppearance = "NSAppearanceNameDarkAqua";
    /** The name of the vibrant light appearance */
    public final static @NotNull String vibrantLightAppearance = "NSAppearanceNameVibrantLight";
    /** The name of the vibrant dark appearance */
    public final static @NotNull String vibrantDarkAppearance = "NSAppearanceNameVibrantDark";

    // The following names appear to be unused:
    // public final static @NotNull String highContrastAquaAppearance = "NSAppearanceNameAccessibilityHighContrastAqua";
    // public final static @NotNull String highContrastDarkAquaAppearance = "NSAppearanceNameAccessibilityHighContrastDarkAqua";
    // public final static @NotNull String highContrastVibrantLightAppearance = "NSAppearanceNameAccessibilityHighContrastVibrantLight";
    // public final static @NotNull String highContrastVibrantDarkAppearance = "NSAppearanceNameAccessibilityHighContrastVibrantDark";

    private static boolean isInitialized;

    static {
        if (NativeSupport.load()) {
            nativeRegisterListeners(VAppearances::settingsChanged, VAppearances::effectiveAppearanceChanged);
            isInitialized = true;
        }
    }

    private static final @NotNull AppearanceSettingsCache cachedAppearanceSettings = new AppearanceSettingsCache();
    private static final @NotNull SystemColorsCache systemColorsCache = new SystemColorsCache();

    private static boolean DEBUG_FLAG = true;

    /**
      Return an object representing the specified appearance.

      @param appearanceName The appearance name.
      @return an object representing the associated appearance.
      @throws IOException if the appearance is not defined or not available.
    */

    public static @NotNull VAppearance getAppearance(@NotNull String appearanceName)
      throws IOException
    {
        checkInitialized();
        AppearancesCache.Result result = appearancesByName.get(appearanceName);
        if (result.isNew) {
            if (DEBUG_FLAG) {
                System.err.println("VAppearances: registered appearance " + appearanceName);
            }
            Set<ChangeListener> listenersToCall = getChangeListeners();
            if (!listenersToCall.isEmpty()) {
                invokeListeners(listenersToCall, result.appearance);
            }
        }
        return result.appearance;
    }

    private static @NotNull Map<String,Color> readSystemColors(@NotNull String appearanceName)
      throws IOException
    {
        String data = nativeGetSystemColorsData(appearanceName);
        if (data == null) {
            throw new IOException("Appearance " + appearanceName + " is not available");
        }
        return parseColorData(data);
    }

    /**
      Return the current effective appearance of the application.

      @return an object containing the currently known attributes.
      @throws IOException if the appearance is not defined or not available, or if the data could not be obtained.
    */

    public static @NotNull VAppearance getApplicationEffectiveAppearance()
      throws IOException
    {
        checkInitialized();

        String name = nativeGetApplicationAppearanceName();
        if (name == null) {
            throw new IOException("Application effective appearance is not available");
        }

        return getAppearance(name);
    }

    /* package private */ static @NotNull Map<String,Color> getSystemColorsForAppearance(@NotNull String appearanceName)
    {
        Map<String,Color> colors = systemColorsCache.get(appearanceName);
        if (colors != null) {
            return colors;
        }
        try {
            colors = readSystemColors(appearanceName);
        } catch (IOException e) {
            colors = new HashMap<>();
        }
        systemColorsCache.put(appearanceName, colors);
        return colors;
    }

    private static void invalidateAppearanceSettings()
    {
        cachedAppearanceSettings.invalidate();
        systemColorsCache.clear();
        notifyEffectiveAppearanceChanged();
    }

    /**
      Return the current appearance settings. Appearance settings are settings whose values may influence the
      system colors.
    */
    public static @NotNull AppearanceSettings getAppearanceSettings()
      throws IOException
    {
        checkInitialized();
        try {
            return cachedAppearanceSettings.get(VAppearances::readAppearanceSettings);
        } catch (UnsupportedOperationException e) {
            throw new IOException(e.getMessage());
        }
    }

    private static @NotNull AppearanceSettings readAppearanceSettings()
      throws UnsupportedOperationException
    {
        int[] data = new int[4];
        Object[] objects = new Object[2];
        int rc = nativeGetAppearanceSettings(data, objects);
        if (rc != 0) {
            throw new UnsupportedOperationException("Unable to get appearance settings");
        }

        try {
            String appearanceName = (String) objects[0];
            String highlightColorValue = (String) objects[1];
            int accentColorIndex = data[0];
            int tintedOption = data[1];
            int increaseContrastValue = data[2];
            int reduceTransparencyValue = data[3];

            String highlightColorName
              = highlightColorValue != null ? extractHighlightColorName(highlightColorValue) : null;
            Color customHighlightColor = null;
            if (highlightColorName == null && highlightColorValue != null) {
                Color c = extractHighlightColor(highlightColorValue);
                if (c == null) {
                    // an unexpected error
                    highlightColorName = "Graphite";
                } else {
                    customHighlightColor = c;
                }
            }

            return AppearanceSettings.create(appearanceName,
              increaseContrastValue != 0,
              reduceTransparencyValue != 0,
              tintedOption,
              accentColorIndex,
              highlightColorName,
              customHighlightColor
            );

        } catch (ClassCastException e) {
            throw new UnsupportedOperationException("Unexpected appearance setting object");
        }
    }

    private static @Nullable String extractHighlightColorName(@NotNull String s)
    {
        int pos = s.lastIndexOf(" ");
        if (pos > 0) {
            String name = s.substring(pos+1);
            if (name.equals("Other")) {
                return null;
            }
            return name;
        }
        return null;
    }

    private static @Nullable Color extractHighlightColor(@NotNull String s)
    {
        List<Integer> list = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(s, " ");
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            try {
                float f = Float.parseFloat(token);
                if (f >= 0 && f <= 1) {
                    int n = Math.round(f * 256);
                    if (n == 256) {
                        n = 255;
                    }
                    list.add(n);
                }
            } catch (NumberFormatException ignore) {
            }
        }
        if (list.size() == 3) {
            return new Color(list.get(0), list.get(1), list.get(2));
        }
        return null;
    }

    private static void invokeListeners(@NotNull Collection<ChangeListener> listeners, @NotNull VAppearance appearance)
    {
        SwingUtilities.invokeLater(() -> {
            ChangeEvent event = new AppearanceChangeEvent(appearance);
            for (ChangeListener listener : listeners) {
                listener.stateChanged(event);
            }
        });
    }

    // Upcall from native code indicating a possible change to system settings related to system colors
    private static void settingsChanged()
    {
        if (DEBUG_FLAG) {
            System.err.println("VAppearances: settings changed");
        }

        invalidateAppearanceSettings();
    }

    // Upcall from native code indicating a possible change to the application effective appearance
    private static void effectiveAppearanceChanged()
    {
        if (DEBUG_FLAG) {
            System.err.println("VAppearances: effective appearance changed");
        }

        invalidateAppearanceSettings();
    }

    private static void notifyEffectiveAppearanceChanged()
    {
        SwingUtilities.invokeLater(() -> {
            ChangeEvent event = new ChangeEvent(VAppearances.class);
            for (ChangeListener listener : getEffectiveAppearanceChangeListeners()) {
                listener.stateChanged(event);
            }
        });
    }

    /**
      Register a change listener to be called when the application effective appearance is changed, for example, from
      light to dark or vice versa. The concept of an application effective appearance was introduced in macOS 10.14.
      <p>
      This listener may be called when the user specified accent color or highlight color changes.
      <p>
      All invocations of the listener are performed on the AWT event dispatching thread.

      @param listener The listener to be registered.
    */

    public static synchronized void addEffectiveAppearanceChangeListener(@NotNull ChangeListener listener)
    {
        effectiveAppearanceChangeListeners.add(listener);
    }

    /**
      Unregister a previously registered change listener.

      @param listener The listener to be unregistered.
    */

    public static synchronized void removeEffectiveAppearanceChangeListener(@NotNull ChangeListener listener)
    {
        effectiveAppearanceChangeListeners.remove(listener);
    }

    private static synchronized @NotNull Set<ChangeListener> getEffectiveAppearanceChangeListeners()
    {
        return new HashSet<>(effectiveAppearanceChangeListeners);
    }

    /**
      Register a change listener to be called when an appearance with a new name becomes known. All invocations of the
      listener are performed on the AWT event dispatching thread. The parameter, an instance of {@link
    AppearanceChangeEvent}, provides the object representing the appearance.

      @param listener The listener to be registered.
    */

    public static synchronized void addChangeListener(@NotNull ChangeListener listener)
    {
        changeListeners.add(listener);
    }

    /**
      Unregister a previously registered change listener.

      @param listener The listener to be unregistered.
    */

    public static synchronized void removeChangeListener(@NotNull ChangeListener listener)
    {
        changeListeners.remove(listener);
    }

    private static synchronized @NotNull Set<ChangeListener> getChangeListeners()
    {
        return new HashSet<>(changeListeners);
    }

    private static @NotNull Map<String,Color> parseColorData(@NotNull String data)
    {
        Map<String,Color> colors = new HashMap<>();
        StringTokenizer st = new StringTokenizer(data, "\n");
        while (st.hasMoreTokens()) {
            String line = st.nextToken();
            if (!line.startsWith("#")) {
                int pos = line.indexOf(':');
                if (pos > 0) {
                    String name = line.substring(0, pos);
                    String rest = line.substring(pos+1).trim();
                    Color color = parseColor(rest);
                    if (color != null) {
                        colors.put(name, color);
                    }
                }
            }
        }
        return Collections.unmodifiableMap(colors);
    }

    private static @Nullable Color parseColor(@NotNull String s)
    {
        StringTokenizer st = new StringTokenizer(s, " ");
        Float red = parseParameter(st);
        Float green = parseParameter(st);
        Float blue = parseParameter(st);
        Float alpha = parseParameter(st);
        if (red != null && green != null && blue != null && alpha != null) {
            return new Color(red, green, blue, alpha);
        } else {
            return null;
        }
    }

    private static @Nullable Float parseParameter(@NotNull StringTokenizer st)
    {
        if (st.hasMoreTokens()) {
            String s = st.nextToken();
            try {
                float f = Float.parseFloat(s);
                if (f < 0) {
                    f = 0;
                } else if (f > 1) {
                    f = 1;
                }
                return f;
            } catch (NumberFormatException ex) {
            }
        }
        return null;
    }

    public static void setDebugFlag(boolean b)
    {
        DEBUG_FLAG = b;
        nativeSetDebugFlag(b);
    }

    private static void checkInitialized()
      throws IOException
    {
        if (!isInitialized) {
            throw new IOException("Unable to load VAppearances native library");
        }
    }

    private static native int nativeGetAppearanceSettings(int @NotNull [] intData, Object @NotNull [] stringData);

    private static native @Nullable String nativeGetSystemColorsData(@NotNull String appearanceName);

    private static native void nativeRegisterListeners(@NotNull Runnable appearanceSettingsListener,
                                                       @NotNull Runnable effectiveAppearanceListener);

    private static native @Nullable String nativeGetApplicationAppearanceName();

    private static native void nativeSetDebugFlag(boolean b);
}
