
package com.android.cglib.dx.util;

public interface AnnotatedOutput
        extends Output {
    public boolean annotates();

    public boolean isVerbose();

    public void annotate(String msg);

    public void annotate(int amt, String msg);

    public void endAnnotation();

    public int getAnnotationWidth();
}
