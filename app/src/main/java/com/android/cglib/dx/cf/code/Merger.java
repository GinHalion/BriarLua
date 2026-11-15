
package com.android.cglib.dx.cf.code;

import com.android.cglib.dx.rop.type.Type;
import com.android.cglib.dx.rop.type.TypeBearer;

public final class Merger {
    public static TypeBearer mergeType(TypeBearer ft1, TypeBearer ft2) {
        if ((ft1 == null) || ft1.equals(ft2)) {
            return ft1;
        } else if (ft2 == null) {
            return null;
        } else {
            Type type1 = ft1.getType();
            Type type2 = ft2.getType();

            if (type1 == type2) {
                return type1;
            } else if (type1.isReference() && type2.isReference()) {
                if (type1 == Type.KNOWN_NULL) {
                    return type2;
                } else if (type2 == Type.KNOWN_NULL) {
                    return type1;
                } else if (type1.isArray() && type2.isArray()) {
                    TypeBearer componentUnion =
                        mergeType(type1.getComponentType(),
                                type2.getComponentType());
                    if (componentUnion == null) {
                        return Type.OBJECT;
                    }
                    return ((Type) componentUnion).getArrayType();
                } else {
                    return Type.OBJECT;
                }
            } else if (type1.isIntlike() && type2.isIntlike()) {
                return Type.INT;
            } else {
                return null;
            }
        }
    }
}
