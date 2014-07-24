package in.tosc.remotedroid.app;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;

import java.nio.ByteBuffer;


public class ClientActivity extends Activity implements SurfaceHolder.Callback{

    private static final String TAG = "omerjerk";

    SurfaceView surfaceView;

    MediaCodec decoder;
    boolean decoderConfigured = false;
    ByteBuffer[] decoderInputBuffers = null;
    ByteBuffer[] decoderOutputBuffers = null;
    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);
        surfaceView = (SurfaceView) findViewById(R.id.main_surface_view);
        surfaceView.getHolder().addCallback(this);
        AsyncHttpClient.getDefaultInstance().websocket("ws://192.168.43.1:6000", null, websocketCallback);
    }

    private AsyncHttpClient.WebSocketConnectCallback websocketCallback = new AsyncHttpClient
            .WebSocketConnectCallback() {
        @Override
        public void onCompleted(Exception ex, WebSocket webSocket) {

            if (ex != null) {
                ex.printStackTrace();
                return;
            }
            showToast("Connection Completed");
            webSocket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception e) {
                    showToast("Closed");
                }
            });
            webSocket.send("a string");
            webSocket.send(new byte[10]);
            webSocket.setStringCallback(new WebSocket.StringCallback() {
                public void onStringAvailable(String s) {
                    String[] parts = s.split(",");
                    try {
                        info.set(Integer.parseInt(parts[0]),
                                Integer.parseInt(parts[1]),
                                Long.parseLong(parts[2]),
                                Integer.parseInt(parts[3]));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                        //TODO: Need to stop the decoder or to skip the current decoder loop
                        showToast(e.getMessage());
                    }

                }
            });
            webSocket.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                    byteBufferList.recycle();
                    if (decoderConfigured) {
                        int inputBufIndex = decoder.dequeueInputBuffer(-1);
                        ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                        inputBuf.clear();
                        inputBuf.put(byteBufferList.getAll());
                        decoder.queueInputBuffer(inputBufIndex, 0, info.size,
                                info.presentationTimeUs, info.flags);
                        //encoderDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        int decoderStatus = decoder.dequeueOutputBuffer(info, CodecUtils.TIMEOUT_USEC);
                        if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            // no output available yet
                            Log.d(TAG, "no output from decoder available");
                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            // The storage associated with the direct ByteBuffer may already be unmapped,
                            // so attempting to access data through the old output buffer array could
                            // lead to a native crash.
                            Log.d(TAG, "decoder output buffers changed");
                            decoderOutputBuffers = decoder.getOutputBuffers();
                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            // this happens before the first frame is returned
                            MediaFormat decoderOutputFormat = decoder.getOutputFormat();
                            Log.d(TAG, "decoder output format changed: " + decoderOutputFormat);
                        } else if (decoderStatus < 0) {
                            //TODO: fail
                        } else {
                            if (info.size == 0) {
                                Log.d(TAG, "got empty frame");
                            }
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                Log.d(TAG, "output EOS");
                            }
                            decoder.releaseOutputBuffer(decoderStatus, true /*render*/);
                        }
                    }
                }
            });
        }
    };

    private void showToast(final String message) {
        ClientActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ClientActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        MediaFormat format = MediaFormat.createVideoFormat(CodecUtils.MIME_TYPE, 720, 1280);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 1000000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
        decoder.configure(format, surfaceHolder.getSurface(), null, 0);
        decoder.start();
        decoderInputBuffers = decoder.getInputBuffers();
        decoderOutputBuffers = decoder.getOutputBuffers();
        decoderConfigured = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }
}
