package in.tosc.remotedroid.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import eu.chainfire.libsuperuser.Shell;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                final boolean isRooted = Shell.SU.available();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isRooted) {
                            Toast.makeText(MainActivity.this, "Device is rooted", Toast.LENGTH_SHORT).show();
                        } else {
                            ErrorDialog errorDialog = new ErrorDialog();
                            errorDialog.show(getFragmentManager(), "NO_ROOT_DIALOG");
                        }
                    }
                });
                return null;
            }
        }.execute();
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

    public void startClient(View v) {
        new AddressInputDialog().show(getFragmentManager(), "Address Dialog");
    }

    public void startServer(View v) {
        Intent startServerIntent = new Intent(MainActivity.this, ServerService.class);
        startServerIntent.setAction("START");
        startService(startServerIntent);
        finish();
    }

    private class ErrorDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Not Rooted!");
            builder.setMessage("The device needs to be rooted for this app to use. Please exit the app.");
            builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {

                }
            });
            return builder.create();
        }
    }
}
