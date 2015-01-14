package in.omerjerk.remotedroid.app;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

import java.nio.ByteBuffer;

/**
 * Created by omerjerk on 21/7/14.
 */
public class CodecUtils {

    public static final int WIDTH = 1080 / 4;
    public static final int HEIGHT = 1920 / 4;

    public static final int TIMEOUT_USEC = 10000;

//    public static final String MIME_TYPE = "video/x-vnd.on2.vp8";
    public static final String MIME_TYPE = "video/avc";

    public static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    public static ByteBuffer clone(ByteBuffer original) {
        ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind();//copy from the beginning
        clone.put(original);
        original.rewind();
        clone.flip();
        return clone;
    }

    public static ByteBuffer cloneByteBuffer(final ByteBuffer original) {
        // Create clone with same capacity as original.
        final ByteBuffer clone = (original.isDirect()) ?
                ByteBuffer.allocateDirect(original.capacity()) :
                ByteBuffer.allocate(original.capacity());

        // Create a read-only copy of the original.
        // This allows reading from the original without modifying it.
        final ByteBuffer readOnlyCopy = original.asReadOnlyBuffer();

        // Flip and read from the original.
        readOnlyCopy.flip();
        clone.put(readOnlyCopy);
        clone.position(original.position());
        clone.limit(original.limit());
        clone.order(original.order());
        return clone;
    }
}
