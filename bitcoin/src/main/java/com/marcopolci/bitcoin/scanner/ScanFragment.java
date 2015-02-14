/*
 * Copyright 2012-2015 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.marcopolci.bitcoin.scanner;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.Vibrator;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.marcopolci.bitcoin.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * @author Andreas Schildbach
 */
public final class ScanFragment extends Fragment implements SurfaceHolder.Callback
{
    public interface OnScanListener {
        public void OnScanResult(final String result);
        public void OnCameraProblem(Exception ex);
    }


    public static final String INTENT_EXTRA_RESULT = "result";

	private static final long VIBRATE_DURATION = 50L;
	private static final long AUTO_FOCUS_INTERVAL_MS = 2500L;

	private final CameraManager cameraManager = new CameraManager();
	private ScannerView scannerView;
	private SurfaceHolder surfaceHolder;
	private Vibrator vibrator;
	private HandlerThread cameraThread;
	private Handler cameraHandler;

	private static final int DIALOG_CAMERA_PROBLEM = 0;

	private static boolean DISABLE_CONTINUOUS_AUTOFOCUS = Build.MODEL.equals("GT-I9100") // Galaxy S2
			|| Build.MODEL.equals("SGH-T989") // Galaxy S2
			|| Build.MODEL.equals("SGH-T989D") // Galaxy S2 X
			|| Build.MODEL.equals("SAMSUNG-SGH-I727") // Galaxy S2 Skyrocket
			|| Build.MODEL.equals("GT-I9300") // Galaxy S3
			|| Build.MODEL.equals("GT-N7000"); // Galaxy Note

	private static final Logger log = LoggerFactory.getLogger(ScanFragment.class);
    private int cameraOrientation;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if ( !(activity instanceof OnScanListener))
            throw new ClassCastException(activity.toString() + " must implement OnScanResultListener");

        vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.scan_fragment, container, false);
        scannerView = (ScannerView) v.findViewById(R.id.scan_activity_mask);
        return v;
    }

    @Override
    public void onResume()
	{
		super.onResume();

		cameraThread = new HandlerThread("cameraThread", Process.THREAD_PRIORITY_BACKGROUND);
		cameraThread.start();
		cameraHandler = new Handler(cameraThread.getLooper());

		final SurfaceView surfaceView = (SurfaceView) getActivity().findViewById(R.id.scan_activity_preview);
		surfaceHolder = surfaceView.getHolder();
		surfaceHolder.addCallback(this);
		surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	@Override
	public void surfaceCreated(final SurfaceHolder holder)
	{
		cameraHandler.post(openRunnable);
	}

	@Override
	public void surfaceDestroyed(final SurfaceHolder holder)
	{
	}

	@Override
	public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height)
	{
	}

	@Override
    public void onPause()
	{
		cameraHandler.post(closeRunnable);

		surfaceHolder.removeCallback(this);

		super.onPause();
	}

	public void setTorch(final boolean enabled)
    {
        cameraHandler.post(new Runnable() {
            @Override
            public void run() {
                cameraManager.setTorch(enabled);
            }
        });
    }

	public void handleResult(final Result scanResult, final Bitmap thumbnailImage, final float thumbnailScaleFactor)
	{
		vibrator.vibrate(VIBRATE_DURATION);

		// superimpose dots to highlight the key features of the qr code
		final ResultPoint[] points = scanResult.getResultPoints();
		if (points != null && points.length > 0)
		{
			final Paint paint = new Paint();
			paint.setColor(getResources().getColor(R.color.scan_result_dots));
			paint.setStrokeWidth(10.0f);

			final Canvas canvas = new Canvas(thumbnailImage);
			canvas.scale(thumbnailScaleFactor, thumbnailScaleFactor);
			for (final ResultPoint point : points)
				canvas.drawPoint(point.getX(), point.getY(), paint);
		}

        // ruota l'immagine catturata perché ScannerView.drawResultBitmap() si comporta come se l'orientamento fosse sempre orizzontale
        Bitmap targetBitmap = Bitmap.createBitmap(thumbnailImage.getWidth(), thumbnailImage.getHeight(), thumbnailImage.getConfig());
        Canvas canvas = new Canvas(targetBitmap);
        float sf = (float) thumbnailImage.getWidth() / (float) thumbnailImage.getHeight();
        Matrix matrix = new Matrix();
        matrix.setRotate(cameraOrientation, thumbnailImage.getWidth() / 2, thumbnailImage.getHeight() / 2);
        matrix.postScale(sf, 1, thumbnailImage.getWidth() / 2, thumbnailImage.getHeight() / 2);
        canvas.drawBitmap(thumbnailImage, matrix, new Paint());

        scannerView.drawResultBitmap(targetBitmap);

        ((OnScanListener) getActivity()).OnScanResult(scanResult.getText());

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                scannerView.drawResultBitmap(null);
            }
        }, 1000);
	}

	private final Runnable openRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			try
			{
				final Camera camera = cameraManager.open(surfaceHolder, !DISABLE_CONTINUOUS_AUTOFOCUS);

                Display display = ((WindowManager)getActivity().getSystemService(Activity.WINDOW_SERVICE)).getDefaultDisplay();
                switch (display.getRotation()){
                    case Surface.ROTATION_270:
                    case Surface.ROTATION_90:
                        cameraOrientation = 0;
                        break;
                    default:
                        cameraOrientation = 90;
                        break;
                }
                camera.setDisplayOrientation(cameraOrientation);

                final Rect framingRect = cameraManager.getFrame();
				final Rect framingRectInPreview = cameraManager.getFramePreview();

				getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scannerView.setFraming(framingRect, framingRectInPreview);
                    }
                });

				final String focusMode = camera.getParameters().getFocusMode();
				final boolean nonContinuousAutoFocus = Camera.Parameters.FOCUS_MODE_AUTO.equals(focusMode)
						|| Camera.Parameters.FOCUS_MODE_MACRO.equals(focusMode);

				if (nonContinuousAutoFocus)
					cameraHandler.post(new AutoFocusRunnable(camera));

				cameraHandler.post(fetchAndDecodeRunnable);
			}
			catch (final IOException | RuntimeException x)
			{
				log.info("problem opening camera", x);
                ((OnScanListener)getActivity()).OnCameraProblem(x);
			}
        }
	};

	private final Runnable closeRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			cameraManager.close();

			// cancel background thread
			cameraHandler.removeCallbacksAndMessages(null);
			cameraThread.quit();
		}
	};

	private final class AutoFocusRunnable implements Runnable
	{
		private final Camera camera;

		public AutoFocusRunnable(final Camera camera)
		{
			this.camera = camera;
		}

		@Override
		public void run()
		{
			camera.autoFocus(new Camera.AutoFocusCallback()
			{
				@Override
				public void onAutoFocus(final boolean success, final Camera camera)
				{
					// schedule again
					cameraHandler.postDelayed(AutoFocusRunnable.this, AUTO_FOCUS_INTERVAL_MS);
				}
			});
		}
	}

	private final Runnable fetchAndDecodeRunnable = new Runnable()
	{
		private final QRCodeReader reader = new QRCodeReader();
		private final Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);

		@Override
		public void run()
		{
			cameraManager.requestPreviewFrame(new PreviewCallback()
			{
				@Override
				public void onPreviewFrame(final byte[] data, final Camera camera)
				{
					decode(data);
				}
			});
		}

		private void decode(final byte[] data)
		{
			final PlanarYUVLuminanceSource source = cameraManager.buildLuminanceSource(data);
			final BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

			try
			{
				hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, new ResultPointCallback()
				{
					@Override
					public void foundPossibleResultPoint(final ResultPoint dot)
					{
						getActivity().runOnUiThread(new Runnable()
						{
							@Override
							public void run()
							{
								scannerView.addDot(dot);
							}
						});
					}
				});
				final Result scanResult = reader.decode(bitmap, hints);

				final int thumbnailWidth = source.getThumbnailWidth();
				final int thumbnailHeight = source.getThumbnailHeight();
				final float thumbnailScaleFactor = (float) thumbnailWidth / source.getWidth();

                final Bitmap thumbnailImage = Bitmap.createBitmap(thumbnailWidth, thumbnailHeight, Bitmap.Config.ARGB_8888);
                thumbnailImage.setPixels(source.renderThumbnail(), 0, thumbnailWidth, 0, 0, thumbnailWidth, thumbnailHeight);

                getActivity().runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						handleResult(scanResult, thumbnailImage, thumbnailScaleFactor);
					}
				});
			}
			catch (final ReaderException x)
			{
				// retry
				cameraHandler.post(fetchAndDecodeRunnable);
			}
			finally
			{
				reader.reset();
			}
		}
	};

    public void resumeScan(long delay) {
        cameraHandler.postDelayed(fetchAndDecodeRunnable, delay);
    }
}
