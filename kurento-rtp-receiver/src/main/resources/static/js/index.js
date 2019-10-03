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

let ws = [];

let videoRtp = [];
let webRtcPeer = [];

// UI
let uiState = null;
const UI_IDLE = 0;
const UI_STARTING = 1;
const UI_STARTED = 2;

window.onload = function () {
  console = new Console();
  ws.push(new WebSocket('wss://' + location.host + '/rtpreceiver'));
  console.log("Page loaded");
  videoRtp.push(document.getElementById('videoRtp'));
  uiSetState(UI_IDLE);
}

window.onbeforeunload = function () {
  ws.forEach(client => {
    client.close();
  })
}

function explainUserMediaError(err) {
  const n = err.name;
  if (n === 'NotFoundError' || n === 'DevicesNotFoundError') {
    return "Missing webcam for required tracks";
  } else if (n === 'NotReadableError' || n === 'TrackStartError') {
    return "Webcam is already in use";
  } else if (n === 'OverconstrainedError' || n === 'ConstraintNotSatisfiedError') {
    return "Webcam doesn't provide required tracks";
  } else if (n === 'NotAllowedError' || n === 'PermissionDeniedError') {
    return "Webcam permission has been denied by the user";
  } else if (n === 'TypeError') {
    return "No media tracks have been requested";
  } else {
    return "Unknown error";
  }
}

function sendMessage(message, id) {
  const jsonMessage = JSON.stringify(message);
  console.log("[sendMessage] message: " + jsonMessage);
  ws[id].send(jsonMessage);
}


/* ============================= */
/* ==== WebSocket signaling ==== */
/* ============================= */

ws.forEach((client, id) => {
  client.onmessage = function (message) {
    const jsonMessage = JSON.parse(message.data);
    console.log("[onmessage] Received message: " + message.data);

    switch (jsonMessage.id) {
      case 'PROCESS_SDP_ANSWER':
        handleProcessSdpAnswer(jsonMessage, id);
        break;
      case 'ADD_ICE_CANDIDATE':
        handleAddIceCandidate(jsonMessage, id);
        break;
      // case 'SHOW_CONN_INFO':
      //   handleShowConnInfo(jsonMessage, id);
      //   break;
      // case 'SHOW_SDP_ANSWER':
      //   handleShowSdpAnswer(jsonMessage, id);
      //   break;
      case 'END_PLAYBACK':
        handleEndPlayback(jsonMessage, id);
        break;
      case 'ERROR':
        handleError(jsonMessage, id);
        break;
      default:
        error("[onmessage] Invalid message, id: " + jsonMessage.id);
        break;
    }
  }
});

// PROCESS_SDP_ANSWER ----------------------------------------------------------

function handleProcessSdpAnswer(jsonMessage, id) {
  console.log("[handleProcessSdpAnswer] SDP Answer received from Kurento Client; process in Kurento Peer");

  webRtcPeer[id].processAnswer(jsonMessage.sdpAnswer, (err) => {
    if (err) {
      console.error("[handleProcessSdpAnswer] " + err);
      return;
    }

    console.log("[handleProcessSdpAnswer] SDP Answer ready; start remote video");
    startVideo(videoRtp[id]);

    uiSetState(UI_STARTED, id);
  });
}

// ADD_ICE_CANDIDATE -----------------------------------------------------------

function handleAddIceCandidate(jsonMessage, id) {
  webRtcPeer[i].addIceCandidate(jsonMessage.candidate, (err) => {
    if (err) {
      console.error("[handleAddIceCandidate] " + err);
      return;
    }
  });
}

// // SHOW_CONN_INFO --------------------------------------------------------------
//
// function handleShowConnInfo(jsonMessage, id) {
//   document.getElementById("msgConnInfo").value = jsonMessage.text;
// }
//
// // SHOW_SDP_ANSWER -------------------------------------------------------------
//
// function handleShowSdpAnswer(jsonMessage, id) {
//   document.getElementById("msgSdpText").value = jsonMessage.text;
// }

// END_PLAYBACK ----------------------------------------------------------------

function handleEndPlayback(jsonMessage, id) {
  uiSetState(UI_IDLE);
  hideSpinner(videoRtp[id]);
}

// ERROR -----------------------------------------------------------------------

function error(errMessage) {
  console.error("[error] " + errMessage);
  if (uiState == UI_STARTING) {
    uiSetState(UI_IDLE);
  }
}

function handleError(jsonMessage) {
  const errMessage = jsonMessage.message;
  error(errMessage);
}


/* ==================== */
/* ==== UI actions ==== */
/* ==================== */

