package in.tosc.remotedroid.app;

import android.app.Activity;
import android.media.MediaCodec;
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
    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);
        surfaceView = (SurfaceView) findViewById(R.id.main_surface_view);
        surfaceView.getHolder().addCallback(this);

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
                            inputBuf.put(buff);
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
        AsyncHttpClient.getDefaultInstance().websocket("ws://192.168.43.1:6000", null, websocketCallback);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
    }
}
