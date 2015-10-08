package in.omerjerk.remotedroid.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import eu.chainfire.libsuperuser.Shell;

/**
 * Created by omerjerk on 9/10/15.
 */
public class MainStarter {

    private static final String COMMAND = "sh -c \"CLASSPATH=%s /system/bin/app_process32 " +
            "/system/bin in.omerjerk.remotedroid.app.Main\"";

    private Context context;

    public MainStarter(Context context) {
        this.context = context;
    }

    public void start() {
        Shell.SU.run(String.format(COMMAND, getApkLocation()));
    }

    private String getApkLocation() {
        String apkLocation = null;
        PackageManager pm = context.getPackageManager();

        for (ApplicationInfo app : pm.getInstalledApplications(0)) {
            Log.d("PackageList", "package: " + app.packageName + ", sourceDir: " + app.sourceDir);
            if (app.packageName.equals(context.getPackageName())) {
                apkLocation = app.sourceDir + "/base.apk";
            }
        }
        return apkLocation;
    }
}
