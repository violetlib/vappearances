/*
 * Copyright (c) 2018-2026 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

/**
 * Native code support for VAppearances.
 */

static int VERSION = 3;

#import <Cocoa/Cocoa.h>
#import <CoreServices/CoreServices.h>
#import <CoreFoundation/CoreFoundation.h>
#import <Availability.h>

#import "VAppearances.h"
#include <stdio.h>
#include "jnix.h"
#include "org_violetlib_vappearances_VAppearances.h"
#include <pthread.h>

static NSMutableDictionary *appearances;
static jobject settingsCallback;
static jobject effectiveAppearanceCallback;
static BOOL isInitialized;
static JavaVM *vm;
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;  // protects isInitialized and appearances

static void invokeRunnable(jobject callback);

static BOOL DEBUG_FLAG;

@interface MyObserver : NSObject
@end

@implementation MyObserver
- (void)observeValueForKeyPath:(NSString *)keyPath
                      ofObject:(id)object
                        change:(NSDictionary<NSKeyValueChangeKey, id> *)change
                       context:(void *)context
{
    if (DEBUG_FLAG) {
        NSLog(@"VAppearances [native]: %@ changed", keyPath);
    }

    if (effectiveAppearanceCallback != NULL) {
        invokeRunnable(effectiveAppearanceCallback);
    }
}
@end

static MyObserver *myObserver;

static void runOnMainThread(void (^block)())
{
    APPKIT_EXEC(block);
}

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

static NSString *obtainSystemColorsForCurrentAppearance()
{
    NSString *appearanceName = nil;

    if (@available(macOS 11, *)) {
        if (NSAppearance.currentDrawingAppearance != nil) {
            appearanceName = NSAppearance.currentDrawingAppearance.name;
            if (DEBUG_FLAG) {
               NSLog(@"VAppearances [native]: fetching system colors for appearance %@", appearanceName);
            }
        }
    }

    NSString *hcs = [NSUserDefaults.standardUserDefaults stringForKey:@"AppleHighlightColor"];

    id acv = [NSUserDefaults.standardUserDefaults objectForKey:@"AppleAccentColor"];

    NSInteger tintedOption = [NSUserDefaults.standardUserDefaults integerForKey:@"NSGlassDiffusionSetting"];

    BOOL increaseContrastOption = NSWorkspace.sharedWorkspace.accessibilityDisplayShouldIncreaseContrast;

    BOOL reduceTransparencyOption = NSWorkspace.sharedWorkspace.accessibilityDisplayShouldReduceTransparency;

    if (DEBUG_FLAG) {
        NSLog(@"Highlight color: %@", hcs ? hcs : @"Accent");
        NSLog(@"Accent color: %@", acv ? acv : @"Multicolor");
        NSLog(@"Tinted option: %ld", tintedOption);
        NSLog(@"Increase contrast option: %@", increaseContrastOption ? @"YES" : @"NO");
        NSLog(@"Reduce transparency option: %@", reduceTransparencyOption ? @"YES" : @"NO");
    }

    NSMutableString *stream = [NSMutableString stringWithCapacity:2000];
    if (appearanceName != nil) {
        [stream appendFormat: @"#Appearance: %@\n", appearanceName];
    }
    [stream appendFormat: @"#Highlight color: %@\n", hcs];
    [stream appendFormat: @"#Accent color: %ld\n", acv];
    [stream appendFormat: @"#Tinted: %ld\n", tintedOption];
    [stream appendFormat: @"#Increase contrast: %ld\n", increaseContrastOption];
    [stream appendFormat: @"#Reduce transparency: %ld\n", reduceTransparencyOption];

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
    if (@available(macOS 10.15, *)) {
        registerColor(stream, [NSColor systemIndigoColor], @"systemIndigo");
        registerColor(stream, [NSColor systemTealColor], @"systemTeal");
    } else {
        registerDefaultColor(stream, @"systemIndigo", .36, .35, .87, 1);
        registerDefaultColor(stream, @"systemTeal", .37, .80, .99, 1);
    }
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
    return stream;
}

static NSAppearance *getNamedAppearance(NSString *appearanceName)
{
    NSAppearance *appearance = [NSAppearance appearanceNamed: appearanceName];

    pthread_mutex_lock(&mutex);
    if (![appearanceName isEqualToString:[appearance name]]) {
        // If the appearance is not available by name, the default appearance is returned.
        // Perhaps we already know it?
        // under what circumstances does this occur?
        NSLog(@"VAppearances [native]: did not find appearance %@", appearanceName);
        appearance = (NSAppearance *) appearances[appearanceName];
    } else {
        appearances[appearanceName] = appearance;
    }
    pthread_mutex_unlock(&mutex);

    return appearance;
}

