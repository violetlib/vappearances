/*
 * Copyright (c) 2018 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vappearances;

import java.io.IOException;
import java.util.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
	This class keeps track of macOS appearances and their attributes. The attributes of an appearance of interest to
	Java applications are the values of system colors.

	<p>
	On macOS 10.14, there are four standard appearances. Previously, there was just one.
	The standard appearances are identified using standard names.

	<p>>
	The values of system colors depends upon the selected appearance. Some system colors also depend on the selected
	accent color, the selected highlight color, and whether the increase contrast accessibility option is enabled. This
	class takes a snapshot of the system colors and makes the colors available in a {@link VAppearance} object. When the
	accent color, highlight color, or increase contrast option is changed, a new snapshot of the system colors is
	created and a new VAppearance object is created containing the new colors.
	</p>

	<p>
	AppKit applications are notified of changes to system colors in two ways. Some changes use a system color change
	notification. Others invoke the viewDidChangeEffectiveAppearance method on NSViews. To ensure that this class has the
	current data, an NSView should implement this method to provide the NSAppearance object to the native function
	VAppearances_updateAppearance(NSAppearance *).

	<p>
	AppKit uses instances of NSAppearance in a tricky way. At any one time, there is a current NSAppearance object for
	each of the standard appearance names. However, at various times, new instances are created that replace the
	previous instances. This trickery is not important to Java, as Java identifies appearances by their standard names.
	The current NSAppearance is determined as needed using the name.
*/

public class VAppearances
{
	public static class AppearanceChangeEvent
		extends ChangeEvent
	{
		private final @NotNull VAppearance appearance;

		public AppearanceChangeEvent(@NotNull VAppearance appearance)
		{
			super(VAppearances.class);

			this.appearance = appearance;
		}

		public @NotNull VAppearance getAppearance()
		{
			return appearance;
		}
	}

	private static final @NotNull Map<String,VAppearance> appearancesByName = new HashMap<>();

	private static final @NotNull Set<ChangeListener> changeListeners = new HashSet<>();

	private interface AppearanceChangedListener
	{
		void appearanceChanged(@NotNull String data);
	}

	/*
	  The following appearance names are defined in macOS 10.14.
	  The high contrast appearances are not available until they are used.
	  The dark Aqua appearances and the high contrast appearances are not available prior to macOS 10.14.
	*/

	public final static @NotNull String aquaAppearance = "NSAppearanceNameAqua";
	public final static @NotNull String darkAquaAppearance = "NSAppearanceNameDarkAqua";
	public final static @NotNull String vibrantLightAppearance = "NSAppearanceNameVibrantLight";
	public final static @NotNull String vibrantDarkAppearance = "NSAppearanceNameVibrantDark";

	// The following names appear to be unused:
	// public final static @NotNull String highContrastAquaAppearance = "NSAppearanceNameAccessibilityHighContrastAqua";
	// public final static @NotNull String highContrastDarkAquaAppearance = "NSAppearanceNameAccessibilityHighContrastDarkAqua";
	// public final static @NotNull String highContrastVibrantLightAppearance = "NSAppearanceNameAccessibilityHighContrastVibrantLight";
	// public final static @NotNull String highContrastVibrantDarkAppearance = "NSAppearanceNameAccessibilityHighContrastVibrantDark";

	static {
		if (NativeSupport.load()) {
			registerChangeListener(VAppearances::appearanceChanged);
		}
	}

	/**
		Return the current attributes of the specified appearance.
		@param appearanceName The appearance name.
		@return an object containing the currently known attributes.
		@throws IOException if the appearance is not defined or not available, or if the data could not be obtained.
	*/

