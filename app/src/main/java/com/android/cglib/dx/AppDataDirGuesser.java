
package com.android.cglib.dx;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

class AppDataDirGuesser {
    public File guess() {
        try {
            ClassLoader classLoader = guessSuitableClassLoader();
            Class<?> clazz = Class.forName("dalvik.system.PathClassLoader");
            clazz.cast(classLoader);
            String pathFromThisClassLoader = getPathFromThisClassLoader(classLoader, clazz);
            File[] results = guessPath(pathFromThisClassLoader);
            if (results.length > 0) {
                return results[0];
            }
        } catch (ClassCastException ignored) {
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    private ClassLoader guessSuitableClassLoader() {
        return AppDataDirGuesser.class.getClassLoader();
    }

    private String getPathFromThisClassLoader(ClassLoader classLoader,
            Class<?> pathClassLoaderClass) {
        try {
            Field pathField = pathClassLoaderClass.getDeclaredField("path");
            pathField.setAccessible(true);
            return (String) pathField.get(classLoader);
        } catch (NoSuchFieldException ignored) {
        } catch (IllegalAccessException ignored) {
        } catch (ClassCastException ignored) {
        }

        String result = classLoader.toString();
        return processClassLoaderString(result);
    }

    static String processClassLoaderString(String input) {
        if (input.contains("DexPathList")) {
            return processClassLoaderString43OrLater(input);
        } else {
            return processClassLoaderString42OrEarlier(input);
        }
    }

    private static String processClassLoaderString42OrEarlier(String input) {
        int index = input.lastIndexOf('[');
        input = (index == -1) ? input : input.substring(index + 1);
        index = input.indexOf(']');
        input = (index == -1) ? input : input.substring(0, index);
        return input;
    }

    private static String processClassLoaderString43OrLater(String input) {
        int start = input.indexOf("DexPathList") + "DexPathList".length();
        if (input.length() > start + 4) {
            String trimmed = input.substring(start);
            int end = trimmed.indexOf(']');
            if (trimmed.charAt(0) == '[' && trimmed.charAt(1) == '[' && end >= 0) {
                trimmed = trimmed.substring(2, end);
                String split[] = trimmed.split(",");

                for (int i = 0; i < split.length; i++) {
                    int quoteStart = split[i].indexOf('"');
                    int quoteEnd = split[i].lastIndexOf('"');
                    if (quoteStart > 0 && quoteStart < quoteEnd) {
                        split[i] = split[i].substring(quoteStart + 1, quoteEnd);
                    }
                }

                StringBuilder sb = new StringBuilder();
                for (String s : split) {
                    if (sb.length() > 0) {
                        sb.append(':');
                    }
                    sb.append(s);
                }
                return sb.toString();
            }
        }

        return input;
    }

    File[] guessPath(String input) {
        List<File> results = new ArrayList<File>();
        for (String potential : splitPathList(input)) {
            if (!potential.startsWith("/data/app/")) {
                continue;
            }
            int start = "/data/app/".length();
            int end = potential.lastIndexOf(".apk");
            if (end != potential.length() - 4) {
                continue;
            }
            int dash = potential.indexOf("-");
            if (dash != -1) {
                end = dash;
            }
            String packageName = potential.substring(start, end);
            File dataDir = new File("/data/data/" + packageName);
            if (isWriteableDirectory(dataDir)) {
                File cacheDir = new File(dataDir, "cache");
                if (fileOrDirExists(cacheDir) || cacheDir.mkdir()) {
                    if (isWriteableDirectory(cacheDir)) {
                        results.add(cacheDir);
                    }
                }
            }
        }
        return results.toArray(new File[results.size()]);
    }

    static String[] splitPathList(String input) {
       String trimmed = input;
       if (input.startsWith("dexPath=")) {
            int start = "dexPath=".length();
            int end = input.indexOf(',');

           trimmed = (end == -1) ? input.substring(start) : input.substring(start, end);
       }

       return trimmed.split(":");
    }

    boolean fileOrDirExists(File file) {
        return file.exists();
    }

    boolean isWriteableDirectory(File file) {
        return file.isDirectory() && file.canWrite();
    }
}