static NSString *obtainSystemColorsForNamedAppearance(NSString *appearanceName)
{
    __block NSString *result = nil;

    pthread_mutex_lock(&mutex);
    ensureInitialized();
    pthread_mutex_unlock(&mutex);

    if (@available(macOS 11, *)) {
        NSAppearance *appearance = getNamedAppearance(appearanceName);
        if (appearance != nil) {
            [appearance performAsCurrentDrawingAppearance: ^(){result = obtainSystemColorsForCurrentAppearance();}];
        }
    }
    if (result == nil) {
        result = obtainSystemColorsForCurrentAppearance();
    }

    return result;
}

JNIEXPORT jint JNICALL Java_org_violetlib_vappearances_VAppearances_nativeGetAppearanceSettings
  (JNIEnv *env, jclass cl, jintArray jints, jobjectArray jobjects)
{
    jint result = -1;

    COCOA_ENTER();

    jsize intCount = (*env)->GetArrayLength(env, jints);
    jsize objectCount = (*env)->GetArrayLength(env, jobjects);
    int *data = (*env)->GetIntArrayElements(env, jints, NULL);

    if (objectCount > 0) {
        NSAppearanceName appearanceName;
        if (@available(macOS 10.14, *)) {
            appearanceName = [NSApp.effectiveAppearance name];
        } else {
            appearanceName = NSAppearanceNameAqua;
        }
        (*env)->SetObjectArrayElement(env, jobjects, 0, TO_JAVA_STRING(appearanceName));
    }

    if (objectCount > 1) {
        NSString *hcs = [NSUserDefaults.standardUserDefaults stringForKey:@"AppleHighlightColor"];
        (*env)->SetObjectArrayElement(env, jobjects, 1, TO_JAVA_STRING(hcs));
    }

    if (data != NULL) {

        if (intCount > 0) {
            NSInteger acv = [NSUserDefaults.standardUserDefaults integerForKey:@"AppleAccentColor"];
            // map null (multicolor) to -2
            data[0] = acv != NULL ? acv : -2;
        }

        if (intCount > 1) {
            NSInteger tintedOption = [NSUserDefaults.standardUserDefaults integerForKey:@"NSGlassDiffusionSetting"];
            data[1] = tintedOption;
        }

        if (intCount > 2) {
            BOOL increaseContrastOption = NSWorkspace.sharedWorkspace.accessibilityDisplayShouldIncreaseContrast;
            if (DEBUG_FLAG) {
                NSLog(@"Increase contrast option: %@", increaseContrastOption ? @"YES" : @"NO");
            }
            data[2] = increaseContrastOption;
        }

        if (intCount > 3) {
            BOOL reduceTransparencyOption = NSWorkspace.sharedWorkspace.accessibilityDisplayShouldReduceTransparency;
            if (DEBUG_FLAG) {
                NSLog(@"Reduce transparency option: %@", reduceTransparencyOption ? @"YES" : @"NO");
            }
            data[3] = reduceTransparencyOption;
        }

        (*env)->ReleaseIntArrayElements(env, jints, data, 0);
        result = 0;
    }

    COCOA_EXIT();

    return result;
}

JNIEXPORT jstring JNICALL Java_org_violetlib_vappearances_VAppearances_nativeGetSystemColorsData
  (JNIEnv *env, jclass cl, jstring jAppearanceName)
{
    // Obtain the system colors for the appearance specified by name.

    jstring result = nil;
    __block NSString *data;

    COCOA_ENTER();

    __block NSString *appearanceName = TO_NSSTRING(jAppearanceName);
    runOnMainThread(^() {
        data = [obtainSystemColorsForNamedAppearance(appearanceName) retain];
    });

    if (data != nil) {
        result = TO_JAVA_STRING(data);
    }

    COCOA_EXIT();

   return result;
}

