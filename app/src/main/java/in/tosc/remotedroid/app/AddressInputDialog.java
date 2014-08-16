package in.tosc.remotedroid.app;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * Created by omerjerk on 14/8/14.
 */
public class AddressInputDialog extends DialogFragment {

    public static final String KEY_ADDRESS_EXTRA = "address";
    public static final String KEY_LAST_ADDRESS_PREF = "last_address";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        final SharedPreferences prefs = getActivity().getSharedPreferences("MAIN_PREFS", Context.MODE_PRIVATE);
        String lastAddress = prefs.getString(KEY_LAST_ADDRESS_PREF, "");

        final LinearLayout dialogLayout = (LinearLayout) inflater.inflate(R.layout.dialog_address_input, null);
        final EditText addressInput = (EditText) dialogLayout.findViewById(R.id.address_input);
        addressInput.setText(lastAddress);

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setTitle("Enter server address");
        builder.setView(dialogLayout)
                // Add action buttons
                .setPositiveButton("Connect", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {

                        String address = addressInput.getText().toString();
                        if (!address.equals("")) {
                            Intent startIntent = new Intent(getActivity(), ClientActivity.class);
                            startIntent.putExtra(KEY_ADDRESS_EXTRA, address);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString(KEY_LAST_ADDRESS_PREF, address);
                            editor.commit();
                            startActivity(startIntent);
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        AddressInputDialog.this.getDialog().cancel();
                    }
                });
        return builder.create();
    }
}
