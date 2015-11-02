package in.omerjerk.remotedroid.app;

import android.os.*;
import android.os.Process;
import android.support.v4.view.InputDeviceCompat;
import android.util.Log;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;

import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by omerjerk on 5/10/15.
 */
public class Main {

    private static EventInput input;
    static Looper looper;

    public static final String TAG = "RemoteDroid_MAIN";
    public static void main(String[] args) {

        Looper.prepare();
        looper = Looper.myLooper();

        Log.d(TAG, "current process id = " + Process.myPid());
        Log.d(TAG, "current process uid = " + Process.myUid());
        try {
            input = new EventInput();
        } catch (Exception e) {
            e.printStackTrace();
        }

        AsyncHttpServer server = new AsyncHttpServer();
        server.websocket("/", null, new AsyncHttpServer.WebSocketRequestCallback() {

            @Override
            public void onConnected(WebSocket webSocket, AsyncHttpServerRequest request) {
                Log.d(TAG, "Touch client connected");
                webSocket.setClosedCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        if (ex != null) {
                            ex.printStackTrace();
                        }
                        Main.looper.quit();
                        Log.d(TAG, "Main WebSocket closed");
                    }
                });
                webSocket.setStringCallback(new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(String s) {
                        Log.d(TAG, "Received string = " + s);
                        try {
                            JSONObject touch = new JSONObject(s);
                            float x = Float.parseFloat(touch.getString("x")) * ServerService.deviceWidth;
                            float y = Float.parseFloat(touch.getString("y")) * ServerService.deviceHeight;
                            String eventType = touch.getString(ClientActivity.KEY_EVENT_TYPE);
                            if (eventType.equals(ClientActivity.KEY_FINGER_DOWN)) {
                                input.injectMotionEvent(InputDeviceCompat.SOURCE_TOUCHSCREEN, 0,
                                        SystemClock.uptimeMillis(), x, y, 1.0f);
                            } else if (eventType.equals(ClientActivity.KEY_FINGER_UP)) {
                                input.injectMotionEvent(InputDeviceCompat.SOURCE_TOUCHSCREEN, 1,
                                        SystemClock.uptimeMillis(), x, y, 1.0f);
                            } else if (eventType.equals(ClientActivity.KEY_FINGER_MOVE)) {
                                input.injectMotionEvent(InputDeviceCompat.SOURCE_TOUCHSCREEN, 2,
                                        SystemClock.uptimeMillis(), x, y, 1.0f);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
        server.listen(6059);
        Log.d(TAG, "Touch server listening at port 6059");

        if (input == null) {
            Log.e(TAG, "THIS SHIT IS NULL");
        } else {
            Log.e(TAG, "THIS SHIT NOT NULL");
        }

        Log.d(TAG, "Waiting for main to finish");
        Looper.loop();
        Log.d(TAG, "Returning from MAIN");
    }

    public static void tap(Float x, Float y) {
        Log.d(TAG, "TAP CALLED X = " + x + " Y = " + y);
        if (input == null) {
            Log.e(TAG, "EventInput object is null. Returning.");
            return;
        }
        try {
            input.injectMotionEvent(InputDeviceCompat.SOURCE_TOUCHSCREEN, 0,
                    SystemClock.uptimeMillis(), x, y, 1.0f);
            input.injectMotionEvent(InputDeviceCompat.SOURCE_TOUCHSCREEN, 1,
                    SystemClock.uptimeMillis(), x, y, 1.0f);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
