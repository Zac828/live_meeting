package com.zaccc.livemeeting.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.fastjson.JSONObject;
import com.zaccc.livemeeting.R;
import com.zaccc.livemeeting.config.Config;
import com.zaccc.livemeeting.listener.MessageListener;
import com.zaccc.livemeeting.listener.PeerConnectionEvents;
import com.zaccc.livemeeting.module.PeerConnectionClient;
import com.zaccc.livemeeting.signaling.SignalingClient;
import com.zaccc.livemeeting.utils.JsonUtil;

import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoTrack;

public class MainActivity extends AppCompatActivity implements PeerConnectionEvents, MessageListener {

    private final static String TAG = "Zaccc";

    private PeerConnectionClient peerConnectionClient;
    private EglBase eglBase = EglBase.create();

    private LinearLayout videoRegion;
    private SurfaceViewRenderer localView;
    private TextView startBtn, userName;

    private void initView() {
        videoRegion = findViewById(R.id.video_region);

        localView = findViewById(R.id.local_view);
        localView.setMirror(true);
        localView.init(eglBase.getEglBaseContext(), null);

        startBtn = findViewById(R.id.start_btn);
        startBtn.setVisibility(View.VISIBLE);

        userName = findViewById(R.id.user_name);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

        int uid = getIntent().getIntExtra("MY_UID", -1);
        userName.setText("Welcome, \n" + uid);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.networkIgnoreMask = 0;

        try {
            peerConnectionClient = new PeerConnectionClient(this, eglBase, this);
            peerConnectionClient.createPeerConnectionFactory(options, createCameraCapturer(true));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        SignalingClient.get(this);
    }

    private VideoCapturer createCameraCapturer(boolean isFront) {
        String[] deviceNames;
        CameraEnumerator enumerator;
        if (Camera2Enumerator.isSupported(this)) {
            enumerator = new Camera2Enumerator(this);
        } else {
            enumerator = new Camera1Enumerator(false);
        }
        deviceNames = enumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (isFront ? enumerator.isFrontFacing(deviceName) : enumerator.isBackFacing(deviceName)) {
                VideoCapturer vc = enumerator.createCapturer(deviceName, null);
                if (vc != null) {
                    return vc;
                }
            }
        }

        return null;
    }

    /**
     * 開始連麥
     */
    public void startToLinkMic(View view) {
        peerJoin();
    }

    private void peerJoin() {
        try {
            peerConnectionClient.createPeerConnection();
            peerConnectionClient.createOffer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        peerConnectionClient.close();

        super.onDestroy();
    }

    /**
     * PeerConnectionEvents
     */

    @Override
    public void onLocalDescription(SessionDescription sdp) {
        Log.i(TAG, "onLocalDescription");

        String msg = JsonUtil.signaling(sdp.type.canonicalForm(), sdp.description, Config.SEND_UID);

        SignalingClient.get(this).send(msg);
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
        Log.i(TAG, "events.onIceCandidate");

        String msg = JsonUtil.candidate(candidate, Config.SEND_UID);
        SignalingClient.get(this).send(msg);
    }

    @Override
    public void onPeerConnectionClosed() {

    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {

    }

    @Override
    public void onIceConnected() {

    }

    @Override
    public void onIceDisconnected() {

    }

    @Override
    public void onConnected() {
        runOnUiThread(() -> startBtn.setVisibility(View.GONE));
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> startBtn.setVisibility(View.VISIBLE));
    }

    @Override
    public void onAddStream(VideoTrack remoteVideoTrack, EglBase.Context eglBaseContext) {
        runOnUiThread(() -> createLinkMicView(remoteVideoTrack, eglBaseContext));
    }

    @Override
    public void onLocalTrackCreate(VideoTrack localVideoTrack) {
        runOnUiThread(() -> localVideoTrack.addSink(localView));
    }

    private void createLinkMicView(VideoTrack remoteVideoTrack, EglBase.Context eglBaseContext) {
        SurfaceViewRenderer renderer = new SurfaceViewRenderer(this);
        renderer.setMirror(false);
        renderer.init(eglBaseContext, null);
        videoRegion.addView(renderer);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) renderer.getLayoutParams();
        params.width = 640;
        params.height = 960;
        renderer.setLayoutParams(params);

        videoRegion.post(() -> remoteVideoTrack.addSink(renderer));

        videoRegion.post(() -> localView.bringToFront());
    }

    /**
     * Message Listener
     */

    @Override
    public void onMessage(String message) {
        if (message != null) {
            JSONObject object = JsonUtil.parseString(message);
            if (object.containsKey(JsonUtil.CMD_TYPE) && object.getInteger(JsonUtil.CMD_TYPE) == 501) {
                if (object.containsKey("body") && object.getJSONObject("body") != null) {
                    JSONObject body = object.getJSONObject("body");

                    if (body.containsKey("type") && body.getString("type") != null) {
                        String type = body.getString("type");
                        switch (type) {
                            case "offer": {
                                String desc = body.getString("desc");

                                peerConnectionClient.createPeerConnection();
                                peerConnectionClient.setRemoteDescription(new SessionDescription(SessionDescription.Type.OFFER, desc));
                                peerConnectionClient.createAnswer();
                                break;
                            }
                            case "answer": {
                                String desc = body.getString("desc");
                                peerConnectionClient.setRemoteDescription(new SessionDescription(SessionDescription.Type.ANSWER, desc));
                                break;
                            }
                            case "candidate":
                                String candidate = body.getString("candidate");
                                String id = body.getString("id");
                                int label = body.getInteger("label");

                                peerConnectionClient.addRemoteIceCandidate(new IceCandidate(id, label, candidate));
                                break;
                        }
                    }
                }
            }
        }
    }
}