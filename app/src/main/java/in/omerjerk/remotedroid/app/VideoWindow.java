package in.omerjerk.remotedroid.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.LinearLayout;

import com.android.grafika.CircularEncoderBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import in.tosc.remotedroid.R;

/**
 * Created by omerjerk on 13/1/15.
 */
@SuppressLint("NewApi")
public class VideoWindow extends LinearLayout implements SurfaceHolder.Callback{

    LayoutInflater mInflater;

    SurfaceView surfaceView;

    private int mWidth = CodecUtils.WIDTH;
    private int mHeight = CodecUtils.HEIGHT;

    MediaCodec decoder;
    ByteBuffer[] decoderInputBuffers = null;
    ByteBuffer[] decoderOutputBuffers = null;
    MediaFormat decoderOutputFormat = null;
    boolean decoderConfigured = false;

    CircularEncoderBuffer encBuffer;

    private static final String TAG = "VideoWindow";

    private Object mLock = new Object();

    private boolean firstIFrameAdded = false;

    public VideoWindow(Context context) {
        super(context);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public VideoWindow (Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VideoWindow (Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void inflateSurfaceView() {
        surfaceView = (SurfaceView) findViewById(R.id.demo_surface_view);
        surfaceView.getHolder().addCallback(this);
        encBuffer = new CircularEncoderBuffer((int)(1024 * 1024 * 0.5), 30, 7);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            decoder = MediaCodec.createDecoderByType(CodecUtils.MIME_TYPE);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    doDecoderThingie();
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    public void doDecoderThingie() {
        boolean outputDone = false;

        while(!decoderConfigured) {
        }

        Log.d(TAG, "Decoder Configured");

        while(!firstIFrameAdded) {}

        int index = encBuffer.getFirstIndex();
        if (index < 0) {
            Log.e(TAG, "CircularBuffer Error");
            return;
        }
        ByteBuffer encodedFrames;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (!outputDone) {
//            Log.d(TAG, "Index = " + index);
            encodedFrames = encBuffer.getChunk(index, info);
            Log.d(TAG, "frame = " + encodedFrames);
            encodedFrames.limit(info.size + info.offset);
            encodedFrames.position(info.offset);
            Log.d(TAG, "updated frame = " + encodedFrames);
//            Log.d(TAG, "time = " + info.presentationTimeUs);

            try {
                index = encBuffer.getNextIntCustom(index);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            int inputBufIndex = decoder.dequeueInputBuffer(-1);
            if (inputBufIndex >= 0) {
                ByteBuffer inputBuf = decoder.getInputBuffer(inputBufIndex);
                inputBuf.clear();
                inputBuf.put(encodedFrames);
                decoder.queueInputBuffer(inputBufIndex, 0, info.size,
                        info.presentationTimeUs, info.flags);
            }

            if (decoderConfigured) {
                int decoderStatus = decoder.dequeueOutputBuffer(info, CodecUtils.TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
//                    Log.d(TAG, "no output from decoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // The storage associated with the direct ByteBuffer may already be unmapped,
                    // so attempting to access data through the old output buffer array could
                    // lead to a native crash.
                    Log.d(TAG, "decoder output buffers changed");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // this happens before the first frame is returned
                    decoderOutputFormat = decoder.getOutputFormat();
                    Log.d(TAG, "decoder output format changed: " +
                            decoderOutputFormat);
                } else { // decoderStatus >= 0
                    boolean doRender = (info.size != 0);
                    // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                    // to SurfaceTexture to convert to a texture.  The API doesn't guarantee
                    // that the texture will be available before the call returns, so we
                    // need to wait for the onFrameAvailable callback to fire.
                    decoder.releaseOutputBuffer(decoderStatus, true);
//                    Log.d(TAG, "Rendering");
                }
            }
        }
    }

    public void setData(ByteBuffer encodedFrames, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            Log.d(TAG, "Configuring Decoder");
            MediaFormat format =
                    MediaFormat.createVideoFormat(CodecUtils.MIME_TYPE, mWidth, mHeight);
            format.setByteBuffer("csd-0", encodedFrames);
//                format.setInteger(MediaFormat.KEY_BIT_RATE, (int) (1024 * 1024 * 0.5));
//                format.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
//                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
//                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            decoder.configure(format, surfaceView.getHolder().getSurface(),
                    null, 0);
            decoder.start();
            decoderInputBuffers = decoder.getInputBuffers();
            decoderOutputBuffers = decoder.getOutputBuffers();
            decoderConfigured = true;
            Log.d(TAG, "decoder configured (" + info.size + " bytes)");
            return;
        }

        encBuffer.add(encodedFrames, info.flags, info.presentationTimeUs);
        if ((info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
            firstIFrameAdded = true;
//            Log.d(TAG, "Sync frame received");
        }
    }
}
