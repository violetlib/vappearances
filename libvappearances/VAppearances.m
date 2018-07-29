/*
 * Copyright (c) 2018 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

/**
 * Native code support for VAppearances.
 */

static int VERSION = 1;

#include <stdio.h>
#include "jni.h"
#include "org_violetlib_vappearances_VAppearances.h"
#include <pthread.h>

#import <Cocoa/Cocoa.h>
#import <CoreServices/CoreServices.h>
#import <CoreFoundation/CoreFoundation.h>
#import <JavaNativeFoundation.h>
#import <Availability.h>

#import "VAppearances.h"

static NSMutableDictionary *appearances;
static jobject callback;
static BOOL isInitialized;
static JavaVM *vm;
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

static void ensureInitialized()
{
    if (appearances == nil) {
        appearances = [[NSMutableDictionary dictionaryWithCapacity: 12] retain];
    }
}

static BOOL similar(CGFloat f1, CGFloat f2)
{
    f1 = round(f1 * 100);
    f2 = round(f2 * 100);
    return f1 == f2;
}

static BOOL isSameColor(NSColor *base, NSColor *modified)
{
    NSColor *c1 = [base colorUsingColorSpace:NSColorSpace.sRGBColorSpace];
    if (c1 == nil) {
        return true;
    }
    NSColor *c2 = [modified colorUsingColorSpace:NSColorSpace.sRGBColorSpace];
    if (c2 == nil) {
        return true;
    }
    CGFloat red1;
    CGFloat green1;
    CGFloat blue1;
    CGFloat alpha1;
    CGFloat red2;
    CGFloat green2;
    CGFloat blue2;
    CGFloat alpha2;
    [c1 getRed:&red1 green:&green1 blue:&blue1 alpha:&alpha1];
    [c2 getRed:&red2 green:&green2 blue:&blue2 alpha:&alpha2];
    return similar(red1, red2) && similar(green1, green2) && similar(blue1, blue2) && similar(alpha1, alpha2);
}

static void registerColor1(NSMutableString *stream, NSColor *c, NSString *name)
{
    CGFloat red = c.redComponent;
    CGFloat green = c.greenComponent;
    CGFloat blue = c.blueComponent;
    CGFloat alpha = c.alphaComponent;
    [stream appendFormat: @"%@: %.2f %.2f %.2f %.2f\n", name, red, green, blue, alpha];
}

static void registerEffect(NSMutableString *stream, NSString *name, NSColor *color, NSInteger effect, NSString *suffix)
{
    if (@available(macOS 10.14, *)) {
        NSColor *modifiedColor = [color colorWithSystemEffect:effect];
        if (color != modifiedColor) {
            if (!isSameColor(color, modifiedColor)) {
                NSString *modifiedName = [name stringByAppendingFormat:@"_%@", suffix];
                NSColor *c = [modifiedColor colorUsingColorSpace:NSColorSpace.sRGBColorSpace];
                registerColor1(stream, c, modifiedName);
            }
        }
    }
}

static void registerColor(NSMutableString *stream, NSColor *color, NSString *name)
{
    NSColor *c = [color colorUsingColorSpace:NSColorSpace.sRGBColorSpace];
    if (c == nil) {
        // The color is not a simple color, probably a pattern.
        return;
    }

    registerColor1(stream, c, name);

    if (@available(macOS 10.14, *)) {
        registerEffect(stream, name, color, NSColorSystemEffectPressed, @"pressed");
        registerEffect(stream, name, color, NSColorSystemEffectDeepPressed, @"deepPressed");
        registerEffect(stream, name, color, NSColorSystemEffectDisabled, @"disabled");
        registerEffect(stream, name, color, NSColorSystemEffectRollover, @"rollover");
    }
}

static void registerColors(NSMutableString *stream, NSArray<NSColor*> *colors, NSString *name)
{
    for (int i = 0; i < colors.count; i++) {
        NSColor *c = [colors objectAtIndex:i];
        NSString *itemName = [name stringByAppendingFormat:@"_%d", i];
        registerColor(stream, c, itemName);
    }
}

static void registerDefaultColor(NSMutableString *stream, NSString *name, CGFloat red, CGFloat green, CGFloat blue, CGFloat alpha)
{
    [stream appendFormat: @"%@: %.2f %.2f %.2f %.2f\n", name, red, green, blue, alpha];
}

