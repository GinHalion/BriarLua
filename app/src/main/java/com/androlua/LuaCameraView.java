package com.androlua;

import android.content.*;
import android.graphics.*;
import android.hardware.*;
import android.util.*;
import android.view.*;
import java.io.*;

import android.hardware.Camera;

class LuaCameraView extends SurfaceView
{
	private SurfaceHolder holder = null;
	public LuaCameraView(Context context)
	{
		super(context);
		holder = this.getHolder();
		holder.addCallback(new SurfaceHolder.Callback()
			{

				private Camera mCamera;
				@Override public void surfaceDestroyed(SurfaceHolder holder)
				{
					if (mCamera != null) {
						try { mCamera.stopPreview(); } catch (Throwable ignored) {}
						try { mCamera.release(); } catch (Throwable ignored) {}
					}
					mCamera = null;
				}
				@Override
				public void surfaceCreated(SurfaceHolder holder)
				{
					try {
						mCamera = Camera.open();
					} catch (Throwable t) {
						mCamera = null;
						return;
					}
					try {
						mCamera.setPreviewDisplay(holder);
					}
					catch (IOException e) {
						try { mCamera.release(); } catch (Throwable ignored) {}
						mCamera = null;
					}
				}
				@Override
				public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
				{
					if (mCamera == null)
						return;
					try {
						Camera.Parameters parameters = mCamera.getParameters();
						parameters.setPictureFormat(PixelFormat.JPEG);
						parameters.setPreviewSize(854, 480);
						parameters.setFocusMode("auto");
						parameters.setPictureSize(2592, 1456);
						mCamera.startPreview();
					} catch (Throwable ignored) {
					}
				}
			}
		);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}
}
