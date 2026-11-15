
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
                synchronized (obj.L) {
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
                                if (retType.equals(boolean.class) || retType.equals(Boolean.class))
                                        return false;
                                else if (retType.isPrimitive() || Number.class.isAssignableFrom(retType))
                                        return 0;
                                else
                                        return null;
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
                                if (retType.equals(boolean.class) || retType.equals(Boolean.class))
                                        return false;
                                else if (retType.isPrimitive() || Number.class.isAssignableFrom(retType))
                                        return 0;
                        return ret;
                }
        }
}
