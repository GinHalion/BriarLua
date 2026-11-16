package com.nirenr.screencapture;

import android.graphics.Bitmap;


public interface ScreenCaptureListener {
     public void onScreenCaptureDone(Bitmap bitmap);

     public void onScreenCaptureError(String msg);
}
