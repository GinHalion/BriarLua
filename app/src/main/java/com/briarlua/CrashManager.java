package com.briarlua;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.graphics.Typeface;
import android.graphics.Color;

import androidx.core.content.ContextCompat;

import com.briarlua.R;

@SuppressLint("NewApi")
public final class CrashManager {

    private static final String EXTRA_RESTART_ACTIVITY_CLASS = "EXTRA_RESTART_ACTIVITY_CLASS";
    private static final String EXTRA_SHOW_ERROR_DETAILS = "EXTRA_SHOW_ERROR_DETAILS";
    private static final String EXTRA_STACK_TRACE = "EXTRA_STACK_TRACE";
    private static final String EXTRA_EXCEPTION_TYPE = "EXTRA_EXCEPTION_TYPE";
    private static final String EXTRA_THREAD_INFO = "EXTRA_THREAD_INFO";
    private static final String EXTRA_CRASH_CONTEXT = "EXTRA_CRASH_CONTEXT";

    private static final String TAG = "crashmanager";
    private static final String INTENT_ACTION_ERROR_ACTIVITY = "ERROR";
    private static final String INTENT_ACTION_RESTART_ACTIVITY = "RESTART";
    private static final String CAOC_HANDLER_PACKAGE_NAME = "com.crashmanager";
    private static final String DEFAULT_HANDLER_PACKAGE_NAME = "com.android.internal.os";
    private static final int MAX_STACK_TRACE_SIZE = 131071;

    private static Application application;
    private static WeakReference<Activity> lastActivityCreated = new WeakReference<>(null);
    private static boolean isInBackground = false;

    private static boolean launchErrorActivityWhenInBackground = true;
    private static boolean showErrorDetails = true;
    private static boolean enableAppRestart = true;
    private static Class<? extends Activity> errorActivityClass = null;
    private static Class<? extends Activity> restartActivityClass = null;

