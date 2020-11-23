package com.zaccc.livemeeting.module;

import android.content.Context;
import android.util.Log;

import com.zaccc.livemeeting.listener.PeerConnectionEvents;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PeerConnectionClient {
    private static final String TAG = "Zaccc";
    private static final String VIDEO_TRACK_ID = "100";
    private static final String AUDIO_TRACK_ID = "101";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private VideoCapturer videoCapturer;
    private VideoTrack reuseVideoTrack;
    private AudioTrack localAudioTrack;
    private SurfaceTextureHelper surfaceTextureHelper;
    private VideoSource videoSource;
    private AudioSource audioSource;
    private MediaConstraints audioConstraints, sdpMediaConstraints;
    private PCObserver pcObserver = new PCObserver();
    private SDPObserver sdpObserver = new SDPObserver();
    private DataChannel dataChannel;
    private List<IceCandidate> queuedRemoteCandidates;

    private Boolean isError;
    private Boolean isInitiator;

    private final Context context;
    private final EglBase rootEglBase;
    private final PeerConnectionEvents events;

    public PeerConnectionClient(Context context, EglBase eglBase, PeerConnectionEvents events) {
        this.context = context;
        this.rootEglBase = eglBase;
        this.events = events;

        executorService.execute(() -> {
            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions()
            );
        });
    }

    public void createPeerConnectionFactory(PeerConnectionFactory.Options options, final VideoCapturer videoCapturer) throws IllegalAccessException {
        if (factory != null) {
            throw  new IllegalAccessException("PeerConnectionFactory has already been constructed");
        }
        this.videoCapturer = videoCapturer;

        executorService.execute(() -> createPeerConnectionFactoryInternal(options));

        executorService.execute(() -> reuseVideoTrack = createVideoTrack(videoCapturer));
    }

    private VideoTrack createVideoTrack(VideoCapturer capturer) {
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
        capturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        capturer.startCapture(480, 640, 30);
        VideoTrack track = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        track.setEnabled(true);
        events.onLocalTrackCreate(track);
        return track;
    }

    private void createPeerConnectionFactoryInternal(PeerConnectionFactory.Options options) {
        isError = false;

        final VideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), true, true);
        final VideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());

        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
        Log.i(TAG, "Create factory success.");

        videoSource = factory.createVideoSource(false);
        Log.i(TAG, "Create video source success");
    }

    public void createPeerConnection() {
        executorService.execute(() -> {
            try {
                createMediaConstraintsInternal();
                createPeerConnectionInternal();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void createMediaConstraintsInternal() {
        audioConstraints = new MediaConstraints();

        sdpMediaConstraints = new MediaConstraints();
    }

    private void createPeerConnectionInternal() {
        if (factory == null || isError) {
            Log.e(TAG, "factory is not created");
            return;
        }
        Log.i(TAG, "Start peer connection creating...");

        queuedRemoteCandidates = new ArrayList<>();

        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        rtcConfig.enableDtlsSrtp = true;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        peerConnection = factory.createPeerConnection(rtcConfig, pcObserver);
        if (peerConnection == null) {
            Log.e(TAG, "peerConnection create failed.");
            return;
        }

        isInitiator = false;

        List<String> mediaStreamLabels = Collections.singletonList("ARDAMS");
        peerConnection.addTrack(reuseVideoTrack, mediaStreamLabels);

//        peerConnection.addTrack(createAudioTrack());

        Log.i(TAG, "Peer connection created.");
    }

    private MediaStreamTrack createAudioTrack() {
        audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        localAudioTrack.setEnabled(true);
        return localAudioTrack;
    }

    public void close() {
        Log.i(TAG, "PeerConnection closing...");

        executorService.execute(this::closeInternal);

        try {
            boolean isDone = executorService.awaitTermination(3, TimeUnit.SECONDS);
            Log.d(TAG, "WebRTC close done? " + isDone);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void closeInternal() {
        if (factory != null) {
            factory.stopAecDump();
        }

        if (dataChannel != null) {
            dataChannel.dispose();
            dataChannel = null;
        }
        Log.i(TAG, "data channel is closed.");
        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }
        Log.i(TAG, "peerConnection is closed.");
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
        Log.i(TAG, "audio source is closed.");
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }
        Log.i(TAG, "video capturer is closed.");
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        Log.i(TAG, "video source is closed.");
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        Log.i(TAG, "surfaceTextureHelper is closed.");
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        Log.i(TAG, "factory is closed.");
        rootEglBase.release();

        events.onPeerConnectionClosed();

        PeerConnectionFactory.stopInternalTracingCapture();
        PeerConnectionFactory.shutdownInternalTracer();
        Log.d(TAG, "all closed.");

        executorService.shutdown();
    }

    /**
     * Create connection's signaling
     */
    public void createOffer() {
        executorService.execute(() -> {
            if (peerConnection != null && !isError) {
                Log.i(TAG, "PC create offer");
                isInitiator = true;
                peerConnection.createOffer(sdpObserver, sdpMediaConstraints);
            }
        });
    }

    public void createAnswer() {
        executorService.execute(() -> {
            if (peerConnection != null && !isError) {
                Log.i(TAG, "PC create answer");
                isInitiator = false;
                peerConnection.createAnswer(sdpObserver, sdpMediaConstraints);
            }
        });
    }

    public void setRemoteDescription(final SessionDescription desc) {
        executorService.execute(() -> {
            if (peerConnection == null || isError) {
                return;
            }
            String sdp = desc.description;
            SessionDescription sdpRemote = new SessionDescription(desc.type, sdp);
            peerConnection.setRemoteDescription(sdpObserver, sdpRemote);
        });
    }

    public void addRemoteIceCandidate(final IceCandidate candidate) {
        executorService.execute(() -> {
            if (peerConnection != null && !isError) {
                if (queuedRemoteCandidates != null) {
                    queuedRemoteCandidates.add(candidate);
                } else {
                    peerConnection.addIceCandidate(candidate);
                }
            }
        });
    }

    public void removeRemoteIceCandidates(final IceCandidate[] candidates) {
        executorService.execute(() -> {
            if (peerConnection == null || isError) {
                return;
            }

            // Drain the queued remote candidates if there is any, so that they are processed in the proper order.
            drainCandidates();
            peerConnection.removeIceCandidates(candidates);
        });
    }

    /**
     * Listener
     */
    private class PCObserver implements PeerConnection.Observer {

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.i(TAG, "Signaling State: " + signalingState);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            executorService.execute(() -> {
                Log.i(TAG, "IceConnectionState: " + iceConnectionState);
                if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                    events.onIceConnected();
                } else if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                    events.onIceDisconnected();
                } else if (iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
                    Log.e(TAG, "ICE connection failed");
                }
            });
        }

        @Override
        public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
            executorService.execute(() -> {
                Log.d(TAG, "PeerConnection State: " + newState);
                if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                    events.onConnected();
                } else if (newState == PeerConnection.PeerConnectionState.DISCONNECTED) {
                    events.onDisconnected();
                } else if (newState == PeerConnection.PeerConnectionState.FAILED) {
                    Log.e(TAG, "DTLS connection failed.");
                }
            });
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
            Log.i(TAG, "onIceConnectionReceiving changed to " + receiving);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.i(TAG, "onIceGatheringChange: " + iceGatheringState);
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            Log.i(TAG, "onIceCandidate");

            executorService.execute(() -> events.onIceCandidate(iceCandidate));
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            Log.i(TAG, "onIceCandidatesRemoved");

            executorService.execute(() -> events.onIceCandidatesRemoved(iceCandidates));
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.i(TAG, "onAddStream");

            VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
            events.onAddStream(remoteVideoTrack, rootEglBase.getEglBaseContext());
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.i(TAG, "onRemoveStream");
        }

        @Override
        public void onDataChannel(DataChannel dc) {
            Log.i(TAG, "New data channel " + dc.label());

            dc.registerObserver(new DataChannel.Observer() {
                @Override
                public void onBufferedAmountChange(long l) {
                    Log.i(TAG, "data channel buffered amount change: " + dc.label() + ": " + dc.state());
                }

                @Override
                public void onStateChange() {
                    Log.i(TAG, "onStateChange: " + dc.state());
                }

                @Override
                public void onMessage(DataChannel.Buffer buffer) {
                    Log.i(TAG, "onMessage");
                }
            });
        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }

        @Override
        public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {
            Log.i(TAG, "Selected candidate pair changed, because: " + event);
        }
    }

    private class SDPObserver implements SdpObserver {

        private SessionDescription description;

        @Override
        public void onCreateSuccess(SessionDescription desc) {
            Log.i(TAG, "onCreateSuccess " + desc.type);
            description = new SessionDescription(desc.type, desc.description);

            executorService.execute(() -> {
                Log.i(TAG, "Set local SDP from " + desc.type);
                peerConnection.setLocalDescription(this, description);
            });
        }

        @Override
        public void onSetSuccess() {
            executorService.execute(() -> {
                if (peerConnection == null || isError) {
                    return;
                }
                if (isInitiator) {
                    if (peerConnection.getRemoteDescription() == null) {
                        Log.i(TAG, "Local SDP set successfully");
                        events.onLocalDescription(description);
                    } else {
                        drainCandidates();
                    }
                } else {
                    if (peerConnection.getLocalDescription() != null) {
                        Log.i(TAG, "Local SDP set successfully");
                        events.onLocalDescription(description);
                        drainCandidates();
                    }
                }
            });
        }

        @Override
        public void onCreateFailure(String s) {
            Log.e(TAG, "Create SDP error: " + s);
        }

        @Override
        public void onSetFailure(String s) {
            Log.e(TAG, "Set SDP error: " + s);
        }
    }

    private void drainCandidates() {
        if (queuedRemoteCandidates != null) {
            Log.i(TAG, "Add " + queuedRemoteCandidates.size() + " remote candidates");
            for (IceCandidate candidate : queuedRemoteCandidates) {
                peerConnection.addIceCandidate(candidate);
            }
            queuedRemoteCandidates.clear();
            queuedRemoteCandidates = null;
        }
    }
}
