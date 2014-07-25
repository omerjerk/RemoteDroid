package in.tosc.remotedroid.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.libcore.RequestHeaders;
import com.koushikdutta.async.http.server.AsyncHttpServer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


public class ServerActivity extends Activity {

    private MediaCodec encoder = null;

    private static final String TAG = "omerjerk";

    public static final int SERVER_PORT = 6000;

    private AsyncHttpServer server;
    private List<WebSocket> _sockets = new ArrayList<WebSocket>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        server = new AsyncHttpServer();
        server.websocket("/", null, websocketCallback);
        server.listen(SERVER_PORT);
    }

    private AsyncHttpServer.WebSocketRequestCallback websocketCallback = new AsyncHttpServer.WebSocketRequestCallback() {

        @Override
        public void onConnected(final WebSocket webSocket, RequestHeaders requestHeaders) {
            _sockets.add(webSocket);
            showToast("Someone just connected");

            //Start rendering display on the surface and setting up the encoder
            startDisplayManager();
            new Thread(new EncoderWorker()).start();
            //Use this to clean up any references to the websocket
            webSocket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    try {
                        if (ex != null)
                            ex.printStackTrace();
                    } finally {
                        _sockets.remove(webSocket);
                    }
                    showToast("Disconnected");
                }
            });

            webSocket.setStringCallback(new WebSocket.StringCallback() {
                @Override
                public void onStringAvailable(String s) {
                    Log.d(TAG, "Received string = " + s);
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

    @TargetApi(19)
    private Surface createDisplaySurface() {
        MediaFormat mMediaFormat = MediaFormat.createVideoFormat(CodecUtils.MIME_TYPE,
                CodecUtils.WIDTH, CodecUtils.HEIGHT);
        mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1000000);
        mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
        Log.i(TAG, "Starting encoder");
        encoder = MediaCodec.createEncoderByType(CodecUtils.MIME_TYPE);
        encoder.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        Surface surface = encoder.createInputSurface();

        encoder.start();
        return surface;
    }

    @TargetApi(19)
    public void startDisplayManager() {
        DisplayManager mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Surface encoderInputSurface = createDisplaySurface();
        mDisplayManager.createVirtualDisplay("Remote Droid", 720, 1280, 150, encoderInputSurface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE);
    }

    @TargetApi(19)
    private class EncoderWorker implements Runnable {

        @Override
        public void run() {
            ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();

            boolean encoderDone = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (!encoderDone) {

                int encoderStatus = encoder.dequeueOutputBuffer(info, CodecUtils.TIMEOUT_USEC);
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
                        break;
                    }
                    // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.
                    encodedData.rewind();

                    //TODO: Send the buffer over websockets to the client
                    byte[] b = new byte[info.size];

                    encodedData.get(b, 0, info.size);

                    for (WebSocket socket : _sockets) {
                        socket.send(info.offset + "," + info.size + "," +
                                info.presentationTimeUs + "," + info.flags);
                        socket.send(b);
                    }

                    encoderDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                    encoder.releaseOutputBuffer(encoderStatus, false);
                }
            }
        }
    }

    private void showToast(final String message) {
        ServerActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ServerActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
