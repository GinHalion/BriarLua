
package com.luajava;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.*;

public final class LuaStateFactory
{
        private static final Map<Long,LuaState> states = new HashMap<Long,LuaState>();

        private LuaStateFactory()
        {}

        public synchronized static LuaState newLuaState()
        {
                LuaState L = new LuaState();

                states.put(L.getPointer(), L);

                return L;
        }

        public synchronized static LuaState getExistingState(long index)
        {
                LuaState l= states.get(index);
                if(l==null)
                {
                        l=new LuaState(index);
                        states.put(index,l);
                }
                return l;
        }

        public synchronized static long insertLuaState(LuaState L)
        {
                states.put(L.getPointer(), L);

                return L.getPointer();
        }

        public synchronized static void removeLuaState(long idx)
        {
                states.remove(idx);
        }


}
