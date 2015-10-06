package in.omerjerk.remotedroid.app;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import in.umairkhan.remotedroid.R;


public class MainActivity extends Activity {

    SharedPreferences prefs;
    boolean hasSystemPrivileges = false;

    private static final String KEY_SYSTEM_PRIVILEGE_PREF = "has_system_privilege";

    public static final boolean DEBUG = false;

    static MediaProjection mMediaProjection;
    private MediaProjectionManager mMediaProjectionManager;

    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private Intent mResultData;
    private int mResultCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        hasSystemPrivileges = prefs.getBoolean(KEY_SYSTEM_PRIVILEGE_PREF, false);
        if (savedInstanceState == null) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
//                    final boolean isRooted = Shell.SU.available();
                    final boolean isRooted = true;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isRooted) {
                                Toast.makeText(MainActivity.this, "Device is rooted... bypassing !", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(MainActivity.this, "Device us unrooted! You won't be able to use" +
                                        "this device as a server", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                    return null;
                }
            }.execute();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mMediaProjectionManager = (MediaProjectionManager)
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Toast.makeText(this, "User cancelled the access", Toast.LENGTH_SHORT).show();
                return;
            }
            mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
            Intent startServerIntent = new Intent(MainActivity.this, ServerService.class);
            startServerIntent.setAction("START");
            startService(startServerIntent);
        }
    }

    public void startClient(View v) {
        new AddressInputDialog().show(getFragmentManager(), "Address Dialog");
    }

    public void startServer(View v) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            new StartServerServiceDialog().show(getFragmentManager(), "Start service");
        } else {
            startScreenCapture();
        }

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void startScreenCapture() {
        startActivityForResult(
                mMediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION);
    }

    @SuppressLint("ValidFragment")
    private class StartServerServiceDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Notice");
            builder.setMessage("For using the server mode, the device MUST be rooted and the app MUST be installed " +
                    "to \\system partition");
            builder.setPositiveButton("Start Server", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Intent startServerIntent = new Intent(MainActivity.this, ServerService.class);
                    startServerIntent.setAction("START");
                    startService(startServerIntent);
                    finish();
                }
            });
            return builder.create();
        }
    }
}
