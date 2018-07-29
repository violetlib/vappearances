/*
 * Copyright (c) 2018 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vappearances;

import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.jetbrains.annotations.*;

/**
	This object represents a snapshot of the attributes of a system appearance.
*/

public class VAppearanceImpl implements VAppearance {
	private final @NotNull String name;
	private final boolean isDark;
	private final boolean isHighContrast;
	private final @NotNull String data;
	private final @NotNull Map<String,Color> colors;
	private @Nullable VAppearance replacement;

	public static @Nullable VAppearance parse(@NotNull String data)
	{
		// The first line is the appearance name, with an optional HighContrast indicator
		int pos = data.indexOf('\n');
		if (pos > 0) {
			String line = data.substring(0, pos);
			String prefix = "Appearance: ";
			if (line.startsWith(prefix)) {
				String firstLine = line.substring(prefix.length()).trim();
				String name = null;
				boolean isHighContrast = false;
				StringTokenizer st = new StringTokenizer(firstLine, " ");
				while (st.hasMoreTokens()) {
					String token = st.nextToken();
					if (name == null) {
						name = token;
					} else if (token.equals("HighContrast")) {
						isHighContrast = true;
					} else {
						System.err.println("VAppearance: unrecognized attribute: " + token);
					}
				}
				if (name != null) {
					String rest = data.substring(pos + 1);
					Map<String, Color> colors = parseData(rest);
					boolean isDark = name.contains("Dark");
					return new VAppearanceImpl(name, isDark, isHighContrast, data, colors);
				}
			}
		}
		return null;
	}

	private static @NotNull Map<String,Color> parseData(@NotNull String data)
	{
		Map<String,Color> colors = new HashMap<>();
		StringTokenizer st = new StringTokenizer(data, "\n");
		while (st.hasMoreTokens()) {
			String line = st.nextToken();
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
		return colors;
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

	private VAppearanceImpl(@NotNull String name,
							boolean isDark,
							boolean isHighContrast,
							@NotNull String data,
							@NotNull Map<String,Color> colors)
	{
		this.name = name;
		this.isDark = isDark;
		this.isHighContrast = isHighContrast;
		this.data = data;
		this.colors = Collections.unmodifiableMap(colors);
	}

	public boolean isValid()
	{
		return replacement == null;
	}

	public synchronized @Nullable VAppearance getReplacement()
	{
		return replacement;
	}

	@Override
	public @NotNull String getName()
	{
		return name;
	}

	@Override
	public boolean isDark() {
		return isDark;
	}

	@Override
	public boolean isHighContrast() {
		return isHighContrast;
	}

	/* package private */ @NotNull String getData()
	{
		return data;
	}

	@Override
	public @NotNull Map<String,Color> getColors()
	{
		return colors;
	}

	/* package private */ synchronized void setReplacement(@NotNull VAppearance replacement)
	{
		this.replacement = replacement;
	}
}
