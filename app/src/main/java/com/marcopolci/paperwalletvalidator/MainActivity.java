package com.marcopolci.paperwalletvalidator;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import com.marcopolci.bitcoin.scanner.ScanFragment;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.BIP38PrivateKey;
import org.bitcoinj.params.MainNetParams;

import java.util.regex.Pattern;


public class MainActivity extends ActionBarActivity implements ScanFragment.OnScanListener{

    private static final int RESUME_SCAN_DELAY = 1500;
    private TextView textAddress;
    private TextView textKey;
    private TextView textResult;
    private ECKey ecKey;
    private Address addr;

    private static final Pattern PATTERN_BITCOIN_ADDRESS = Pattern.compile("[" + new String(Base58.ALPHABET) + "]{20,40}");
    private static final Pattern PATTERN_DUMPED_PRIVATE_KEY_UNCOMPRESSED_MAINNET = Pattern.compile("5"
            + "[" + new String(Base58.ALPHABET) + "]{50}");
    private static final Pattern PATTERN_DUMPED_PRIVATE_KEY_UNCOMPRESSED_TESTNET = Pattern.compile("9"
            + "[" + new String(Base58.ALPHABET) + "]{50}");
    private static final Pattern PATTERN_DUMPED_PRIVATE_KEY_COMPRESSED_MAINNET = Pattern.compile("[KL]"
            + "[" + new String(Base58.ALPHABET) + "]{51}");
    private static final Pattern PATTERN_DUMPED_PRIVATE_KEY_COMPRESSED_TESTNET = Pattern.compile("c"
            + "[" + new String(Base58.ALPHABET) + "]{51}");
    private static final Pattern PATTERN_BIP38_PRIVATE_KEY = Pattern.compile("6P" + "[" + new String(Base58.ALPHABET) + "]{56}");
    private static final Pattern PATTERN_TRANSACTION = Pattern.compile("[0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ$\\*\\+\\-\\.\\/\\:]{100,}");
    private MediaPlayer mPlayErr;
    private MediaPlayer mPlayOk;