// start -----------------------------------------------------------------------

function magic(id) {
  console.log("magic --------------------")
}

function start(id) {
  id = 0;
  console.log("[start] Create WebRtcPeerRecvonly");
  uiSetState(UI_STARTING);
  showSpinner(videoRtp[id]);

  const options = {
    localVideo: null,
    remoteVideo: videoRtp[id],
    mediaConstraints: {audio: true, video: true},
    onicecandidate: (candidate) => sendMessage({
      id: 'ADD_ICE_CANDIDATE',
      candidate: candidate,
    }),
  };

  webRtcPeer[id] = initializeWebRTCPeer(this, options);
}

function initializeWebRTCPeer(that, options) {
  return new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
    function (err) {
      if (err) {
        console.error("[start/WebRtcPeerRecvonly] Error in constructor: "
          + explainUserMediaError(err));
        return;
      }

      console.log("[start/WebRtcPeerRecvonly] Created; generate SDP Offer");
      that.webRtcPeer[id].generateOffer((err, sdpOffer) => {
        if (err) {
          console.error("[start/WebRtcPeerRecvonly/generateOffer] " + err);
          return;
        }

        const useComedia = document.getElementById('useComedia').checked;
        const useSrtp = document.getElementById('useSrtp').checked;

        console.log("[start/WebRtcPeerRecvonly/generateOffer] Use COMEDIA: "
          + useComedia);
        console.log("[start/WebRtcPeerRecvonly/generateOffer] Use SRTP: "
          + useSrtp);

        sendMessage({
          id: 'PROCESS_SDP_OFFER',
          sdpOffer: sdpOffer,
          useComedia: useComedia,
          useSrtp: useSrtp,
        });

        console.log("[start/WebRtcPeerRecvonly/generateOffer] Done!");
        uiSetState(UI_STARTED);
      });
    });
}

// stop ------------------------------------------------------------------------

function stop() {
  console.log("[stop]");

  sendMessage({
    id: 'STOP',
  });

  if (webRtcPeer) {
    webRtcPeer.dispose();
    webRtcPeer = null;
  }

  uiSetState(UI_IDLE);
  hideSpinner(videoRtp);
}

function startRecording(id) {
  console.log("[startRecording triggered]", id);
  sendMessage({
    id: 'START_REC',
  });
}
function stopRecording(id) {
  console.log("[startRecording triggered]", id);
  sendMessage({
    id: 'STOP_REC',
  });
}

/* ================== */
/* ==== UI state ==== */

/* ================== */

function uiSetState(nextState, id) {
  uiEnableElement('#magic', 'magic(id)');
  switch (nextState) {
    case UI_IDLE:
      uiEnableElement('#start', 'start(id)');
      uiDisableElement('#stop');
      break;
    case UI_STARTING:
      uiDisableElement('#start');
      uiDisableElement('#stop');
      break;
    case UI_STARTED:
      uiDisableElement('#start');
      uiEnableElement('#stop', 'stop()');
      break;
    default:
      console.error("[setState] Unknown state: " + nextState);
      return;
  }
  uiState = nextState;
}

function uiEnableElement(id, onclickHandler) {
  $(id).attr('disabled', false);
  if (onclickHandler) {
    $(id).attr('onclick', onclickHandler);
  }
}

function uiDisableElement(id) {
  $(id).attr('disabled', true);
  $(id).removeAttr('onclick');
}

function showSpinner() {
  for (let i = 0; i < arguments.length; i++) {
    //todo: fix spinner
    // arguments[i].poster = './img/transparent-1px.png';
    // arguments[i].style.background = "center transparent url('./img/spinner.gif') no-repeat";
  }
}

function hideSpinner() {
  for (let i = 0; i < arguments.length; i++) {
    arguments[i].src = '';
    arguments[i].poster = './img/webrtc.png';
    arguments[i].style.background = '';
  }
}

function startVideo(video) {
  // Manually start the <video> HTML element
  // This is used instead of the 'autoplay' attribute, because iOS Safari
  //  requires a direct user interaction in order to play a video with audio.
  // Ref: https://developer.mozilla.org/en-US/docs/Web/HTML/Element/video
  video.play().catch((err) => {
    if (err.name === 'NotAllowedError') {
      console.error("[start] Browser doesn't allow playing video: " + err);
    } else {
      console.error("[start] Error in video.play(): " + err);
    }
  });
}

/**
 * Lightbox utility (to display media pipeline image in a modal dialog)
 */
$(document).delegate('*[data-toggle="lightbox"]', 'click', function (event) {
  event.preventDefault();
  $(this).ekkoLightbox();
});
