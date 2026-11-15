
package com.android.cglib.dx;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.android.cglib.dx.rop.type.StdTypeList;

final class TypeList {
    final TypeId<?>[] types;
    final StdTypeList ropTypes;

    TypeList(TypeId<?>[] types) {
        this.types = types.clone();
        this.ropTypes = new StdTypeList(types.length);
        for (int i = 0; i < types.length; i++) {
            ropTypes.set(i, types[i].ropType);
        }
    }

    public List<TypeId<?>> asList() {
        return Collections.unmodifiableList(Arrays.asList(types));
    }

    @Override public boolean equals(Object o) {
        return o instanceof TypeList && Arrays.equals(((TypeList) o).types, types);
    }

    @Override public int hashCode() {
        return Arrays.hashCode(types);
    }

    @Override public String toString() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(types[i]);
        }
        return result.toString();
    }
}