    @Override
    protected void onStart() {
        super.onStart();
        try {
            mPlayOk = MediaPlayer.create(this, R.raw.ok);
            mPlayErr = MediaPlayer.create(this, R.raw.error);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStop() {
        mPlayErr.release();
        mPlayErr = null;
        mPlayOk.release();
        mPlayOk = null;
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textAddress = (TextView) findViewById(R.id.textAddress);
        textKey = (TextView) findViewById(R.id.textKey);
        textResult = (TextView) findViewById(R.id.textResult);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
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
    public void OnScanResult(final String result) {
        final ScanFragment scanfragment = (ScanFragment) getFragmentManager().findFragmentById(R.id.fragmentScan);
        if (PATTERN_BITCOIN_ADDRESS.matcher(result).matches()) {
            textAddress.setText(result);
            ecKey = null;
            textKey.setText(null);
            try {
                addr = new Address(null, result);
            } catch (AddressFormatException e) {
                e.printStackTrace();
                //TODO segnalare indirizzo non leggibile
            }
            scanfragment.resumeScan(RESUME_SCAN_DELAY);
        } else if (PATTERN_BIP38_PRIVATE_KEY.matcher(result).matches()) {
            textKey.setText(result);
            //scanfragment.pauseScan();
            //dismissCamera();
            createPassphraseDialog(new PassphraseListener() {
                @Override
                public void onPassphrase(final String passphrase) {
                    final ProgressDialog progress = ProgressDialog.show(MainActivity.this, getString(R.string.bip38_progress_title),
                                                                        getString(R.string.bip38_progress_description), true);
                    new AsyncTask<Void,Void,Void>() {
                        @Override
                        protected Void doInBackground(Void... params) {
                            try {
                                //TODO manca supporto per testnet
                                final BIP38PrivateKey bip38Key = new BIP38PrivateKey(MainNetParams.get(), result);
                                ecKey = bip38Key.decrypt(passphrase);
                                //ValidateData();
                            } catch (BIP38PrivateKey.BadPassphraseException e) {
                                //TODO segnalare password sbagliata
                            } catch (AddressFormatException e) {
                                //TODO segnalare chiave non leggibile
                            }
                            return null;
                        }
                        @Override
                        protected void onPostExecute(Void r) {
                            progress.dismiss();
                            ValidateData();
                            scanfragment.resumeScan(RESUME_SCAN_DELAY);
                            //restartCamera();
                        }
                    }.execute();
                }
            }, new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    scanfragment.resumeScan(RESUME_SCAN_DELAY);
                }
            }).show(getFragmentManager(), "Passphrase");
        } else if (PATTERN_DUMPED_PRIVATE_KEY_UNCOMPRESSED_MAINNET.matcher(result).matches() ||
                   PATTERN_DUMPED_PRIVATE_KEY_COMPRESSED_MAINNET.matcher(result).matches() ||
                   PATTERN_DUMPED_PRIVATE_KEY_UNCOMPRESSED_TESTNET.matcher(result).matches() ||
                   PATTERN_DUMPED_PRIVATE_KEY_COMPRESSED_TESTNET.matcher(result).matches())
        {
            textKey.setText(result);
            try {
                ecKey = (new DumpedPrivateKey(null, result)).getKey();
                ValidateData();
            } catch (AddressFormatException e) {
                //TODO segnalare chiave non leggibile
                e.printStackTrace();
            }
            scanfragment.resumeScan(RESUME_SCAN_DELAY);
        }
    }

/*
    private void dismissCamera() {
        final ScanFragment scanfragment = (ScanFragment) getFragmentManager().findFragmentById(R.id.fragmentScan);
        if (scanfragment != null) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.detach(scanfragment);
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            ft.commit();
        }
    }

    private void restartCamera() {
        final ScanFragment scanfragment = (ScanFragment) getFragmentManager().findFragmentById(R.id.fragmentScan);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ScanFragment newscan = new ScanFragment();
        if (scanfragment != null)
            ft.replace(R.id.fragmentScan, newscan);
        else
            ft.add(R.id.fragmentScan, newscan);
        ft.commit();

    }
*/

    private void ValidateData() {
        if (addr != null && ecKey != null) {

            Address ka = ecKey.toAddress(addr.getParameters());
            Sha256Hash hash = Sha256Hash.create("sign test".getBytes());
            ECKey.ECDSASignature signature = ecKey.sign(hash);
            if (ka.equals(addr) && ecKey.verify(hash, signature)) {
                textResult.setText("OK");
                mPlayOk.start();
            }
            else {
                textResult.setText("Not valid!");
                mPlayErr.start();
            }
        }
    }

    @Override
    public void OnCameraProblem(Exception ex) {
        //showDialog(DIALOG_CAMERA_PROBLEM);
        createCameraProblemdDialog().show(getFragmentManager(),null);
    }

    private static DialogFragment createCameraProblemdDialog() {
        return new DialogFragment() {
            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                final AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
                dialog.setIcon(R.drawable.ic_menu_warning);
                dialog.setTitle(R.string.scan_camera_problem_dialog_title);
                dialog.setMessage(R.string.scan_camera_problem_dialog_message);
                dialog.setNeutralButton(R.string.button_dismiss, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which)
                    {
                        dialog.dismiss();
                    }
                });
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener()
                {
                    @Override
                    public void onCancel(final DialogInterface dialog)
                    {
                        dialog.dismiss();
                    }
                });
                return dialog.create();
            }
        };
    }

    private static interface PassphraseListener {
        public void onPassphrase(final String passphrase);
    }

    private static DialogFragment createPassphraseDialog(final PassphraseListener passphraseListener, final DialogInterface.OnCancelListener cancelListener) {
        return new DialogFragment() {
             @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                LayoutInflater inflater = getActivity().getLayoutInflater();
                builder.setView(inflater.inflate(R.layout.dialog_passphrase, null))
                       .setPositiveButton(R.string.decode, new DialogInterface.OnClickListener() {
                           @Override
                           public void onClick(DialogInterface dialog, int which) {
                               EditText edit = (EditText) ((Dialog) dialog).findViewById(R.id.passphrase);
                               String p = edit.getText().toString();
                               dialog.dismiss();
                               passphraseListener.onPassphrase(p);
                           }
                       });
                return builder.create();
            }
            @Override
            public void onCancel(DialogInterface dialog) {
                cancelListener.onCancel(dialog);
            }
        };
    }
}
