package com.androlua;

import com.android.cglib.proxy.Enhancer;
import com.android.cglib.proxy.EnhancerInterface;
import com.android.cglib.proxy.MethodFilter;
import com.android.cglib.proxy.MethodInterceptor;


public class LuaEnhancer {

    private Enhancer mEnhancer;

    public LuaEnhancer(String cls) throws ClassNotFoundException {
        this(Class.forName(cls));
    }

    public LuaEnhancer(Class<?> cls) {
        mEnhancer = new Enhancer(LuaApplication.getInstance());
        mEnhancer.setSuperclass(cls);
    }

    public void setInterceptor(EnhancerInterface obj, MethodInterceptor interceptor) {
        obj.setMethodInterceptor_Enhancer(interceptor);
    }

    public Class<?> create() {
        try {
            return mEnhancer.create();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("luajava: enhance failed: " + e.getMessage(), e);
        }
    }

    public Class<?> create(MethodFilter filer) {
        try {
            mEnhancer.setMethodFilter(filer);
            return mEnhancer.create();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("luajava: enhance failed: " + e.getMessage(), e);
        }
    }

}
