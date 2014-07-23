package in.tosc.remotedroid.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;


public class ClientActivity extends Activity {

    private static final String TAG = "omerjerk";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);
        AsyncHttpClient.getDefaultInstance().websocket("ws://192.168.43.1:6000", "ws", websocketCallback);

    }

    private AsyncHttpClient.WebSocketConnectCallback websocketCallback = new AsyncHttpClient
            .WebSocketConnectCallback() {
        @Override
        public void onCompleted(Exception ex, WebSocket webSocket) {
            Log.d(TAG, "Connection completed!");
            if (ex != null) {
                ex.printStackTrace();
                return;
            }
            webSocket.send("a string");
            webSocket.send(new byte[10]);
            webSocket.setStringCallback(new WebSocket.StringCallback() {
                public void onStringAvailable(String s) {
                    System.out.println("I got a string: " + s);
                }
            });
            webSocket.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                    Log.d(TAG, "I got some bytes = " + byteBufferList.toString());
                }
            });
        }
    };
}