// Obtain the system colors for the specified appearance.
static NSString *obtainSystemColorsForAppearance(NSAppearance *appearance)
{
    NSAppearance *oldAppearance = NSAppearance.currentAppearance;
    NSAppearance.currentAppearance = appearance;

    // Determine whether the high contrast option has been enabled.
    // That option changes the values of system colors, but does not change the name of the appearance.

    BOOL isHighContrast = NSWorkspace.sharedWorkspace.accessibilityDisplayShouldIncreaseContrast;
    NSString *hcs = isHighContrast ? @" HighContrast" : @"";

    NSMutableString *stream = [NSMutableString stringWithCapacity:1000];
    [stream appendFormat: @"Appearance: %@%@\n", appearance.name, hcs];
    registerColor(stream, NSColor.systemBlueColor, @"systemBlue");
    registerColor(stream, NSColor.systemBrownColor, @"systemBrown");
    registerColor(stream, NSColor.systemGrayColor, @"systemGray");
    registerColor(stream, NSColor.systemGreenColor, @"systemGreen");
    registerColor(stream, NSColor.systemOrangeColor, @"systemOrange");
    registerColor(stream, NSColor.systemPinkColor, @"systemPink");
    registerColor(stream, NSColor.systemPurpleColor, @"systemPurple");
    registerColor(stream, NSColor.systemRedColor, @"systemRed");
    registerColor(stream, NSColor.systemYellowColor, @"systemYellow");
    registerColor(stream, NSColor.labelColor, @"label");
    registerColor(stream, NSColor.secondaryLabelColor, @"secondaryLabel");
    registerColor(stream, NSColor.tertiaryLabelColor, @"tertiaryLabel");
    registerColor(stream, NSColor.quaternaryLabelColor, @"quaternaryLabel");
    registerColor(stream, NSColor.textColor, @"text");
    registerColor(stream, NSColor.placeholderTextColor, @"placeholderText");
    registerColor(stream, NSColor.selectedTextColor, @"selectedText");
    registerColor(stream, NSColor.textBackgroundColor, @"textBackground");
    registerColor(stream, NSColor.selectedTextBackgroundColor, @"selectedTextBackground");
    registerColor(stream, NSColor.keyboardFocusIndicatorColor, @"keyboardFocusIndicator");
    if (@available(macOS 10.14, *)) {
        registerColor(stream, NSColor.unemphasizedSelectedTextColor, @"unemphasizedSelectedText");
        registerColor(stream, NSColor.unemphasizedSelectedTextBackgroundColor, @"unemphasizedSelectedTextBackground");
        registerColor(stream, NSColor.separatorColor, @"separator");
        registerColor(stream, NSColor.selectedContentBackgroundColor, @"selectedContentBackground");
        registerColor(stream, NSColor.unemphasizedSelectedContentBackgroundColor, @"unemphasizedSelectedContentBackground");
        registerColors(stream, NSColor.alternatingContentBackgroundColors, @"alternatingContentBackground");
        registerColor(stream, NSColor.controlAccentColor, @"controlAccent");
    } else {
        registerDefaultColor(stream, @"unemphasizedSelectedText", 0, 0, 0, 1);
        registerDefaultColor(stream, @"unemphasizedSelectedTextBackground", .860, .860, .860, 1);
        registerDefaultColor(stream, @"separator", 0, 0, 0, .10);
        registerColor(stream, NSColor.alternateSelectedControlColor, @"selectedContentBackground");
        registerColor(stream, NSColor.secondarySelectedControlColor, @"unemphasizedSelectedContentBackground");
        registerColors(stream, NSColor.controlAlternatingRowBackgroundColors, @"alternatingContentBackground");
        NSControlTint controlTint = NSColor.currentControlTint;
        NSColor *controlTintColor = [[NSColor colorForControlTint:controlTint] colorUsingColorSpace:NSColorSpace.sRGBColorSpace];
        registerDefaultColor(stream, @"controlAccent", controlTintColor.redComponent, controlTintColor.greenComponent, controlTintColor.blueComponent, controlTintColor.alphaComponent);
    }
    if (@available(macOS 10.13, *)) {
        registerColor(stream, NSColor.findHighlightColor, @"findHighlight");
    } else {
        registerColor(stream, NSColor.highlightColor, @"findHighlight");
    }
    registerColor(stream, NSColor.linkColor, @"link");
    registerColor(stream, NSColor.selectedMenuItemTextColor, @"selectedMenuItemText");
    registerColor(stream, NSColor.gridColor, @"grid");
    registerColor(stream, NSColor.headerTextColor, @"headerText");
    registerColor(stream, NSColor.controlColor, @"control");
    registerColor(stream, NSColor.controlBackgroundColor, @"controlBackground");
    registerColor(stream, NSColor.controlTextColor, @"controlText");
    registerColor(stream, NSColor.disabledControlTextColor, @"disabledControlText");
    registerColor(stream, NSColor.selectedControlColor, @"selectedControl");
    registerColor(stream, NSColor.selectedControlTextColor, @"selectedControlText");
    registerColor(stream, NSColor.alternateSelectedControlTextColor, @"alternateSelectedControlText");
    if (@available(macOS 10.12.2, *)) {
        registerColor(stream, NSColor.scrubberTexturedBackgroundColor, @"scrubberTexturedBackground");
    }
    registerColor(stream, NSColor.windowBackgroundColor, @"windowBackground");
    registerColor(stream, NSColor.windowFrameTextColor, @"windowFrameText");
    registerColor(stream, NSColor.underPageBackgroundColor, @"underPageBackground");
    registerColor(stream, NSColor.highlightColor, @"highlight");
    registerColor(stream, NSColor.shadowColor, @"shadow");
    NSAppearance.currentAppearance = oldAppearance;
    return stream;
}