static void invokeRunnable(jobject callback)
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
            jmethodID m = (*env)->GetMethodID(env, cl, "run", "()V");
            if (m != NULL) {
                (*env)->CallVoidMethod(env, callback, m);
            } else {
                NSLog(@"Unable to invoke callback: run method not found");
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
JNIEXPORT void VAppearances_updateAppearance(NSAppearance *appearance)
{
    appearance = [NSAppearance appearanceNamed:appearance.name];

    pthread_mutex_lock(&mutex);
    ensureInitialized();
    // Check to see if we already know about this appearance
    NSAppearance *existingAppearance = appearances[appearance.name];
    if (appearance != existingAppearance) {
        // Remember the appearance so that it can be updated if a system color changes.
        appearances[appearance.name] = appearance;
    }
    pthread_mutex_unlock(&mutex);
}

static void systemColorsChanged()
{
    // This function is called when a setting is changed that might affect the value of the system colors.

    // This function is called with no lock.

    if (DEBUG_FLAG) {
        NSLog(@"VAppearances [native]: system colors changed");
    }

    if (settingsCallback) {
        invokeRunnable(settingsCallback);
    }
}

static void displayOptionsChanged()
{
    // This function is called when an accessibility display option is changed.

    // This function is called with no lock.

    if (DEBUG_FLAG) {
        NSLog(@"VAppearances [native]: display options changed");
    }

    if (settingsCallback) {
        invokeRunnable(settingsCallback);
    }
}

static void registerListeners(JNIEnv *env, jobject settingsListener, jobject effectiveAppearanceListener)
{
    pthread_mutex_lock(&mutex);
    settingsCallback = settingsListener;
    effectiveAppearanceCallback = effectiveAppearanceListener;
    BOOL doIt = NO;
    if (!isInitialized) {
        isInitialized = YES;
        doIt = YES;
    }
    pthread_mutex_unlock(&mutex);

    if (doIt) {
        jint status = (*env)->GetJavaVM(env, &vm);
        if (status || !vm) {
            NSLog(@"Unable to get Java virtual machine: %d", status);
        } else {

            if (@available(macOS 10.14, *)) {
                myObserver = [[MyObserver alloc] init];
                [NSApp addObserver:myObserver
                        forKeyPath:@"effectiveAppearance"
                           options:0
                           context:NULL];
                [NSApp addObserver:myObserver
                        forKeyPath:@"appearance"
                           options:0
                           context:NULL];
            }

           // debug
           // showAppearanceNames();

            // The system colors change notification is generated when the accent color or the highlight color is changed.
            // Prior to macOS 10.14, this notification is generated when the Blue/Graphite appearance is changed.

            // These notifications may be redundant. An effective appearance change is also delivered (via a cooperating
            // NSView). Perhaps these notifications are needed on older macOS releases?

            NSNotificationCenter *dnc = [NSNotificationCenter defaultCenter];
            [dnc addObserverForName:NSSystemColorsDidChangeNotification
                             object:nil
                              queue:[NSOperationQueue mainQueue]
                         usingBlock:^(NSNotification *note){systemColorsChanged();}
            ];

            NSNotificationCenter *nc = NSWorkspace.sharedWorkspace.notificationCenter;
            [nc addObserverForName:NSWorkspaceAccessibilityDisplayOptionsDidChangeNotification
                            object:nil
                             queue:[NSOperationQueue mainQueue]
                        usingBlock:^(NSNotification *note){displayOptionsChanged();}
            ];
        }
    }
}

JNIEXPORT void JNICALL Java_org_violetlib_vappearances_VAppearances_nativeRegisterListeners
  (JNIEnv *env, jclass cl, jobject jSettingsListener, jobject jEffectiveAppearanceListener)
{
    COCOA_ENTER();
    jobject sl = (*env)->NewGlobalRef(env, jSettingsListener);
    jobject eal = (*env)->NewGlobalRef(env, jEffectiveAppearanceListener);
    registerListeners(env, sl, eal);
    COCOA_EXIT();
}

/*
 * Class:     org_violetlib_vappearances_VAppearances
 * Method:    nativeGetApplicationAppearanceName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_violetlib_vappearances_VAppearances_nativeGetApplicationAppearanceName
  (JNIEnv *env, jclass cl)
{
    jstring result = NULL;

    COCOA_ENTER();

    NSAppearanceName appearanceName;
    if (@available(macOS 10.14, *)) {
        appearanceName = [NSApp.appearance name];
    } else {
        appearanceName = NSAppearanceNameAqua;
    }
    result = TO_JAVA_STRING(appearanceName);

    COCOA_EXIT();

    return result;
}

/*
 * Class:     org_violetlib_vappearances_VAppearances
 * Method:    nativeSetApplicationAppearance
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_org_violetlib_vappearances_VAppearances_nativeSetApplicationAppearance
  (JNIEnv *env, jclass cl, jstring jAppearanceName)
{
    jint result = -1;

    COCOA_ENTER();

    NSAppearance *appearance = nil;
    if (jAppearanceName) {
        NSString *appearanceName = TO_NSSTRING(jAppearanceName);
        NSAppearance *app = [NSAppearance appearanceNamed: appearanceName];
        if ([appearanceName isEqualToString: app.name]) {
            // If the appearance name is not recognized, some other appearance is returned.
            appearance = app;
            result = 0;
        }
    } else {
        result = 0;
    }

    if (result == 0) {
        runOnMainThread(^() {
            NSApp.appearance = appearance;
        });
    }

    COCOA_EXIT();

    return result;
}

/*
 * Class:     org_violetlib_vappearances_VAppearances
 * Method:    nativeGetApplicationEffectiveAppearanceName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_violetlib_vappearances_VAppearances_nativeGetApplicationEffectiveAppearanceName
  (JNIEnv *env, jclass cl)
{
    jstring result = NULL;

    COCOA_ENTER();

    NSAppearanceName appearanceName;
    if (@available(macOS 10.14, *)) {
        appearanceName = [NSApp.effectiveAppearance name];
    } else {
        appearanceName = NSAppearanceNameAqua;
    }
    result = TO_JAVA_STRING(appearanceName);

    COCOA_EXIT();

    return result;
}

JNIEXPORT void JNICALL Java_org_violetlib_vappearances_VAppearances_nativeSetDebugFlag
  (JNIEnv *env, jclass cl, jboolean isDebug)
{
    DEBUG_FLAG = isDebug;
}
