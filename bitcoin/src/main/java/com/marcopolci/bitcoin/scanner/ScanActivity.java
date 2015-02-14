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
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;

import com.marcopolci.bitcoin.R;

/**
 * @author Andreas Schildbach
 */
public final class ScanActivity extends Activity implements ScanFragment.OnScanListener
{
	public static final String INTENT_EXTRA_RESULT = "result";

	private final CameraManager cameraManager = new CameraManager();

	private static final int DIALOG_CAMERA_PROBLEM = 0;

    @Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.scan_activity2);
	}

	@Override
	public void onBackPressed()
	{
		setResult(RESULT_CANCELED);
		finish();
	}

	@Override
	public boolean onKeyDown(final int keyCode, final KeyEvent event)
	{
		switch (keyCode)
		{
			case KeyEvent.KEYCODE_FOCUS:
			case KeyEvent.KEYCODE_CAMERA:
				// don't launch camera app
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_VOLUME_UP:
                ScanFragment fragment = (ScanFragment) getFragmentManager().findFragmentById(R.id.fragmentScan);
                fragment.setTorch(keyCode == KeyEvent.KEYCODE_VOLUME_UP);
				return true;
		}

		return super.onKeyDown(keyCode, event);
	}


    @Override
    public void OnScanResult(String res) {
        final Intent result = new Intent();
        result.putExtra(INTENT_EXTRA_RESULT, res);
        setResult(RESULT_OK, result);

        // delayed finish
        new Handler().post(new Runnable()
        {
            @Override
            public void run()
            {
                finish();
            }
        });
    }

    @Override
    public void OnCameraProblem(Exception ex) {
        showDialog(DIALOG_CAMERA_PROBLEM);
    }


	@Override
	protected Dialog onCreateDialog(final int id)
	{
		if (id == DIALOG_CAMERA_PROBLEM)
		{
            final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setIcon(R.drawable.ic_menu_warning);
            dialog.setTitle(R.string.scan_camera_problem_dialog_title);
            dialog.setMessage(R.string.scan_camera_problem_dialog_message);
            dialog.setNeutralButton(R.string.button_dismiss, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(final DialogInterface dialog, final int which)
                {
                    finish();
                }
            });
            dialog.setOnCancelListener(new DialogInterface.OnCancelListener()
            {
                @Override
                public void onCancel(final DialogInterface dialog)
                {
                    finish();
                }
            });
			return dialog.create();
		}
		else
		{
			throw new IllegalArgumentException();
		}
	}
}
