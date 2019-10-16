package org.kurento.tutorial.rtpreceiver;

import com.google.gson.JsonObject;
import org.kurento.client.BaseRtpEndpoint;
import org.kurento.client.ConnectionStateChangedEvent;
import org.kurento.client.CryptoSuite;
import org.kurento.client.ErrorEvent;
import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.IceCandidateFoundEvent;
import org.kurento.client.IceComponentStateChangeEvent;
import org.kurento.client.IceGatheringDoneEvent;
import org.kurento.client.MediaFlowInStateChangeEvent;
import org.kurento.client.MediaFlowOutStateChangeEvent;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaState;
import org.kurento.client.MediaStateChangedEvent;
import org.kurento.client.MediaTranscodingStateChangeEvent;
import org.kurento.client.MediaType;
import org.kurento.client.NewCandidatePairSelectedEvent;
import org.kurento.client.PausedEvent;
import org.kurento.client.PlayerEndpoint;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.RecordingEvent;
import org.kurento.client.RtpEndpoint;
import org.kurento.client.SDES;
import org.kurento.client.StoppedEvent;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class EndpointUtils {

  private final Logger log = LoggerFactory.getLogger(EndpointUtils.class);

  public void addWebRtpListeners(WebRtcEndpoint webRtcEp) {
    webRtcEp.addMediaStateChangedListener(new EventListener<MediaStateChangedEvent>() {
      @Override
      public void onEvent(MediaStateChangedEvent event) {
        if ((event.getOldState() != event.getNewState()) && (MediaState.CONNECTED == event.getNewState())) {
          log.info("RECORD: webRtcEp connected");
        }
        if ((event.getOldState() != event.getNewState()) && (MediaState.DISCONNECTED == event.getNewState())) {
          log.info("RECORD: webRtcEp DISCONNECTED");
        }
      }
    });
  }

  public void addRtpListeners(RtpEndpoint rtpEp) {
    rtpEp.addMediaFlowInStateChangeListener(new EventListener<MediaFlowInStateChangeEvent>() {
      @Override
      public void onEvent(MediaFlowInStateChangeEvent event) {
        log.info("RECORD: RTP sink flow in state change: " + event.getType() + "::" + event.getPadName() + ":: " + event.getMediaType() + ":: " + event.getState());

      }
    });

    rtpEp.addMediaStateChangedListener(new EventListener<MediaStateChangedEvent>() {
      @Override
      public void onEvent(MediaStateChangedEvent event) {
        log.info("RECORD:  RTP sink media state change - new:" + event.getNewState() + "- old:" + event.getOldState());


        log.info("RECORD: max output bit rate: " + rtpEp.getMaxOutputBitrate());
        log.info("RECORD: min output bit rate: " + rtpEp.getMinOutputBitrate());
        log.info("RECORD: max video recv bw: " + rtpEp.getMaxVideoRecvBandwidth());
        log.info("RECORD: min video recv bw: " + rtpEp.getMinVideoRecvBandwidth());
        log.info("RECORD: max video send bw: " + rtpEp.getMaxVideoSendBandwidth());
        log.info("RECORD: min video send bw: " + rtpEp.getMinVideoSendBandwidth());
        if (MediaState.CONNECTED == event.getNewState()) {
        }
        if (MediaState.DISCONNECTED == event.getNewState()) {
        }
      }
    });
  }

  public void addPlayerListeners(PlayerEndpoint playerEp) {
    playerEp.addMediaFlowInStateChangeListener(new EventListener<MediaFlowInStateChangeEvent>() {
      @Override
      public void onEvent(MediaFlowInStateChangeEvent event) {
        log.info("RECORD: Player sink flow in state change: " + event.getType() + "::" + event.getPadName() + ":: " + event.getMediaType() + ":: " + event.getState());
        log.info("RECORD: Player max output bit rate: " + playerEp.getMaxOutputBitrate());
        log.info("RECORD: Player min output bit rate: " + playerEp.getMinOutputBitrate());
      }
    });

    playerEp.addErrorListener(event -> {
      log.info("RECORD: player error" + event.getErrorCode() + event.getDescription() + event.getType());
    });

    playerEp.addMediaFlowOutStateChangeListener(event -> {
      log.info("RECORD: Player sink flow out state change: " + event.getType() + "::" + event.getPadName() + ":: " + event.getMediaType() + ":: " + event.getState());
    });
    playerEp.addElementConnectedListener(event -> {
      log.info("RECORD: Player sink element connected: " + event.getType() + "::" + event.getMediaType() + ":: " + event.getSinkMediaDescription());
    });

    playerEp.addMediaTranscodingStateChangeListener(event -> {
      log.info("RECORD: Player TRANSCODING: " + event.getType() + "::" + event.getMediaType() + ":: " + event.getState() + "::" + event.getBinName());
    });
  }

  public void addRecorderListeners(RecorderEndpoint recorder) {

    recorder.addRecordingListener(new EventListener<RecordingEvent>() {
      @Override
      public void onEvent(RecordingEvent event) {
        log.info("RECORD: on event: " + event.getType());
        if (recorder.isMediaFlowingIn(MediaType.VIDEO)) {
          log.info("RECORD: recorder video flowing in: max output bitrate" + recorder.getMaxOutputBitrate());
          log.info("RECORD: recorder video flowing in: min output bitrate" + recorder.getMinOutputBitrate());
        }
      }
    });

    recorder.addStoppedListener(new EventListener<StoppedEvent>() {
      @Override
      public void onEvent(StoppedEvent event) {
        log.info("RECORD: stopped");
      }
    });

    recorder.addPausedListener(new EventListener<PausedEvent>() {
      @Override
      public void onEvent(PausedEvent event) {
        log.info("RECORD: paused");
      }
    });

    recorder.addErrorListener(new EventListener<ErrorEvent>() {
      @Override
      public void onEvent(ErrorEvent event) {
        log.info("error in recorder: " + event.getType() + "::" + event.getDescription() + ":: " + event.getErrorCode());
      }
    });

    recorder.addMediaTranscodingStateChangeListener(event -> {
      log.info("RECORD: recorder TRANSCODING: " + event.getType() + "::" + event.getMediaType() + ":: " + event.getState());
    });

  }
  // ADD_ICE_CANDIDATE ---------------------------------------------------------

  public void handleAddIceCandidate(final WebSocketSession session,
                                    JsonObject jsonMessage, ConcurrentHashMap<String, UserSession> users) {
    String sessionId = session.getId();
    UserSession user = users.get(sessionId);

    if (user != null) {
      JsonObject jsonCandidate = jsonMessage.get("candidate").getAsJsonObject();
      IceCandidate candidate =
          new IceCandidate(jsonCandidate.get("candidate").getAsString(),
              jsonCandidate.get("sdpMid").getAsString(),
              jsonCandidate.get("sdpMLineIndex").getAsInt());

      WebRtcEndpoint webRtcEp = user.getWebRtcEp();
      webRtcEp.addIceCandidate(candidate);
    }
  }


  public void addWebRtcEventListeners(final WebSocketSession session,
                                      final WebRtcEndpoint webRtcEp) {
    log.info("[Handler::addWebRtcEventListeners] name: {}, sessionId: {}",
        webRtcEp.getName(), session.getId());

    // Event: The ICE backend found a local candidate during Trickle ICE
    webRtcEp.addIceCandidateFoundListener(
        new EventListener<IceCandidateFoundEvent>() {
          @Override
          public void onEvent(IceCandidateFoundEvent ev) {
            log.debug("[WebRtcEndpoint::{}] source: {}, timestamp: {}, tags: {}, candidate: {}",
                ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                ev.getTags(), JsonUtils.toJsonObject(ev.getCandidate()));

            JsonObject message = new JsonObject();
            message.addProperty("id", "ADD_ICE_CANDIDATE");
            message.addProperty("webRtcEpId", webRtcEp.getId());
            message.add("candidate", JsonUtils.toJsonObject(ev.getCandidate()));
            sendMessage(session, message.toString());
          }
        });

    // Event: The ICE backend changed state
    webRtcEp.addIceComponentStateChangeListener(
        new EventListener<IceComponentStateChangeEvent>() {
          @Override
          public void onEvent(IceComponentStateChangeEvent ev) {
            log.debug("[WebRtcEndpoint::{}] source: {}, timestamp: {}, tags: {}, streamId: {}, componentId: {}, " +
                    "state: {}",
                ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                ev.getTags(), ev.getStreamId(), ev.getComponentId(), ev.getState());
          }
        });

    // Event: The ICE backend finished gathering ICE candidates
    webRtcEp.addIceGatheringDoneListener(
        new EventListener<IceGatheringDoneEvent>() {
          @Override
          public void onEvent(IceGatheringDoneEvent ev) {
            log.debug("[WebRtcEndpoint::{}] source: {}, timestamp: {}, tags: {}",
                ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                ev.getTags());
          }
        });

    // Event: The ICE backend selected a new pair of ICE candidates for use
    webRtcEp.addNewCandidatePairSelectedListener(
        new EventListener<NewCandidatePairSelectedEvent>() {
          @Override
          public void onEvent(NewCandidatePairSelectedEvent ev) {
            log.info("[WebRtcEndpoint::{}] name: {}, timestamp: {}, tags: {}, streamId: {}, local: {}, remote: {}",
                ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                ev.getTags(), ev.getCandidatePair().getStreamID(),
                ev.getCandidatePair().getLocalCandidate(),
                ev.getCandidatePair().getRemoteCandidate());
          }
        });


    webRtcEp.addMediaTranscodingStateChangeListener(event -> {
      log.error("RECORD: WEB-RTC EP TRANSCODING: " + event.getType() + "::" + event.getMediaType() + ":: " + event.getState());
    });

  }

  public void addBaseEventListeners(final WebSocketSession session,
                                    BaseRtpEndpoint baseRtpEp, final String className) {
    log.info("[Handler::addBaseEventListeners] name: {}, class: {}, sessionId: {}",
        baseRtpEp.getName(), className, session.getId());

    // Event: Some error happened
    baseRtpEp.addErrorListener(new EventListener<ErrorEvent>() {
      @Override
      public void onEvent(ErrorEvent ev) {
        log.error("[{}::{}] source: {}, timestamp: {}, tags: {}, description: {}, errorCode: {}",
            className, ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
            ev.getTags(), ev.getDescription(), ev.getErrorCode());
//        stop(session);
        //todo: fix above - stop session completely
      }
    });

    // Event: Media is flowing into this sink
    baseRtpEp.addMediaFlowInStateChangeListener(
        new EventListener<MediaFlowInStateChangeEvent>() {
          @Override
          public void onEvent(MediaFlowInStateChangeEvent ev) {
            log.info("[{}::{}] source: {}, timestamp: {}, tags: {}, state: {}, padName: {}, mediaType: {}",
                className, ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                ev.getTags(), ev.getState(), ev.getPadName(), ev.getMediaType());
          }
        });

    // Event: Media is flowing out of this source
    baseRtpEp.addMediaFlowOutStateChangeListener(
        new EventListener<MediaFlowOutStateChangeEvent>() {
          @Override
          public void onEvent(MediaFlowOutStateChangeEvent ev) {
            log.info("[{}::{}] source: {}, timestamp: {}, tags: {}, state: {}, padName: {}, mediaType: {}",
                className, ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                ev.getTags(), ev.getState(), ev.getPadName(), ev.getMediaType());
          }
        });

    // Event: [TODO write meaning of this event]
    baseRtpEp.addConnectionStateChangedListener(
        new EventListener<ConnectionStateChangedEvent>() {
          @Override
          public void onEvent(ConnectionStateChangedEvent ev) {
            log.info("[{}::{}] source: {}, timestamp: {}, tags: {}, oldState: {}, newState: {}",
                className, ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                ev.getTags(), ev.getOldState(), ev.getNewState());
          }
        });

    // Event: [TODO write meaning of this event]
    baseRtpEp.addMediaStateChangedListener(
        new EventListener<MediaStateChangedEvent>() {
          @Override
          public void onEvent(MediaStateChangedEvent ev) {
            log.info("[{}::{}] source: {}, timestamp: {}, tags: {}, oldState: {}, newState: {}",
                className, ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                ev.getTags(), ev.getOldState(), ev.getNewState());
          }
        });

    // Event: This element will (or will not) perform media transcoding
    baseRtpEp.addMediaTranscodingStateChangeListener(
        new EventListener<MediaTranscodingStateChangeEvent>() {
          @Override
          public void onEvent(MediaTranscodingStateChangeEvent ev) {
            log.info("[{}::{}] source: {}, timestamp: {}, tags: {}, state: {}, binName: {}, mediaType: {}",
                className, ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                ev.getTags(), ev.getState(), ev.getBinName(), ev.getMediaType());
          }
        });
  }


  public synchronized void sendMessage(final WebSocketSession session,
                                       String message) {
    if (!session.isOpen()) {
      log.error("[Handler::sendMessage] WebSocket session is closed");
      return;
    }

    try {
      session.sendMessage(new TextMessage(message));
    } catch (IOException ex) {
      log.error("[Handler::sendMessage] Exception: {}", ex.getMessage());
    }
  }


  public void startWebRtcEndpoint(WebRtcEndpoint webRtcEp) {
    // Calling gatherCandidates() is when the Endpoint actually starts working.
    // In this tutorial, this is emphasized for demonstration purposes by
    // leaving the ICE candidate gathering in its own method.
    webRtcEp.gatherCandidates();
  }

  public RtpEndpoint makeRtpEndpoint(MediaPipeline pipeline, Boolean useSrtp) {
    if (!useSrtp) {
      return new RtpEndpoint.Builder(pipeline).build();
    }

    // ---- SRTP configuration BEGIN ----
    // This is used by KMS to encrypt its SRTP/SRTCP packets.
    // Encryption key used by receiver (ASCII): "4321ZYXWVUTSRQPONMLKJIHGFEDCBA"
    // In Base64: "NDMyMVpZWFdWVVRTUlFQT05NTEtKSUhHRkVEQ0JB"
    CryptoSuite srtpCrypto = CryptoSuite.AES_128_CM_HMAC_SHA1_80;
    // CryptoSuite crypto = CryptoSuite.AES_256_CM_HMAC_SHA1_80;

    // You can provide the SRTP Master Key in either plain text or Base64.
    // The second form allows providing binary, non-ASCII keys.
    String srtpMasterKeyAscii = "4321ZYXWVUTSRQPONMLKJIHGFEDCBA";
    // String srtpMasterKeyBase64 = "NDMyMVpZWFdWVVRTUlFQT05NTEtKSUhHRkVEQ0JB";
    // ---- SRTP configuration END ----

    SDES sdes = new SDES();
    sdes.setCrypto(srtpCrypto);
    sdes.setKey(srtpMasterKeyAscii);
    // sdes.setKeyBase64(srtpMasterKeyBase64);

    return new RtpEndpoint.Builder(pipeline).withCrypto(sdes).build();
  }

  public PlayerEndpoint makePlayerEndpoint(MediaPipeline pipeline, Boolean useSrtp) {

//    PlayerEndpoint player = new PlayerEndpoint.Builder(pipeline, "rtsp://localhost:5004/").build();
    PlayerEndpoint player = new PlayerEndpoint.Builder(pipeline, "rtsp://172.30.0.194:8080/video/h264").build();
//    PlayerEndpoint player = new PlayerEndpoint.Builder(pipeline, "http://172.30.0.194:8080/video").build();

//    PlayerEndpoint player = new PlayerEndpoint.Builder(pipeline, "http://172.30.0.194:8080/video/mjpeg").build();
//    PlayerEndpoint player = new PlayerEndpoint.Builder(pipeline, "http://192.168.178.165:8080/video").build();

    return player;

//      return new RtpEndpoint.Builder(pipeline).build();
  }


  // STOP ----------------------------------------------------------------------

  public void sendPlayEnd(final WebSocketSession session, ConcurrentHashMap<String, UserSession> users) {
    if (users.containsKey(session.getId())) {
      JsonObject message = new JsonObject();
      message.addProperty("id", "END_PLAYBACK");
      sendMessage(session, message.toString());
    }
  }

  public void stop(final WebSocketSession session, ConcurrentHashMap<String, UserSession> users) {
    log.info("[Handler::stop]");

    // Update the UI
    sendPlayEnd(session, users);

    // Remove the user session and release all resources
    String sessionId = session.getId();
    UserSession user = users.remove(sessionId);
    if (user != null) {
      MediaPipeline mediaPipeline = user.getMediaPipeline();
      if (mediaPipeline != null) {
        log.info("[Handler::stop] Release the Media Pipeline");
        mediaPipeline.release();
      }
    }
  }

  public void handleStop(final WebSocketSession session,
                         JsonObject jsonMessage, ConcurrentHashMap<String, UserSession> users) {
    stop(session, users);
  }

  //todo: connect with frontend
  public void handleStartRec(final WebSocketSession session,
                             JsonObject jsonMessage, ConcurrentHashMap<String, UserSession> users) {
    String sessionId = session.getId();
    UserSession user = users.get(sessionId);
    user.getRecorder().record();
  }

  public void handleStopRec(final WebSocketSession session,
                            JsonObject jsonMessage, ConcurrentHashMap<String, UserSession> users) {
    String sessionId = session.getId();
    UserSession user = users.get(sessionId);
    user.getRecorder().stop();
  }

  // ---------------------------------------------------------------------------

  public void sendError(final WebSocketSession session, String errMsg, ConcurrentHashMap<String, UserSession> users) {
    if (users.containsKey(session.getId())) {
      JsonObject message = new JsonObject();
      message.addProperty("id", "ERROR");
      message.addProperty("message", errMsg);
      sendMessage(session, message.toString());
    }
  }


  public void initWebRtcEndpoint(final WebSocketSession session,
                                 final WebRtcEndpoint webRtcEp, String sdpOffer) {
    addBaseEventListeners(session, webRtcEp, "WebRtcEndpoint");
    addWebRtcEventListeners(session, webRtcEp);

    /*
    OPTIONAL: Force usage of an Application-specific STUN server.
    Usually this is configured globally in KMS WebRTC settings file:
    /etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini

    But it can also be configured per-application, as shown:

    log.info("[Handler::initWebRtcEndpoint] Using STUN server: 193.147.51.12:3478");
    webRtcEp.setStunServerAddress("193.147.51.12");
    webRtcEp.setStunServerPort(3478);
    */

    // Process the SDP Offer to generate an SDP Answer
    String sdpAnswer = webRtcEp.processOffer(sdpOffer);

    log.info("[Handler::initWebRtcEndpoint] name: {}, SDP Offer from browser to KMS:\n{}",
        webRtcEp.getName(), sdpOffer);
    log.info("[Handler::initWebRtcEndpoint] name: {}, SDP Answer from KMS to browser:\n{}",
        webRtcEp.getName(), sdpAnswer);

    JsonObject message = new JsonObject();
    message.addProperty("id", "PROCESS_SDP_ANSWER");
    message.addProperty("sdpAnswer", sdpAnswer);
    sendMessage(session, message.toString());
  }


}
