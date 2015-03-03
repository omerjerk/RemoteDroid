package in.omerjerk.remotedroid.app;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import in.tosc.remotedroid.R;

public class ServerService extends Service {

    private MediaCodec encoder = null;

    private static final String TAG = "omerjerk";

    private int serverPort;
    private float bitrateRatio;

    private AsyncHttpServer server;
    private List<WebSocket> _sockets = new ArrayList<WebSocket>();

    long frameCount = 0;

    Thread encoderThread = null;

    Handler mHandler;

    SharedPreferences preferences;

    int deviceWidth;
    int deviceHeight;
    Point resolution = new Point();

    private static boolean LOCAL_DEBUG = true;
    VideoWindow videoWindow = null;

    private class ToastRunnable implements Runnable {
        String mText;

        public ToastRunnable(String text) {
            mText = text;
        }

        @Override
        public void run() {
            Toast.makeText(getApplicationContext(), mText, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Main Entry Point of the server code.
     * Create a WebSocket server and start the encoder.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() == "STOP") {
            if (encoder != null)
                encoder.signalEndOfInputStream();
            server.stop();
            server = null;
            stopForeground(true);
            stopSelf();
        }
        if (server == null && intent.getAction().equals("START")) {
            preferences = PreferenceManager.getDefaultSharedPreferences(this);
            LOCAL_DEBUG = preferences.getBoolean("local_debugging", true);
            DisplayMetrics dm = new DisplayMetrics();
            Display mDisplay = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            mDisplay.getMetrics(dm);
            deviceWidth = dm.widthPixels;
            deviceHeight = dm.heightPixels;
            float resolutionRatio = Float.parseFloat(
                    preferences.getString(SettingsActivity.KEY_RESOLUTION_PREF, "0.25"));
            mDisplay.getRealSize(resolution);
            resolution.x = (int) (resolution.x * resolutionRatio);
            resolution.y = (int) (resolution.y * resolutionRatio);

            if (!LOCAL_DEBUG) {
                server = new AsyncHttpServer();
                server.websocket("/", null, websocketCallback);
                serverPort = Integer.parseInt(preferences.getString(SettingsActivity.KEY_PORT_PREF, "6000"));
                bitrateRatio = Float.parseFloat(preferences.getString(SettingsActivity.KEY_BITRATE_PREF, "1"));
                updateNotification("Streaming is live at");
                server.listen(serverPort);
            } else {
                final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);

                params.gravity = Gravity.TOP | Gravity.LEFT;
                params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                params.width = WindowManager.LayoutParams.WRAP_CONTENT;

                WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                videoWindow = (VideoWindow) inflater.inflate(R.layout.video_window, null);
                windowManager.addView(videoWindow, params);
                videoWindow.inflateSurfaceView();

                if (encoderThread == null) {
                    encoderThread = new Thread(new EncoderWorker(), "Encoder Thread");
                    encoderThread.start();
                }
            }

            mHandler = new Handler();
        }
        return START_NOT_STICKY;
    }

