package com.zaccc.livemeeting.signaling;

import android.util.Log;

import com.zaccc.livemeeting.listener.MessageListener;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class SignalingClient extends WebSocketClient {

    private static SignalingClient instance;
    private static final URI uri = URI.create("wss://ms-tp.omee.top:11443/WebSocketChatRoomServerExample01/ws_service");

    private static MessageListener listener;

    private SignalingClient() {
        super(uri);
    }

    public static SignalingClient get(MessageListener l) {
        listener = l;
        if(instance == null) {
            synchronized (SignalingClient.class) {
                if(instance == null) {
                    instance = new SignalingClient();
                }
            }
        }
        return instance;
    }

    @Override
    public void onOpen(ServerHandshake handShakeData) {
        Log.i("Zaccc", "handShakeData: " + handShakeData.toString());
    }

    @Override
    public void onMessage(String message) {
        Log.d("Zaccc", "收到 <== " + message);

        if (listener != null) {
            listener.onMessage(message);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Log.d("Zaccc", "close: " + reason + ", code: " + code + ", isRemote:" + remote);
    }

    @Override
    public void onError(Exception ex) {
        Log.e("Zaccc", "ex: " + ex.toString());
    }

    @Override
    public void send(String data) {
        Log.d("Zaccc", "送出 ==> " + data);
        super.send(data);
    }
}
