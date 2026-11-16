
package com.android.cglib.dx.rop.type;

public interface TypeList {
    public boolean isMutable();

    public int size();

    public Type getType(int n);

    public int getWordCount();

    public TypeList withAddedType(Type type);
}