    public static void install(Context context) {
        try {
            if (context == null) {
                Log.e(TAG, "Install failed: context is null!");
                return;
            }
            Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();
            if (oldHandler != null && oldHandler.getClass().getName().startsWith(CAOC_HANDLER_PACKAGE_NAME)) {
                Log.e(TAG, "You have already installed crashmanager, doing nothing!");
                return;
            }
            if (oldHandler != null && !oldHandler.getClass().getName().startsWith(DEFAULT_HANDLER_PACKAGE_NAME)) {
                Log.e(TAG, "IMPORTANT WARNING! You already have an UncaughtExceptionHandler, are you sure this is correct?");
            }

            application = (Application) context.getApplicationContext();
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                Log.e(TAG, "App has crashed, executing crashmanager's UncaughtExceptionHandler", throwable);
                if (errorActivityClass == null) {
                    errorActivityClass = guessErrorActivityClass(application);
                }
                if (isStackTraceLikelyConflictive(throwable, errorActivityClass)) {
                    Log.e(TAG, "Your application class or your error activity have crashed, the custom activity will not be launched!");
                } else if (launchErrorActivityWhenInBackground || !isInBackground) {
                    final Intent intent = new Intent(application, errorActivityClass);
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    throwable.printStackTrace(pw);
                    String stackTraceString = sw.toString();

                    if (stackTraceString.length() > MAX_STACK_TRACE_SIZE) {
                        String disclaimer = " [stack trace too large]";
                        stackTraceString = stackTraceString.substring(0, MAX_STACK_TRACE_SIZE - disclaimer.length()) + disclaimer;
                    }

                    if (enableAppRestart && restartActivityClass == null) {
                        restartActivityClass = guessRestartActivityClass(application);
                    } else if (!enableAppRestart) {
                        restartActivityClass = null;
                    }

                    intent.putExtra(EXTRA_STACK_TRACE, stackTraceString);
                    intent.putExtra(EXTRA_RESTART_ACTIVITY_CLASS, restartActivityClass);
                    intent.putExtra(EXTRA_SHOW_ERROR_DETAILS, showErrorDetails);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    intent.putExtra(EXTRA_EXCEPTION_TYPE, throwable.getClass().getName());
                    intent.putExtra(EXTRA_THREAD_INFO, thread.getName() + " (ID:" + thread.getId() + ")");
                    intent.putExtra(EXTRA_CRASH_CONTEXT, getCrashContext(application));
                    try {
                        application.startActivity(intent);
                    } catch (Throwable t) {
                        Log.e(TAG, "Failed to start ErrorActivity", t);
                    }
                }
                final Activity lastActivity = lastActivityCreated.get();
                if (lastActivity != null) {
                    lastActivity.finish();
                    lastActivityCreated.clear();
                }
                killCurrentProcess();
            });

            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                int currentlyStartedActivities = 0;

                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    if (activity.getClass() != errorActivityClass) {
                        lastActivityCreated = new WeakReference<>(activity);
                    }
                }

                @Override
                public void onActivityStarted(Activity activity) {
                    currentlyStartedActivities++;
                    isInBackground = (currentlyStartedActivities == 0);
                }

                @Override
                public void onActivityResumed(Activity activity) {}
                @Override
                public void onActivityPaused(Activity activity) {}

                @Override
                public void onActivityStopped(Activity activity) {
                    currentlyStartedActivities--;
                    isInBackground = (currentlyStartedActivities == 0);
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                @Override
                public void onActivityDestroyed(Activity activity) {}
            });

            Log.i(TAG, "crashmanager has been installed.");
        } catch (Throwable t) {
            Log.e(TAG, "An unknown error occurred while installing crashmanager", t);
        }
    }

    public static boolean isShowErrorDetailsFromIntent(Intent intent) {
        return intent.getBooleanExtra(EXTRA_SHOW_ERROR_DETAILS, true);
    }

    private static int getColorPrimary(Context context) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true);
        return typedValue.data;
    }

    public static SpannableStringBuilder getStackTraceFromIntent(Context context, Intent intent) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int titleColor = getColorPrimary(context);

        appendWithStyle(sb, "\n[Exception Type]\n", new StyleSpan(Typeface.BOLD), titleColor);
        appendWithStyle(sb, safeString(intent.getStringExtra(EXTRA_EXCEPTION_TYPE)) + "\n\n",
                new ForegroundColorSpan(safeColor(context, R.color.warning)), 0);

        appendWithStyle(sb, "[Thread Info]\n", new StyleSpan(Typeface.BOLD), titleColor);
        sb.append(safeString(intent.getStringExtra(EXTRA_THREAD_INFO))).append("\n\n");

        appendWithStyle(sb, "[Crash Context]\n", new StyleSpan(Typeface.ITALIC), titleColor);
        sb.append(safeString(intent.getStringExtra(EXTRA_CRASH_CONTEXT))).append("\n\n");

        appendWithStyle(sb, "[Key Stack Frames]\n", new StyleSpan(Typeface.BOLD), titleColor);
        sb.append(highlightFileLineNumbers(context, extractKeyFrames(safeString(intent.getStringExtra(EXTRA_STACK_TRACE)))));

        appendWithStyle(sb, "\n\n[Full Stack Trace]\n", new StyleSpan(Typeface.BOLD), titleColor);
        sb.append(highlightFileLineNumbers(context, safeString(intent.getStringExtra(EXTRA_STACK_TRACE))));

        return sb;
    }

    private static String safeString(String s) {
        return s == null ? "" : s;
    }

    private static int safeColor(Context context, int resId) {
        try {
            return ContextCompat.getColor(context, resId);
        } catch (Throwable t) {
            return Color.GRAY;
        }
    }

    private static SpannableStringBuilder highlightFileLineNumbers(Context context, CharSequence stackTrace) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(stackTrace);
        Pattern pattern = Pattern.compile("\\(\\w+\\.(java|kt):\\d+\\)");
        Matcher matcher = pattern.matcher(stackTrace);
        int color = safeColor(context, R.color.highlight);
        while (matcher.find()) {
            ssb.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new UnderlineSpan(), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ssb;
    }

    private static String getCrashContext(Context context) {
        return "Current Application: " + context.getPackageName();
    }

    private static String extractKeyFrames(String stackTrace) {
        try {
            StringBuilder sb = new StringBuilder();
            String[] lines = stackTrace.split("\n");
            int count = 0;
            for (String line : lines) {
                if (line.contains("com.briarlua") || line.contains("android.app") || line.contains("CRITICAL")) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(line.length() > 120 ? line.substring(0, 117) + "..." : line);
                    count++;
                    if (count >= 5) break;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "Key frame extraction failed: " + e.getMessage();
        }
    }

    public static SpannableStringBuilder getAllErrorDetailsFromIntent(Context context, Intent intent) {
        SpannableStringBuilder errorDetails = new SpannableStringBuilder();
        int sectionColor = safeColor(context, R.color.section_title);
        int warningColor = safeColor(context, R.color.warning);
        int highlightColor = safeColor(context, R.color.highlight);

        appendSection(errorDetails, "Crash Time:", sectionColor);
        errorDetails.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        errorDetails.append("\n\n");

        appendSection(errorDetails, "App Version", sectionColor);
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            appendKeyValue(errorDetails, "Name", packageInfo.versionName, highlightColor);
            long versionCode = Build.VERSION.SDK_INT >= 28 ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
            appendKeyValue(errorDetails, "Code", String.valueOf(versionCode), highlightColor);
        } catch (Throwable t) {
            errorDetails.append("Version info unavailable\n");
        }
        errorDetails.append("\n");

        appendSection(errorDetails, "Device Info", sectionColor);
        appendKeyValue(errorDetails, "Model", Build.MODEL, highlightColor);
        appendKeyValue(errorDetails, "Manufacturer", Build.MANUFACTURER, highlightColor);
        appendKeyValue(errorDetails, "SDK", Build.VERSION.SDK_INT + " (" + Build.VERSION.RELEASE + ")", highlightColor);
        errorDetails.append("\n");

        appendSection(errorDetails, "Memory Status", sectionColor);
        Runtime runtime = Runtime.getRuntime();
        appendMemoryInfo(errorDetails, "Max", runtime.maxMemory(), warningColor);
        appendMemoryInfo(errorDetails, "Free", runtime.freeMemory(), warningColor);
        appendMemoryInfo(errorDetails, "Total", runtime.totalMemory(), warningColor);
        errorDetails.append("\n");

        try {
            StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
            long freeBytes = statFs.getAvailableBytes();
            long totalBytes = statFs.getTotalBytes();
            long freeMB = freeBytes / 1024 / 1024;
            long totalMB = totalBytes / 1024 / 1024;

            appendSection(errorDetails, "Storage Status", sectionColor);
            errorDetails.append("Total: ");
            appendColoredValue(errorDetails, totalMB + " MB", highlightColor);
            errorDetails.append("\nFree: ");
            int storageColor = freeMB < 100 ? warningColor : highlightColor;
            appendColoredValue(errorDetails, freeMB + " MB", storageColor);
            errorDetails.append("\n\n");
        } catch (Exception e) {
            appendColoredValue(errorDetails, "Storage info unavailable", warningColor);
        }

        try {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            appendSection(errorDetails, "Display Metrics", sectionColor);
            String resolution = metrics.widthPixels + "x" + metrics.heightPixels;
            appendKeyValue(errorDetails, "Resolution", resolution, highlightColor);
            double screenInches = Math.sqrt(Math.pow(metrics.widthPixels / metrics.xdpi, 2) + Math.pow(metrics.heightPixels / metrics.ydpi, 2));
            appendKeyValue(errorDetails, "Size", String.format(Locale.US, "%.1f\"", screenInches), highlightColor);
            appendKeyValue(errorDetails, "Density", metrics.densityDpi + " dpi", highlightColor);
            errorDetails.append("\n\n");
        } catch (Exception e) {
            appendColoredValue(errorDetails, "Display info error", warningColor);
        }

        try {
            Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                appendSection(errorDetails, "Battery Status", sectionColor);
                int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                float percent = scale > 0 ? (level / (float) scale) * 100 : -1;
                int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                String state = (status == BatteryManager.BATTERY_STATUS_CHARGING) ? "Charging" : "Discharging";
                int batteryColor = percent < 20 ? warningColor : highlightColor;
                appendColoredValue(errorDetails, String.format(Locale.US, "%.1f%% (%s)", percent, state), batteryColor);
                errorDetails.append("\n\n");
            }
        } catch (Exception e) {
            appendColoredValue(errorDetails, "Battery info error", warningColor);
        }

        appendSection(errorDetails, "Graphics Info", sectionColor);
        appendColoredValue(errorDetails, "OpenGL: GL info unavailable", warningColor);
        errorDetails.append("\n\n");

        appendSection(errorDetails, "Crash Stack Trace", sectionColor);
        errorDetails.append(highlightPackageFrames(getStackTraceFromIntent(context, intent)));

        return errorDetails;
    }

    private static void appendSection(SpannableStringBuilder sb, String title, int color) {
        int start = sb.length();
        sb.append(title).append("\n");
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void appendKeyValue(SpannableStringBuilder sb, String key, String value, int color) {
        sb.append("• ").append(key).append(": ");
        int valueStart = sb.length();
        sb.append(safeString(value)).append("\n");
        sb.setSpan(new ForegroundColorSpan(color), valueStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void appendWithStyle(SpannableStringBuilder sb, String text, Object span, int color) {
        int start = sb.length();
        sb.append(text);
        if (color != 0) {
            sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (span != null) {
            sb.setSpan(span, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void appendMemoryInfo(SpannableStringBuilder sb, String type, long bytes, int color) {
        long mb = bytes / 1024 / 1024;
        sb.append("• ").append(type).append(": ");
        int start = sb.length();
        sb.append(String.valueOf(mb)).append(" MB\n");
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void appendColoredValue(SpannableStringBuilder sb, String value, int color) {
        int start = sb.length();
        sb.append(value);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static SpannableStringBuilder highlightPackageFrames(CharSequence stackTrace) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(stackTrace);
        Pattern pattern = Pattern.compile("(com\\.briarlua\\.[\\w$]+\\.?)+");
        Matcher matcher = pattern.matcher(stackTrace);
        while (matcher.find()) {
            ssb.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(Color.BLUE), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ssb;
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends Activity> getRestartActivityClassFromIntent(Intent intent) {
        Serializable serializedClass = intent.getSerializableExtra(EXTRA_RESTART_ACTIVITY_CLASS);
        if (serializedClass != null && serializedClass instanceof Class) {
            return (Class<? extends Activity>) serializedClass;
        }
        return null;
    }

    public static void restartApplicationWithIntent(Activity activity, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.finish();
        activity.startActivity(intent);
        killCurrentProcess();
    }

    public static void closeApplication(Activity activity) {
        activity.finish();
        killCurrentProcess();
    }

    public static boolean isLaunchErrorActivityWhenInBackground() {
        return launchErrorActivityWhenInBackground;
    }

    public static void setLaunchErrorActivityWhenInBackground(boolean v) {
        launchErrorActivityWhenInBackground = v;
    }

    public static boolean isShowErrorDetails() {
        return showErrorDetails;
    }

    public static void setShowErrorDetails(boolean v) {
        showErrorDetails = v;
    }

    public static boolean isEnableAppRestart() {
        return enableAppRestart;
    }

    public static void setEnableAppRestart(boolean v) {
        enableAppRestart = v;
    }

    public static Class<? extends Activity> getErrorActivityClass() {
        return errorActivityClass;
    }

    public static void setErrorActivityClass(Class<? extends Activity> clazz) {
        errorActivityClass = clazz;
    }

    public static Class<? extends Activity> getRestartActivityClass() {
        return restartActivityClass;
    }

    public static void setRestartActivityClass(Class<? extends Activity> clazz) {
        restartActivityClass = clazz;
    }

    private static boolean isStackTraceLikelyConflictive(Throwable throwable, Class<? extends Activity> activityClass) {
        int depth = 0;
        while (throwable != null && depth < 10) {
            StackTraceElement[] stackTrace = throwable.getStackTrace();
            for (StackTraceElement element : stackTrace) {
                if ((element.getClassName().equals("android.app.ActivityThread") && element.getMethodName().equals("handleBindApplication"))
                        || element.getClassName().equals(activityClass.getName())) {
                    return true;
                }
            }
            throwable = throwable.getCause();
            depth++;
        }
        return false;
    }

    private static Class<? extends Activity> guessRestartActivityClass(Context context) {
        Class<? extends Activity> resolved = getRestartActivityClassWithIntentFilter(context);
        if (resolved == null) resolved = getLauncherActivity(context);
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Activity> getRestartActivityClassWithIntentFilter(Context context) {
        List<ResolveInfo> resolveInfos = context.getPackageManager()
                .queryIntentActivities(new Intent().setAction(INTENT_ACTION_RESTART_ACTIVITY),
                        PackageManager.GET_RESOLVED_FILTER);
        if (resolveInfos != null && !resolveInfos.isEmpty()) {
            try {
                return (Class<? extends Activity>) Class.forName(resolveInfos.get(0).activityInfo.name);
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Failed to resolve restart activity", e);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Activity> getLauncherActivity(Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent != null && intent.getComponent() != null) {
            try {
                return (Class<? extends Activity>) Class.forName(intent.getComponent().getClassName());
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Failed to resolve launcher activity", e);
            }
        }
        return null;
    }

    private static Class<? extends Activity> guessErrorActivityClass(Context context) {
        Class<? extends Activity> resolved = getErrorActivityClassWithIntentFilter(context);
        if (resolved == null) resolved = ErrorActivity.class;
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Activity> getErrorActivityClassWithIntentFilter(Context context) {
        List<ResolveInfo> resolveInfos = context.getPackageManager()
                .queryIntentActivities(new Intent().setAction(INTENT_ACTION_ERROR_ACTIVITY),
                        PackageManager.GET_RESOLVED_FILTER);
        if (resolveInfos != null && !resolveInfos.isEmpty()) {
            try {
                return (Class<? extends Activity>) Class.forName(resolveInfos.get(0).activityInfo.name);
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Failed to resolve error activity", e);
            }
        }
        return null;
    }

    private static void killCurrentProcess() {
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(10);
    }
}