package com.luajava;

import com.android.cglib.proxy.MethodInterceptor;
import com.android.cglib.proxy.MethodProxy;
import com.androlua.LuaContext;

import java.lang.reflect.Method;


public class LuaAbstractMethodInterceptor implements MethodInterceptor {
    private final LuaContext mContext;
    private LuaObject obj;

    public LuaAbstractMethodInterceptor(LuaObject obj) {
        this.obj = obj;
        mContext = obj.getLuaState().getContext();
    }

    @Override
    public Object intercept(Object object, Object[] args, MethodProxy methodProxy) throws Exception {
        synchronized (obj.L) {
            Method method = methodProxy.getOriginalMethod();
            String methodName = method.getName();
            LuaObject func;
            if (obj.isFunction()) {
                func = obj;
            } else {
                func = obj.getField(methodName);
            }
            Class<?> retType = method.getReturnType();

            if (func.isNil()) {
                if (retType.equals(boolean.class) || retType.equals(Boolean.class))
                    return false;
                else if (retType.isPrimitive() || Number.class.isAssignableFrom(retType))
                    return LuaState.defaultNumberValue(retType);
                else
                    return null;
            }

            Object ret = null;
            try {
                if (retType.equals(Void.class) || retType.equals(void.class)) {
                    func.call(args);
                    ret = null;
                } else {
                    ret = func.call(args);
                    if (ret != null && ret instanceof Double) {
                        ret = LuaState.convertLuaNumber((Double) ret, retType);
                    } else if (ret instanceof Long
                            && (retType.isPrimitive() || Number.class.isAssignableFrom(retType))) {
                        ret = LuaState.convertLuaNumber((Long) ret, retType);
                    } else if (ret instanceof Number
                            && (retType.isPrimitive() || Number.class.isAssignableFrom(retType))) {
                        ret = LuaState.convertLuaNumber((Number) ret, retType);
                    }
                }
            } catch (LuaException e) {
                mContext.sendError(methodName, e);
            }
            if (ret == null)
                if (retType.equals(boolean.class) || retType.equals(Boolean.class))
                    return false;
                else if (retType.isPrimitive() || Number.class.isAssignableFrom(retType))
                    return LuaState.defaultNumberValue(retType);
            return ret;
        }
    }
}
