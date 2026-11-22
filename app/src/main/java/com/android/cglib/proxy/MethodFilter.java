package com.android.cglib.proxy;

import java.lang.reflect.Method;


public interface MethodFilter {
    public boolean filter(Method mode,String name);
}