	public static @NotNull VAppearance getAppearance(@NotNull String appearanceName)
		throws IOException
	{
		synchronized (VAppearanceImpl.class) {
			VAppearance appearance = appearancesByName.get(appearanceName);
			if (appearance != null) {
				return appearance;
			}

			if (!NativeSupport.load()) {
				throw new IOException("Unable to load native library");
			}
		}

		String data = getSystemColorsData(appearanceName);
		if (data == null) {
			throw new IOException("Appearance " + appearanceName + " is not available");
		}
		VAppearance appearance = VAppearanceImpl.parse(data);
		if (appearance != null) {
			installAppearance(appearance, "requested");
			synchronized (VAppearanceImpl.class) {
				VAppearance a = appearancesByName.get(appearanceName);
				assert a != null;
				// a is most likely the same object, but it might be a replacement
				return a;
			}
		} else {
			throw new IOException("Unable to parse appearance data");
		}
	}

	/**
	 * Return the current effective appearance of the application.
	 * @return an object containing the currently known attributes.
	 * @throws IOException if the appearance is not defined or not available, or if the data could not be obtained.
	 */

	public static @NotNull VAppearance getApplicationEffectiveAppearance()
		throws IOException
	{
		String name;

		synchronized (VAppearanceImpl.class) {

			if (!NativeSupport.load()) {
				throw new IOException("Unable to load native library");
			}

			name = nativeGetApplicationAppearanceName();
		}

		if (name == null) {
			throw new IOException("Application effective appearance is not available");
		}

		return getAppearance(name);
	}

	// Upcall from native code with (possibly) new data for an appearance
	private static void appearanceChanged(@NotNull String data)
	{
		SwingUtilities.invokeLater(() -> installAppearance(data));
	}

	private static void installAppearance(@NotNull String data)
	{
		VAppearance a = VAppearanceImpl.parse(data);
		if (a != null) {
			synchronized (VAppearances.class) {
				VAppearanceImpl old = (VAppearanceImpl) appearancesByName.get(a.getName());
				if (old == null || !data.equals(old.getData())) {
					installAppearance(a, "updated");
				} else {
					//System.err.println("VAppearances: redundant data received for " + a.getName());
				}
			}
		} else {
			System.err.println("VAppearances: invalid data received");
		}
	}

	private static void installAppearance(@NotNull VAppearance a, @NotNull String source)
	{
		String name = a.getName();

		List<ChangeListener> listenersToCall;
		VAppearanceImpl old;

		synchronized (VAppearances.class) {
			old = (VAppearanceImpl) appearancesByName.get(name);
			appearancesByName.put(name, a);
			listenersToCall = new ArrayList<>(changeListeners);
			if (old != null) {
				old.setReplacement(a);
			}
		}

		if (old != null) {
			old.setReplacement(a);
		}

		System.err.println("VAppearances: installed " + source + " " + name);

		if (!listenersToCall.isEmpty()) {
			SwingUtilities.invokeLater(() -> invokeListeners(listenersToCall, a));
		}
	}

	private static void invokeListeners(@NotNull List<ChangeListener> listeners, @NotNull VAppearance appearance)
	{
		ChangeEvent event = new AppearanceChangeEvent(appearance);
		for (ChangeListener listener : listeners) {
			listener.stateChanged(event);
		}
	}

	/**
	 * Register a change listener to be called when appearance with a new name becomes known or a previously known
	 * appearance is replaced with an appearance have the same name but different attributes (system color values).
	 * All invocations of the listener are performed on the AWT event dispatching thread. The parameter, an instance
	 * of {@link AppearanceChangeEvent}, provides the name of the new or changed appearance.
	 * @param listener The listener to be registered.
	 */

	public static synchronized void addChangeListener(@NotNull ChangeListener listener)
	{
		changeListeners.add(listener);
	}

	/**
	 * Unregister a previously registered change listener.
	 * @param listener The listener to be unregistered.
	 */

	public static synchronized void removeChangeListener(@NotNull ChangeListener listener)
	{
		changeListeners.remove(listener);
	}

	private static native @Nullable String getSystemColorsData(@NotNull String appearanceName);

	private static native void registerChangeListener(@Nullable AppearanceChangedListener listener);

	public static native @Nullable String nativeGetApplicationAppearanceName();
}
