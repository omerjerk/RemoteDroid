package in.tosc.remotedroid.app;

import android.app.Activity;
import android.location.Address;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.provider.Telephony;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;

import java.nio.Buffer;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;


public class ClientActivity extends Activity implements SurfaceHolder.Callback, View.OnTouchListener{

    private static final String TAG = "omerjerk";

    SurfaceView surfaceView;

    MediaCodec decoder;
    boolean decoderConfigured = false;
    ByteBuffer[] decoderInputBuffers = null;
    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    long frameCount = 0;

    private WebSocket webSocket;

    String address;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        address = getIntent().getStringExtra(AddressInputDialog.KEY_ADDRESS_EXTRA);
        hideSystemUI();
        setContentView(R.layout.activity_client);
        surfaceView = (SurfaceView) findViewById(R.id.main_surface_view);
        surfaceView.getHolder().addCallback(this);
        surfaceView.setOnTouchListener(this);
    }

    private AsyncHttpClient.WebSocketConnectCallback websocketCallback = new AsyncHttpClient
            .WebSocketConnectCallback() {
        @Override
        public void onCompleted(final Exception ex, WebSocket webSocket) {

            if (ex != null) {
                ex.printStackTrace();
                return;
            }
            ClientActivity.this.webSocket = webSocket;
            showToast("Connection Completed");
            setTimer();
            webSocket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception e) {
                    ClientActivity.this.webSocket = null;
                    showToast("Closed");
                }
            });
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
                        Log.d(TAG, "===========Exception = " + e.getMessage() + " =================");
                        //TODO: Need to stop the decoder or to skip the current decoder loop
                       showToast(e.getMessage());
                    }

                }
            });
            webSocket.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                    if (true) {
                        ByteBuffer b = byteBufferList.getAll();
                        b.position(info.offset);
                        b.limit(info.offset + info.size);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            MediaFormat format =
                                    MediaFormat.createVideoFormat(CodecUtils.MIME_TYPE,
                                            CodecUtils.WIDTH, CodecUtils.HEIGHT);
                            format.setByteBuffer("csd-0", b);
                            decoder.configure(format, surfaceView.getHolder().getSurface(), null, 0);
                            decoder.start();
                            byteBufferList.recycle();
                            decoderInputBuffers = decoder.getInputBuffers();
                            decoderConfigured = true;
                            return;
                        }
                        int inputBufIndex = decoder.dequeueInputBuffer(CodecUtils.TIMEOUT_USEC);
                        if (inputBufIndex >= 0) {
                            ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                            inputBuf.clear();

                            Log.d(TAG, "Size = " + b.limit());
                            byte[] buff = new byte[info.size];
                            b.get(buff, 0, info.size);
                            inputBuf.clear();
                            try {
                                inputBuf.put(buff);
                            } catch (BufferOverflowException e) {
                                showToast("Buffer Overflow = " + e.getMessage());
                                e.printStackTrace();
                                byteBufferList.recycle();
                                return;
                            }

                            inputBuf.rewind();
                            decoder.queueInputBuffer(inputBufIndex, 0, info.size,
                                    info.presentationTimeUs, 0 /*flags*/);
                        }
                        int decoderStatus = decoder.dequeueOutputBuffer(info, CodecUtils.TIMEOUT_USEC);
                        if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            // no output available yet
                            Log.d(TAG, "no output from decoder available");
                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            // The storage associated with the direct ByteBuffer may already be unmapped,
                            // so attempting to access data through the old output buffer array could
                            // lead to a native crash.
                            Log.d(TAG, "decoder output buffers changed");
                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            // this happens before the first frame is returned
                            MediaFormat decoderOutputFormat = decoder.getOutputFormat();
                            Log.d(TAG, "decoder output format changed: " + decoderOutputFormat);
                        } else if (decoderStatus < 0) {
                            //TODO: fail
                            showToast("Something wrong with the decoder. Need to stop everything.");
                        } else {
                            if (info.size == 0) {
                                Log.d(TAG, "got empty frame");
                            }
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                Log.d(TAG, "output EOS");
                            }
                            boolean doRender = (info.size != 0);
                            decoder.releaseOutputBuffer(decoderStatus, doRender /*render*/);
                        }
                    }
                    byteBufferList.recycle();
                }
            });
        }
    };

    private void hideSystemUI() {
        // Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

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
        decoder = MediaCodec.createDecoderByType(CodecUtils.MIME_TYPE);
        AsyncHttpClient.getDefaultInstance().websocket("ws://" + address, null, websocketCallback);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (webSocket != null) {
            webSocket.send(motionEvent.getX() + "," + motionEvent.getY());
        }
        return false;
    }

    private void setTimer() {
        new Timer("keep_alive").scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "Sending random data");
                if (webSocket != null) {
                    webSocket.send("random,");
                }
            }
        }, 2000, 2000);
    }
}
