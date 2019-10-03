package org.kurento.tutorial.rtpreceiver;

import com.google.gson.JsonObject;
import org.kurento.client.BaseRtpEndpoint;
import org.kurento.client.ConnectionStateChangedEvent;
import org.kurento.client.ErrorEvent;
import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.IceCandidateFoundEvent;
import org.kurento.client.IceComponentStateChangeEvent;
import org.kurento.client.IceGatheringDoneEvent;
import org.kurento.client.MediaFlowInStateChangeEvent;
import org.kurento.client.MediaFlowOutStateChangeEvent;
import org.kurento.client.MediaState;
import org.kurento.client.MediaStateChangedEvent;
import org.kurento.client.MediaTranscodingStateChangeEvent;
import org.kurento.client.MediaType;
import org.kurento.client.NewCandidatePairSelectedEvent;
import org.kurento.client.PausedEvent;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.RecordingEvent;
import org.kurento.client.RtpEndpoint;
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
        log.error("RECORD: RTP sink flow in state change: " + event.getType() + "::" + event.getPadName() + ":: " + event.getMediaType() + ":: " + event.getState());

      }
    });

    rtpEp.addMediaStateChangedListener(new EventListener<MediaStateChangedEvent>() {
      @Override
      public void onEvent(MediaStateChangedEvent event) {
        log.info("RECORD:  RTP sink media state change - new:" + event.getNewState() + "- old:" + event.getOldState());
        if (MediaState.CONNECTED == event.getNewState()) {
          log.info("RECORD:  RTP sink media state changed to CONNECTED");
//          recorder.record();
          //todo: move these to frontend
        }
        if (MediaState.DISCONNECTED == event.getNewState()) {
          log.info("RECORD:  RTP sink media state changed to DISCONNECTED");
//          recorder.stopAndWait();
          //todo: move these to frontend
        }
      }
    });

    rtpEp.addErrorListener(event -> {
      log.info("RECORD: rtp error" + event.getErrorCode() + event.getDescription() + event.getType());
    });

    rtpEp.addConnectionStateChangedListener(event -> {
      log.info("RECORD: rtp connection state change" + event.getNewState() + event.getOldState());
    });
  }
  public void addRecorderListeners(RecorderEndpoint recorder) {

    recorder.addRecordingListener(new EventListener<RecordingEvent>() {
      @Override
      public void onEvent(RecordingEvent event) {
        log.info("RECORD: on event: " + event.getType());
        if (recorder.isMediaFlowingIn(MediaType.VIDEO)) {
          log.info("RECORD: recorder video flowing in ");
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
        log.error("error in recorder: " + event.getType() + "::" + event.getDescription() + ":: " + event.getErrorCode());
      }
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

}
