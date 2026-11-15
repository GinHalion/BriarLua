
package com.android.cglib.dx.rop.annotation;

import com.android.cglib.dx.util.ToHuman;

public enum AnnotationVisibility implements ToHuman {
    RUNTIME("runtime"),
    BUILD("build"),
    SYSTEM("system"),
    EMBEDDED("embedded");

    private final String human;

    private AnnotationVisibility(String human) {
        this.human = human;
    }

    public String toHuman() {
        return human;
    }
}