static NSString *obtainSystemColorsForNamedAppearance(NSString *appearanceName)
{
    pthread_mutex_lock(&mutex);
    ensureInitialized();

    NSAppearance *appearance = [NSAppearance appearanceNamed: appearanceName];

    if (![appearanceName isEqualToString:[appearance name]]) {
        // If the appearance is not available by name, the default appearance is returned.
        // Perhaps we already know it?
        appearance = (NSAppearance *) appearances[appearanceName];
    } else {
        appearances[appearanceName] = appearance;
    }

    NSString *result = nil;
    if (appearance != nil) {
        result = obtainSystemColorsForAppearance(appearance);
    }
    pthread_mutex_unlock(&mutex);
    return result;
}

/*
 * Class:     org_violetlib_vappearances_VAppearances
 * Method:    getSystemColorsData
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_violetlib_vappearances_VAppearances_getSystemColorsData
  (JNIEnv *env, jclass cl, jstring jAppearanceName)
{
    // Obtain the system colors for the appearance specified by name.

    jstring result = nil;

    JNF_COCOA_ENTER(env);

    // Convert Java String to NSString
    const jchar *appearanceNameChars = (*env)->GetStringChars(env, jAppearanceName, NULL);
    NSString *appearanceName = [NSString stringWithCharacters:(UniChar *)appearanceNameChars length:(*env)->GetStringLength(env, jAppearanceName)];
    (*env)->ReleaseStringChars(env, jAppearanceName, appearanceNameChars);

    NSString *s = obtainSystemColorsForNamedAppearance(appearanceName);
    if (s != nil) {
        // Convert NSString to Java String
        result = (*env)->NewStringUTF(env, [s UTF8String]);
    }

    JNF_COCOA_EXIT(env);

   return result;
}

// Inform the Java class that (possibly new) data is available for an appearance.
static void invokeCallback(NSString *data, jobject callback)
{
    JNIEnv *env;
    jboolean attached = NO;
    int status = (*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        status = (*vm)->AttachCurrentThread(vm, (void **) &env, 0);
        if (status == JNI_OK) {
            attached = YES;
        } else {
            NSLog(@"Unable to attach thread %d", status);
        }
    }

    if (status == JNI_OK) {
       jclass cl = (*env)->GetObjectClass(env, callback);
        if (!(*env)->ExceptionOccurred(env)) {
            jmethodID m = (*env)->GetMethodID(env, cl, "appearanceChanged", "(Ljava/lang/String;)V");
            if (m != NULL) {
                jstring jData = (*env)->NewStringUTF(env, [data UTF8String]);
                (*env)->CallVoidMethod(env, callback, m, jData);
            } else {
                NSLog(@"Unable to invoke callback: appearanceChanged method not found");
            }
        }
    } else {
        NSLog(@"Unable to invoke callback %d", status);
    }

    if (attached) {
        (*vm)->DetachCurrentThread(vm);
    }
}

// This function should be called by any NSView to register its appearance after receiving the
// viewDidChangeEffectiveAppearance message.
void VAppearances_updateAppearance(NSAppearance *appearance)
{
    appearance = [NSAppearance appearanceNamed:appearance.name];

    pthread_mutex_lock(&mutex);
    ensureInitialized();
    NSString *data = nil;
    jobject theCallback = callback;
    // Check to see if we already know about this appearance
    NSAppearance *existingAppearance = appearances[appearance.name];
    if (appearance != existingAppearance) {
        // Remember the appearance so that it can be updated if a system color changes.
        appearances[appearance.name] = appearance;
    }
    if (callback != nil) {
        // Inform the Java class of the new appearance data.
        data = obtainSystemColorsForAppearance(appearance);
    }
    pthread_mutex_unlock(&mutex);
    if (data != nil) {
        NSLog(@"VAppearances: updating appearance %@", appearance.name);
        invokeCallback(data, theCallback);
    }
}

static void systemColorsChanged()
{
    // This function is called with no lock
    NSArray *knownAppearances = nil;
    pthread_mutex_lock(&mutex);
    if (appearances) {
        knownAppearances = appearances.allValues;
    }
    pthread_mutex_unlock(&mutex);
    if (callback && knownAppearances && knownAppearances.count > 0) {
        NSLog(@"VAppearances: system colors changed");
        // Assume that all known appearances may have changed
        for (NSAppearance *appearance in knownAppearances) {
            VAppearances_updateAppearance(appearance);
        }
    } else if (callback) {
        NSLog(@"VAppearances: system colors changed (no known appearances)");
        NSAppearance *appearance = [NSAppearance appearanceNamed:NSAppearanceNameAqua];
        VAppearances_updateAppearance(appearance);
    } else {
        NSLog(@"VAppearances: system colors changed (no callback)");
    }
}

static void registerChangeListener(JNIEnv *env, jobject listener)
{
    pthread_mutex_lock(&mutex);
    callback = listener;
    if (!isInitialized) {
        isInitialized = YES;

        jint status = (*env)->GetJavaVM(env, &vm);
        if (status || !vm) {
            NSLog(@"Unable to get Java virtual machine: %d", status);
        } else {

           // debug
           // showAppearanceNames();

            // The system colors change notification is generated when the accent color or the highlight color is changed.
            // Prior to macOS 10.14, this notification is generated when the Blue/Graphite appearance is changed.

            NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
            NSOperationQueue *mainQueue = [NSOperationQueue mainQueue];
            [nc addObserverForName:NSSystemColorsDidChangeNotification object:nil queue:mainQueue usingBlock:^(NSNotification *note){
                systemColorsChanged();
            }];
            [nc addObserverForName:NSWorkspaceAccessibilityDisplayOptionsDidChangeNotification object:nil queue:mainQueue usingBlock:^(NSNotification *note){
                systemColorsChanged();
            }];
        }
    }
    pthread_mutex_unlock(&mutex);
}

/*
 * Class:     org_violetlib_vappearances_VAppearances
 * Method:    registerChangeListener
 * Signature: (Ljava/lang/Runnable;)V
 */
JNIEXPORT void JNICALL Java_org_violetlib_vappearances_VAppearances_registerChangeListener
  (JNIEnv *env, jclass cl, jobject jListener)
{
    JNF_COCOA_ENTER(env);
    registerChangeListener(env, JNFNewGlobalRef(env, jListener));
    JNF_COCOA_EXIT(env);
}

/*
 * Class:     org_violetlib_vappearances_VAppearances
 * Method:    nativeGetApplicationAppearanceName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_violetlib_vappearances_VAppearances_nativeGetApplicationAppearanceName
  (JNIEnv *env, jclass cl)
{
    __block jstring result = nil;

    JNF_COCOA_ENTER(env);

    NSAppearanceName appearanceName = nil;

    if (@available(macOS 10.14, *)) {
        appearanceName = [NSApp.effectiveAppearance name];
    } else {
        appearanceName = NSAppearanceNameAqua;
    }

    if (appearanceName) {
        result = (*env)->NewStringUTF(env, [appearanceName UTF8String]);
    }

    JNF_COCOA_EXIT(env);

    return result;
}
