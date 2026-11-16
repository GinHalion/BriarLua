package com.zzzmode.apkeditor.utils;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

public class Charsets {



    public static Charset toCharset(final Charset charset) {
        return charset == null ? Charset.defaultCharset() : charset;
    }

    public static Charset toCharset(final String charset) {
        return charset == null ? Charset.defaultCharset() : Charset.forName(charset);
    }


    @Deprecated
    public static final Charset UTF_8 = Charset.forName("UTF-8");
}