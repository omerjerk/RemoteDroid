package in.tosc.remotedroid.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;

/**
 * Created by omerjerk on 26/7/14.
 */
public class SettingsActivity extends PreferenceActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private EditTextPreference portNumberPref;
    private ListPreference bitratePref;

    public static final String KEY_PORT_PREF = "port";
    public static final String KEY_BITRATE_PREF = "bitrate";

    private static final String[] bitrateOptions = {"256 Kbps", "512 Kbps", "1 Mbps", "2 Mbps"};
    private static final String[] bitrateValues = {"0.25", "0.5", "1", "2"};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        portNumberPref = (EditTextPreference) findPreference(KEY_PORT_PREF);
        portNumberPref.setSummary("The port on which the stream will be casted (default : 6000)");

        bitratePref = (ListPreference) findPreference(KEY_BITRATE_PREF);
        bitratePref.setEntries(bitrateOptions);
        bitratePref.setEntryValues(bitrateValues);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {

    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
}
