
package com.luajava;

import com.androlua.LuaContext;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class LuaInvocationHandler implements InvocationHandler {
        private final LuaContext mContext;
        private LuaObject obj;

        public LuaInvocationHandler(LuaObject obj) {
                this.obj = obj;
                mContext=obj.getLuaState().getContext();
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws LuaException {
                if (obj == null || obj.L == null || obj.L.isClosed()) {
                        return defaultReturnValue(method.getReturnType());
                }
                synchronized (obj.L) {
                        if (obj.L.isClosed()) {
                                return defaultReturnValue(method.getReturnType());
                        }
                        String methodName = method.getName();
                        LuaObject func;
                        if (obj.isFunction()){
                                func = obj;
                        }
                        else{
                                func = obj.getField(methodName);
                        }
                        Class<?> retType = method.getReturnType();

                        if (func.isNil()) {
                                return defaultReturnValue(retType);
                        }

                        Object ret = null;
                        try {
                                if (retType.equals(Void.class) || retType.equals(void.class)) {
                                        func.call(args);
                                        ret = null;
                                }
                                else {
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
                        }
                        catch (LuaException e) {
                                try {
                                        mContext.sendError(methodName, e);
                                } catch (Throwable ignored) {
                                }
                        }
                        if (ret == null)
                                return defaultReturnValue(retType);
                        return ret;
                }
        }

        private static Object defaultReturnValue(Class<?> retType) {
                if (retType.equals(void.class) || retType.equals(Void.class))
                        return null;
                if (retType.equals(boolean.class) || retType.equals(Boolean.class))
                        return Boolean.FALSE;
                if (retType.equals(int.class) || retType.equals(Integer.class))
                        return 0;
                if (retType.equals(long.class) || retType.equals(Long.class))
                        return 0L;
                if (retType.equals(double.class) || retType.equals(Double.class))
                        return 0.0;
                if (retType.equals(float.class) || retType.equals(Float.class))
                        return 0.0f;
                if (retType.equals(short.class) || retType.equals(Short.class))
                        return (short) 0;
                if (retType.equals(byte.class) || retType.equals(Byte.class))
                        return (byte) 0;
                if (retType.equals(char.class) || retType.equals(Character.class))
                        return (char) 0;
                if (Number.class.isAssignableFrom(retType))
                        return 0;
                return null;
        }
}
