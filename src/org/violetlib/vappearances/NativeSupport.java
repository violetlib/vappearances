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

import java.io.*;
import java.security.AccessControlException;
import java.util.StringTokenizer;

/**
  Manages the native support library.
*/

/* package private */ class NativeSupport
{
    private static final String libraryName = "vappearances";

    private static boolean isAvailable;
    private static boolean isInitialized;

    /**
      Load the native library, if not already loaded.
      @return true if the library has been loaded, false if the library cannot be loaded.
    */
    public static synchronized boolean load()
    {
        if (!isInitialized) {
            isInitialized = true;
            loadNativeSupport();
        }
        return isAvailable;
    }

    private static void loadNativeSupport()
    {
        try {
            String fn = findNativeLibrary(NativeSupport.class, libraryName);
            if (fn == null) {
                reportError("Library " + libraryName + " not found");
                return;
            }
            System.load(fn);
            isAvailable = true;
        } catch (UnsatisfiedLinkError e) {
            reportError(e.getMessage());
        } catch (AccessControlException e) {
            reportError("permission denied: " + e.getMessage());
        } catch (Throwable e) {
            reportError(e.toString());
            e.printStackTrace();
        }
    }

    private static void reportError(@NotNull String msg)
    {
        String p = System.mapLibraryName(libraryName);
        String s = "Unable to load library " + p + ": " + msg;
        System.err.println(s);
    }

    private static @Nullable String findNativeLibrary(@NotNull Class<?> root, @NotNull String name)
      throws IllegalArgumentException
    {
        String opt = System.getProperty("useClasspathFirst");
        if ("true".equals(opt)) {
            String lf = findNativeLibraryOnClasspath(root, name);
            if (lf != null) {
                return lf;
            }
            return findNativeLibraryOnPath(name);
        } else {
            String lf = findNativeLibraryOnPath(name);
            if (lf != null) {
                return lf;
            }
            return findNativeLibraryOnClasspath(root, name);
        }
    }

    private static @Nullable String findNativeLibraryOnClasspath(@NotNull Class<?> root, @NotNull String name)
    {
        String prefix = "lib" + name;
        String suffix = ".dylib";
        String libfn = prefix + suffix;

        // Try to find the library as a resource of the specified class.
        // If found, copy the resource to a temporary file.

        InputStream s;
        try {
            s = root.getClassLoader().getResourceAsStream(libfn);
        } catch (SecurityException ex) {
            return null;
        }

        if (s == null) {
            return null;
        }

        try {
            try {
                File f = File.createTempFile(prefix, suffix).getAbsoluteFile();
                f.deleteOnExit();
                try (FileOutputStream fs = new FileOutputStream(f)) {
                    internalInitializeFile(s, fs);
                }
                return f.getPath();
            } catch (IOException ex) {
                System.err.println("Unable to extract native library resource: " + ex.getMessage());
                return null;
            }
        } finally {
            try {
                s.close();
            } catch (IOException ignore) {
            }
        }
    }

    private static @Nullable String findNativeLibraryOnPath(@NotNull String name)
      throws IllegalArgumentException
    {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Invalid library name: name is empty");
        }

        if (name.indexOf(' ') >= 0 || name.indexOf('.') >= 0 || name.indexOf(File.pathSeparatorChar) >= 0) {
            throw new IllegalArgumentException("Invalid library name");
        }

        // Try finding the library using the Java library path.

        String libfn = "lib" + name + ".dylib";
        String lp = System.getProperty("java.library.path");
        if (lp != null) {
            StringTokenizer st = new StringTokenizer(lp, ":");
            while (st.hasMoreTokens()) {
                String prefix = st.nextToken();
                File f = new File(prefix + File.separator + libfn).getAbsoluteFile();
                if (f.isFile()) {
                    return f.getPath();
                }
            }
        }

        return null;
    }

    private static void internalInitializeFile(@NotNull InputStream sin, @NotNull OutputStream sout)
      throws IOException
    {
        byte[] buf = new byte[1024];
        for (;;) {
            int count = sin.read(buf);
            if (count <= 0) {
                break;
            }
            sout.write(buf, 0, count);
        }
    }
}
