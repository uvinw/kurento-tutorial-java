/*
 * Copyright 2018 Kurento (https://www.kurento.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kurento.tutorial.rtpreceiver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.RandomStringUtils;
import org.kurento.client.EventListener;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaProfileSpecType;
import org.kurento.client.MediaType;
import org.kurento.client.OnKeySoftLimitEvent;
import org.kurento.client.PlayerEndpoint;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.RtpEndpoint;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Kurento client
// Kurento crypto
// Kurento events


/**
 * Kurento Java Tutorial - Handler class.
 */
public class Handler extends TextWebSocketHandler {
  private final Logger log = LoggerFactory.getLogger(Handler.class);
  private final Gson gson = new GsonBuilder().create();

  private final ConcurrentHashMap<String, UserSession> users = new ConcurrentHashMap<>();

    private static final String RECORDER_BASE_FILE_PATH = "file:///tmp/";
//  private static final String RECORDER_BASE_FILE_PATH = "file:///mnt/c/Users/uvin.withana/Videos/";
  //  private static final String RECORDER_FILE_EXT = "newvideo.mp4";
  private static final String RECORDER_FILE_EXT = "newvideo-wed.mp4";

  private EndpointUtils endpointUtils = new EndpointUtils();

  @Autowired
  private KurentoClient kurento;

