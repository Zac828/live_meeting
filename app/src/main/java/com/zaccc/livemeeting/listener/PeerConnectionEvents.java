package com.zaccc.livemeeting.listener;

import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtc.VideoTrack;

public interface PeerConnectionEvents {
    /**
     * Once local SDP is created and set.
     */
    void onLocalDescription(final SessionDescription sdp);
    /**
     * Once Ice candidate is generated.
     */
    void onIceCandidate(final IceCandidate candidate);
    /**
     * Once peer connection is closed
     */
    void onPeerConnectionClosed();
    /**
     * Once ICE candidates are removed.
     */
    void onIceCandidatesRemoved(final IceCandidate[] candidates);
    /**
     * Once connection is established (IceConnectionState is CONNECTED)
     */
    void onIceConnected();
    /**
     * Once connection is disconnected (IceConnectionState is DISCONNECTED).
     */
    void onIceDisconnected();
    /**
     * Once DTLS connection is established (PeerConnectionState is CONNECTED).
     */
    void onConnected();
    /**
     * Once DTLS connection is disconnected (PeerConnectionState is DISCONNECTED).
     */
    void onDisconnected();
    /**
     * 加入遠端畫面
     */
    void onAddStream(VideoTrack remoteVideoTrack, EglBase.Context eglBaseContext);
    /**
     * 加入本地端畫面
     */
    void onLocalTrackCreate(VideoTrack localVideoTrack);
}
