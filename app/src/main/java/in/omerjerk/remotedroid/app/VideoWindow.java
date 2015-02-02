package in.omerjerk.remotedroid.app;

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

import in.tosc.remotedroid.R;

/**
 * Created by root on 13/1/15.
 */
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
        encBuffer = new CircularEncoderBuffer((int)(1024 * 1024 * 0.5), 15, 5);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            decoder = MediaCodec.createDecoderByType(CodecUtils.MIME_TYPE);
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

        int index = encBuffer.getFirstIndex();
        if (index < 0) {
            Log.e(TAG, "CircularBuffer Error");
            return;
        }
        ByteBuffer encodedFrames;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (!outputDone) {
            encodedFrames = encBuffer.getChunk(index, info);
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
            } else {
                Log.d(TAG, "Decoder not configured. Skipping");
                if (!decoderConfigured)
                    continue;
                int inputBufIndex = decoder.dequeueInputBuffer(-1);
                ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                inputBuf.clear();
                inputBuf.put(encodedFrames);
                decoder.queueInputBuffer(inputBufIndex, 0, info.size,
                        info.presentationTimeUs, info.flags);
            }
            if (decoderConfigured) {
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
                    decoderOutputFormat = decoder.getOutputFormat();
                    Log.d(TAG, "decoder output format changed: " +
                            decoderOutputFormat);
                } else if (decoderStatus < 0) {
                    break;
                } else {  // decoderStatus >= 0
                    boolean doRender = (info.size != 0);
                    // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                    // to SurfaceTexture to convert to a texture.  The API doesn't guarantee
                    // that the texture will be available before the call returns, so we
                    // need to wait for the onFrameAvailable callback to fire.
                    decoder.releaseOutputBuffer(decoderStatus, doRender);
                }
            }
        }
    }

    public void setData(ByteBuffer encodedFrames, MediaCodec.BufferInfo info) {
        encBuffer.add(encodedFrames, info.flags, info.presentationTimeUs);
    }
}