  @Override
  public void afterConnectionClosed(final WebSocketSession session,
                                    CloseStatus status) throws Exception {
    log.debug("[Handler::afterConnectionClosed] status: {}, sessionId: {}", status, session.getId());
    endpointUtils.stop(session, users);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session,
                                   TextMessage message) throws Exception {
    JsonObject jsonMessage = gson.fromJson(message.getPayload(),
        JsonObject.class);
    String sessionId = session.getId();

    log.info("[Handler::handleTextMessage] {}, sessionId: {}",
        jsonMessage, sessionId);

    log.info("-------------------------------" + users.size());
    try {
      String messageId = jsonMessage.get("id").getAsString();
      switch (messageId) {
        case "PROCESS_SDP_OFFER":
          handleProcessSdpOffer(session, jsonMessage);
          break;
        case "ADD_ICE_CANDIDATE":
          endpointUtils.handleAddIceCandidate(session, jsonMessage, users);
          break;
        case "STOP":
          endpointUtils.handleStop(session, jsonMessage, users);
          break;
        case "START_REC":
          endpointUtils.handleStartRec(session, jsonMessage, users);
          break;
        case "STOP_REC":
          endpointUtils.handleStopRec(session, jsonMessage, users);
          break;
        default:
          endpointUtils.sendError(session, "Invalid message, id: " + messageId, users);
          break;
      }
    } catch (Throwable ex) {
      log.error("[Handler::handleTextMessage] Exception: {}, sessionId: {}",
          ex, sessionId);
      endpointUtils.sendError(session, "Exception: " + ex.getMessage(), users);
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable ex)
      throws Exception {
    log.error("[Handler::handleTransportError] Exception: {}, sessionId: {}",
        ex, session.getId());
  }

  // PROCESS_SDP_OFFER ---------------------------------------------------------


  private String getRandomFileName() {
    String fileName = RandomStringUtils.randomAlphanumeric(10);
    return RECORDER_BASE_FILE_PATH + RECORDER_FILE_EXT;
  }


  /*
  RtpEndpoint configuration.
  Controls the SDP Offer/Answer negotiation between a 3rd-party RTP sender
  and KMS. A fake SDP Offer simulates the features that our RTP sender
  will have; then, the SDP Answer is parsed to extract the RTP and RTCP ports
  that KMS will use to listen for packets.
  */
  private void startRtpEndpoint(final WebSocketSession session,
                                RtpEndpoint rtpEp, Boolean useComedia, Boolean useSrtp) {
    log.info("[Handler::startRtpEndpoint] Configure RtpEndpoint, port discovery: {}, SRTP: {}",
        useComedia, useSrtp);

    endpointUtils.addBaseEventListeners(session, rtpEp, "RtpEndpoint");

    // Event: The SRTP key is about to expire
    rtpEp.addOnKeySoftLimitListener(
        new EventListener<OnKeySoftLimitEvent>() {
          @Override
          public void onEvent(OnKeySoftLimitEvent ev) {
            log.info("[RtpEndpoint::{}] source: {}, timestamp: {}, tags: {}, mediaType: {}",
                ev.getType(), ev.getSource(), ev.getTimestamp(), ev.getTags(),
                ev.getMediaType());
          }
        });

    // ---- RTP configuration BEGIN ----
    // Set the appropriate values for your setup
    String senderIp = "127.0.0.1";
    int senderRtpPortA = 5006;
    int senderRtpPortV = 5004;
    int senderSsrcA = 445566;
    int senderSsrcV = 112233;
    String senderCname = "user@example.com";
    String senderCodecV = "H264";
    // String senderCodecV = "VP8";
    // ---- RTP configuration END ----

    /*
    OPTIONAL: Set maximum bandwidth on reception.
    This can be useful if there is some limitation on the incoming bandwidth
    that the receiver is able to process.
    */
    // log.info("[Handler::startRtpEndpoint] Limit output bandwidth: 1024 kbps");
//     rtpEp.setMaxVideoRecvBandwidth(6000); // In kbps (1000 bps)

    String sdpComediaAttr = "";
    if (useComedia) {
      // Use Discard port (9)
      senderRtpPortA = 9;
      senderRtpPortV = 9;

      // Inspired by RFC 4145 Draft 05 ("COMEDIA")
      sdpComediaAttr = "a=direction:active\r\n";
    }

    Boolean useAudio = true;
    String senderProtocol = "RTP/AVPF";
    String sdpCryptoAttr = "";
    if (useSrtp) {
      // Use SRTP protocol
      useAudio = false;  // This demo uses audio only for non-SRTP streams
      senderProtocol = "RTP/SAVPF";

      // This is used by KMS to decrypt the sender's SRTP/SRTCP
      // Encryption key used by sender (ASCII): "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234"
      // In Base64: "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVoxMjM0"
      sdpCryptoAttr = "a=crypto:2 AES_CM_128_HMAC_SHA1_80 inline:QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVoxMjM0|2^31|1:1\r\n";
    }

/*
# SDP quick reference
SDP structure is composed of levels: session > media > source.
Each level can contain one or more of the next ones.
Typically, one session contains several medias, and each media contains one source.

---- Session-level information ----
v=
o=
s=
c=
t=
---- Media-level attributes ----
m=
a=
---- Source-level attributes ----
a=ssrc

Some default values are defined by different RFCs:
- RFC 3264 defines recommended values for "s=", "t=", "a=sendonly".
- RFC 5576 defines source-level attribute "a=ssrc".
*/

    String rtpSdpOffer =
        "v=0\r\n"
            + "o=- 0 0 IN IP4 " + senderIp + "\r\n"
            + "s=Kurento Tutorial - RTP Receiver\r\n"
            + "c=IN IP4 " + senderIp + "\r\n"
            + "t=0 0\r\n";

    if (useAudio) {
      rtpSdpOffer +=
          "m=audio " + senderRtpPortA + " RTP/AVPF 96\r\n"
              + "a=rtpmap:96 opus/48000/2\r\n"
              + "a=sendonly\r\n"
              + sdpComediaAttr
              + "a=ssrc:" + senderSsrcA + " cname:" + senderCname + "\r\n";
    }

    //todo: uncomment this
    rtpSdpOffer +=
        "m=video " + senderRtpPortV + " " + senderProtocol + " 103\r\n"
            + sdpCryptoAttr
            + "a=rtpmap:103 " + senderCodecV + "/90000\r\n"
            + "a=rtcp-fb:103 goog-remb\r\n"
            + "a=sendonly\r\n"
            + sdpComediaAttr
            + "a=ssrc:" + senderSsrcV + " cname:" + senderCname + "\r\n"
            + "";

    // Send the SDP Offer to KMS, and get its negotiated SDP Answer
    String rtpSdpAnswer = rtpEp.processOffer(rtpSdpOffer);

    log.info("[Handler::startRtpEndpoint] Fake SDP Offer from App to KMS:\n{}",
        rtpSdpOffer);
    log.info("[Handler::startRtpEndpoint] SDP Answer from KMS to App:\n{}",
        rtpSdpAnswer);

    // Parse SDP Answer
    // NOTE: No error checking; this code assumes that the SDP Answer from KMS
    // is always well formed.
    Pattern p;
    Matcher m;

    int kmsRtpPortA = 0;
    int senderRtcpPortA = 0;
    if (useAudio) {
      p = Pattern.compile("m=audio (\\d+) RTP");
      m = p.matcher(rtpSdpAnswer);
      m.find();
      kmsRtpPortA = Integer.parseInt(m.group(1));
      senderRtcpPortA = senderRtpPortA + 1;
    }

    p = Pattern.compile("m=video (\\d+) RTP");
    m = p.matcher(rtpSdpAnswer);
    m.find();
    int kmsRtpPortV = Integer.parseInt(m.group(1));
    int senderRtcpPortV = senderRtpPortV + 1;

    p = Pattern.compile("a=ssrc:(\\d+)");
    m = p.matcher(rtpSdpAnswer);
    m.find();
    String kmsSsrcV = m.group(1);

    p = Pattern.compile("c=IN IP4 (([0-9]{1,3}\\.){3}[0-9]{1,3})");
    m = p.matcher(rtpSdpAnswer);
    m.find();
    String kmsIp = m.group(1);

    // Check if KMS accepted the use of "direction" attribute
    useComedia = rtpSdpAnswer.contains("a=direction:passive");

    String msgConnInfo = "SDP negotiation finished\n";
    if (useAudio) {
      msgConnInfo += String.format(
          "* KMS listens for Audio RTP at port: %d\n", kmsRtpPortA);
    }
    msgConnInfo += String.format(
        "* KMS listens for Video RTP at port: %d\n", kmsRtpPortV);

    log.warn("\n\n\n use below >>>>> \n\n\nPEER_A=" + kmsRtpPortA + " PEER_V=" + kmsRtpPortV + " PEER_IP=localhost" +
        " \\\n" +
        "SELF_PATH=\"video.mp4\" \\\n" +
        "SELF_A=5006 SELF_ASSRC=445566 \\\n" +
        "SELF_V=5004 SELF_VSSRC=112233 \\\n" +
        "bash -c 'gst-launch-1.0 -e \\\n" +
        "  rtpbin name=r sdes=\"application/x-rtp-source-sdes,cname=(string)\\\"user\\@example.com\\\"\" \\\n" +
        "  filesrc location=\"$SELF_PATH\" ! decodebin name=d \\\n" +
        "  d. ! queue ! audioconvert ! opusenc \\\n" +
        "    ! rtpopuspay ! \"application/x-rtp,payload=(int)96,clock-rate=(int)48000,ssrc=(uint)$SELF_ASSRC\" \\\n" +
        "    ! r.send_rtp_sink_0 \\\n" +
        "  d. ! queue ! videoconvert ! x264enc tune=zerolatency \\\n" +
        "    ! rtph264pay ! \"application/x-rtp,payload=(int)103,clock-rate=(int)90000,ssrc=(uint)$SELF_VSSRC\" \\\n" +
        "    ! r.send_rtp_sink_1 \\\n" +
        "  r.send_rtp_src_0 ! udpsink host=$PEER_IP port=$PEER_A bind-port=$SELF_A \\\n" +
        "  r.send_rtcp_src_0 ! udpsink host=$PEER_IP port=$((PEER_A+1)) bind-port=$((SELF_A+1)) sync=false " +
        "async=false \\\n" +
        "  udpsrc port=$((SELF_A+1)) ! r.recv_rtcp_sink_0 \\\n" +
        "  r.send_rtp_src_1 ! udpsink host=$PEER_IP port=$PEER_V bind-port=$SELF_V \\\n" +
        "  r.send_rtcp_src_1 ! udpsink host=$PEER_IP port=$((PEER_V+1)) bind-port=$((SELF_V+1)) sync=false " +
        "async=false \\\n" +
        "  udpsrc port=$((SELF_V+1)) ! tee name=t \\\n" +
        "    t. ! queue ! r.recv_rtcp_sink_1 \\\n" +
        "    t. ! queue ! fakesink dump=true async=false'\n\n\n use above >>>>>");

    if (useSrtp) {
      msgConnInfo += String.format(
          "* KMS uses Video SSRC: %s\n", kmsSsrcV);
    }
    if (useAudio) {
      msgConnInfo += String.format(
          "* KMS expects Audio SSRC from sender: %d\n", senderSsrcA);
    }
    msgConnInfo += String.format(
        "* KMS expects Video SSRC from sender: %d\n", senderSsrcV);
    msgConnInfo += String.format("* KMS local IP address: %s\n", kmsIp);
    if (useComedia) {
      msgConnInfo += "* KMS will discover remote IP and port to send RTCP\n";
    } else {
      if (useAudio) {
        msgConnInfo += String.format(
            "* KMS sends Audio RTCP to: %s:%d\n", senderIp, senderRtcpPortA);
      }
      msgConnInfo += String.format(
          "* KMS sends Video RTCP to: %s:%d\n", senderIp, senderRtcpPortV);
    }

    log.info("[Handler::startRtpEndpoint] " + msgConnInfo);

    // Send info to UI
    {
      JsonObject message = new JsonObject();
      message.addProperty("id", "SHOW_CONN_INFO");
      message.addProperty("text", msgConnInfo);
      endpointUtils.sendMessage(session, message.toString());
    }
    {
      JsonObject message = new JsonObject();
      message.addProperty("id", "SHOW_SDP_ANSWER");
      message.addProperty("text", rtpSdpAnswer);
      endpointUtils.sendMessage(session, message.toString());
    }
  }


  private void handleProcessSdpOffer(final WebSocketSession session,
                                     JsonObject jsonMessage) {
    // ---- Session handling

    String sessionId = session.getId();

    log.info("[Handler::handleStart] User count: {}", users.size());
    log.info("[Handler::handleStart] New user: {}", sessionId);

    final UserSession user = new UserSession();
    users.put(session.getId(), user);


    // ---- Media pipeline

    log.info("[Handler::handleStart] Create Media Pipeline");
    final MediaPipeline pipeline = kurento.createMediaPipeline();
    user.setMediaPipeline(pipeline);

    Boolean useSrtp = jsonMessage.get("useSrtp").getAsBoolean();
    final PlayerEndpoint playerEp = endpointUtils.makePlayerEndpoint(pipeline, useSrtp);

    RecorderEndpoint recorder = new RecorderEndpoint.Builder(pipeline, getRandomFileName())
        .withMediaProfile(MediaProfileSpecType.MP4).build();
    final WebRtcEndpoint webRtcEp = new WebRtcEndpoint.Builder(pipeline).build();
    user.setWebRtcEp(webRtcEp);


    playerEp.setMaxOutputBitrate(0);
    playerEp.setMinOutputBitrate(300000);

    recorder.setMaxOutputBitrate(0);
    recorder.setMinOutputBitrate(300000);

    webRtcEp.setMaxOutputBitrate(0);
    webRtcEp.setMinOutputBitrate(300000);

    webRtcEp.setMaxAudioRecvBandwidth(0);

    webRtcEp.setMaxVideoRecvBandwidth(0);
    webRtcEp.setMinVideoRecvBandwidth(300000);

    webRtcEp.setMaxVideoSendBandwidth(0);
    webRtcEp.setMinVideoSendBandwidth(300000);

//    webRtcEp.setMinVideoSendBandwidth(100);
//    rtpEp.setMaxVideoSendBandwidth(0);
//    rtpEp.setMaxOutputBitrate(0);
//    rtpEp.setMinOutputBitrate(0);
//    rtpEp.setMaxAudioRecvBandwidth(0);
//    rtpEp.setMaxAudioRecvBandwidth(0);
//    rtpEp.setMaxVideoSendBandwidth(0);
//    rtpEp.setMaxVideoRecvBandwidth(0);
//    rtpEp.setMinVideoRecvBandwidth(0);
//    rtpEp.setMinVideoSendBandwidth(0);


    // ---- Endpoint configuration


//    final RtpEndpoint rtpEp = endpointUtils.makeRtpEndpoint(pipeline, useSrtp);
//    user.setRtpEp(rtpEp);
    user.setPlayerEp(playerEp);
    user.setRecorder(recorder);
//    endpointUtils.addRtpListeners(rtpEp);
    endpointUtils.addPlayerListeners(playerEp);
    endpointUtils.addRecorderListeners(recorder);
    endpointUtils.addWebRtpListeners(webRtcEp);

    playerEp.connect(recorder, MediaType.VIDEO);
    playerEp.connect(recorder, MediaType.AUDIO);
    playerEp.connect(webRtcEp);
//    playerEp.connect(recorder);


    //todo: uncomment this
//    rtpEp.connect(recorder, MediaType.VIDEO);
//    rtpEp.connect(recorder, MediaType.AUDIO);
//    rtpEp.connect(webRtcEp);

    //todo: temp code for connecting all webrtc endpoints to the same RTP sink
//    if (users.keySet().size() == 4) {
//      log.error("xxxxxx--------------------------------" + users.keySet().size());
//      for (String key : users.keySet()) {
//        rtpEp.connect(users.get(key).getWebRtcEp());
//      }
//    }

    String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
    endpointUtils.initWebRtcEndpoint(session, webRtcEp, sdpOffer);
    endpointUtils.startWebRtcEndpoint(webRtcEp);

//    Boolean useComedia = jsonMessage.get("useComedia").getAsBoolean();
//    startRtpEndpoint(session, rtpEp, useComedia, useSrtp);


    // ---- Debug
    String pipelineDot = pipeline.getGstreamerDot();
    try (PrintWriter out = new PrintWriter("pipeline.dot")) {
      out.println("------------------- " + pipelineDot);
    } catch (IOException ex) {
      log.error("[Handler::start] Exception: {}", ex.getMessage());
    }

    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    playerEp.play();


  }



}
