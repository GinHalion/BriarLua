
package com.android.cglib.dx.util;

public interface IntSet {

    void add(int value);

    void remove(int value);

    boolean has(int value);

    void merge(IntSet other);

    int elements();

    IntIterator iterator();
}