    private AsyncHttpServer.WebSocketRequestCallback websocketCallback = new AsyncHttpServer.WebSocketRequestCallback() {

        @Override
        public void onConnected(final WebSocket webSocket, AsyncHttpServerRequest request) {
            _sockets.add(webSocket);
            showToast("Someone just connected");
            //Start rendering display on the surface and setting up the encoder
            if (encoderThread == null) {
                startDisplayManager();
                encoderThread = new Thread(new EncoderWorker(), "Encoder Thread");
                encoderThread.start();
            }
            //Use this to clean up any references to the websocket
            webSocket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    try {
                        if (ex != null)
                            ex.printStackTrace();
                    } finally {
                        _sockets.clear();
                        showToast("Removed");
                    }
                    showToast("Disconnected");
                }
            });

            webSocket.setStringCallback(new WebSocket.StringCallback() {
                @Override
                public void onStringAvailable(String s) {
                    String[] parts = s.split(",");
                    if (parts.length < 2) {
                        return;
                    }
                    try {
                        float x = Float.parseFloat(parts[0]) * deviceWidth;
                        float y = Float.parseFloat(parts[1]) * deviceHeight;
                        Process su = null;
                        DataOutputStream outputStream = null;
                        try {
                            su = Runtime.getRuntime().exec("su");
                            outputStream = new DataOutputStream(su.getOutputStream());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        //outputStream.flush();
                        outputStream.writeBytes("input tap " + x + " " + y + "\n");
                        outputStream.flush();
                        outputStream.writeBytes("exit\n");
                        outputStream.flush();
                        su.waitFor();
                        Log.d(TAG, "Execution using su = " + "input tap " + x + " " + y);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            webSocket.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                    byteBufferList.recycle();
                }
            });
        }
    };

    /**
     * Create the display surface out of the encoder. The data to encoder will be fed from this
     * Surface itself.
     * @return
     * @throws IOException
     */
    @TargetApi(19)
    private Surface createDisplaySurface() throws IOException {
        MediaFormat mMediaFormat = MediaFormat.createVideoFormat(CodecUtils.MIME_TYPE,
                CodecUtils.WIDTH, CodecUtils.HEIGHT);
        mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, (int) (1024 * 1024 * 0.5));
        mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        Log.i(TAG, "Starting encoder");
        encoder = MediaCodec.createEncoderByType(CodecUtils.MIME_TYPE);
        encoder.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        Surface surface = encoder.createInputSurface();
        return surface;
    }

    @TargetApi(19)
    public void startDisplayManager() {
        DisplayManager mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Surface encoderInputSurface = null;
        try {
            encoderInputSurface = createDisplaySurface();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mDisplayManager.createVirtualDisplay("Remote Droid", CodecUtils.WIDTH, CodecUtils.HEIGHT, 50,
                encoderInputSurface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE);
        encoder.start();
    }

    @TargetApi(19)
    private class EncoderWorker implements Runnable {

        @Override
        public void run() {
            startDisplayManager();
            ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();

            boolean encoderDone = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            String infoString;
            while (!encoderDone) {
                int encoderStatus;
                try {
                    encoderStatus = encoder.dequeueOutputBuffer(info, CodecUtils.TIMEOUT_USEC);
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                    break;
                }

                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    //Log.d(TAG, "no output from encoder available");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an encoder
                    encoderOutputBuffers = encoder.getOutputBuffers();
                    Log.d(TAG, "encoder output buffers changed");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // not expected for an encoder
                    MediaFormat newFormat = encoder.getOutputFormat();
                    Log.d(TAG, "encoder output format changed: " + newFormat);
                } else if (encoderStatus < 0) {
                    break;
                } else {
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        Log.d(TAG, "============It's NULL. BREAK!=============");
                        return;
                    }
                    if (!LOCAL_DEBUG) {
                        for (WebSocket socket : _sockets) {
                            infoString = info.offset + "," + info.size + "," +
                                    info.presentationTimeUs + "," + info.flags;
                            socket.send(infoString.getBytes());

                            byte[] b = new byte[info.size];
                            try {
                                if (info.size != 0) {
                                    encodedData.limit(info.offset + info.size);
                                    encodedData.position(info.offset);
                                    encodedData.get(b, info.offset, info.offset + info.size);
                                    socket.send(b);
                                }

                            } catch (BufferUnderflowException e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        if (info.size != 0) {
                            encodedData.position(info.offset);
                            encodedData.limit(info.offset + info.size);
                        }
                        videoWindow.setData(CodecUtils.clone(encodedData), info);

                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            Log.w(TAG, "config flag received");
                        }
                    }

                    encoderDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                    try {
                        encoder.releaseOutputBuffer(encoderStatus, false);
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                    }
                }

            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (encoder != null) {
            encoder.stop();
            encoder.release();
            encoder = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showToast(final String message) {
        mHandler.post(new ToastRunnable(message));
    }

    /**
     * Display the notification
     * @param message
     */
    private void updateNotification(String message) {
        Intent intent = new Intent(this, ServerService.class);
        intent.setAction("STOP");
        PendingIntent stopServiceIntent = PendingIntent.getService(this, 0, intent, 0);
        Notification.Builder mBuilder =
                new Notification.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setOngoing(true)
                        .addAction(R.drawable.ic_media_stop, "Stop", stopServiceIntent)
                        .setContentTitle(message)
                        .setContentText(Utils.getIPAddress(true) + ":" + serverPort);
        startForeground(6000, mBuilder.build());
    }
}
