package com.androlua;

import com.luajava.*;

import java.lang.ref.WeakReference;

public class LuaPrint extends JavaFunction {

    private WeakReference<LuaContext> mLuaContextRef;
    private StringBuilder output = new StringBuilder();

    public LuaPrint(LuaContext luaContext, LuaState L) {
        super(L);
        mLuaContextRef = new WeakReference<>(luaContext);
    }

    @Override
    public int execute() throws LuaException {
        LuaContext context = mLuaContextRef.get();
        if (context == null) {
            return 0;
        }

        int top = L.getTop();
        if (top < 2) {
            context.sendMsg("");
            return 0;
        }

        output.setLength(0);
        for (int i = 2; i <= top; i++) {
            int type = L.type(i);
            String val;
            String stype = L.typeName(type);

            if (stype.equals("userdata")) {
                Object obj = L.toJavaObject(i);
                val = (obj != null) ? obj.toString() : "null";
            } else if (stype.equals("boolean")) {
                val = L.toBoolean(i) ? "true" : "false";
            } else {
                val = L.toString(i);
            }

            output.append("\t").append(val);
        }

        context.sendMsg(output.toString().substring(1));
        return 0;
    }
}
