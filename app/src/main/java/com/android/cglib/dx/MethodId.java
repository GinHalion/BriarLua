
package com.android.cglib.dx;

import java.util.List;

import com.android.cglib.dx.rop.cst.CstMethodRef;
import com.android.cglib.dx.rop.cst.CstNat;
import com.android.cglib.dx.rop.cst.CstString;
import com.android.cglib.dx.rop.type.Prototype;

public final class MethodId<D, R> {
    final TypeId<D> declaringType;
    final TypeId<R> returnType;
    final String name;
    final TypeList parameters;

    final CstNat nat;
    final CstMethodRef constant;

    MethodId(TypeId<D> declaringType, TypeId<R> returnType, String name, TypeList parameters) {
        if (declaringType == null || returnType == null || name == null || parameters == null) {
            throw new NullPointerException();
        }
        this.declaringType = declaringType;
        this.returnType = returnType;
        this.name = name;
        this.parameters = parameters;
        this.nat = new CstNat(new CstString(name), new CstString(descriptor(false)));
        this.constant = new CstMethodRef(declaringType.constant, nat);
    }

    public TypeId<D> getDeclaringType() {
        return declaringType;
    }

    public TypeId<R> getReturnType() {
        return returnType;
    }

    public boolean isConstructor() {
        return name.equals("<init>");
    }

    public String getName() {
        return name;
    }

    public List<TypeId<?>> getParameters() {
        return parameters.asList();
    }

    String descriptor(boolean includeThis) {
        StringBuilder result = new StringBuilder();
        result.append("(");
        if (includeThis) {
            result.append(declaringType.name);
        }
        for (TypeId t : parameters.types) {
            result.append(t.name);
        }
        result.append(")");
        result.append(returnType.name);
        return result.toString();
    }

    Prototype prototype(boolean includeThis) {
        return Prototype.intern(descriptor(includeThis));
    }

    @Override public boolean equals(Object o) {
        return o instanceof MethodId
                && ((MethodId<?, ?>) o).declaringType.equals(declaringType)
                && ((MethodId<?, ?>) o).name.equals(name)
                && ((MethodId<?, ?>) o).parameters.equals(parameters)
                && ((MethodId<?, ?>) o).returnType.equals(returnType);
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + declaringType.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + parameters.hashCode();
        result = 31 * result + returnType.hashCode();
        return result;
    }

    @Override public String toString() {
        return declaringType + "." + name + "(" + parameters + ")";
    }
}
