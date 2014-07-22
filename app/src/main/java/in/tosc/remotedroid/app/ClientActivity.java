package in.tosc.remotedroid.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import com.koushikdutta.async.AsyncSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.http.socketio.*;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.socketio.transport.SocketIOTransport;
import org.json.JSONArray;
import org.json.JSONObject;


public class ClientActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);
        SocketIOClient.connect(AsyncHttpClient.getDefaultInstance(), "http://192.168.1.2:3000", connectCallback);
    }

    private ConnectCallback connectCallback = new ConnectCallback() {
        @Override
        public void onConnectCompleted(Exception e, SocketIOClient socketIOClient) {
            if (e != null) {
                e.printStackTrace();
                return;
            }

            socketIOClient.setStringCallback(new StringCallback() {
                @Override
                public void onString(String s, Acknowledge acknowledge) {

                }
            });
        }
    };
}
