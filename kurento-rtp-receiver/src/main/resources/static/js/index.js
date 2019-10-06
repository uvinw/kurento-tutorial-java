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
let startRecordingBtn = [];
let stopRecordingBtn = [];

// UI
let uiState = null;
const UI_IDLE = 0;
const UI_STARTING = 1;
const UI_STARTED = 2;

window.onload = function () {
  // console = new Console();
  console.log("Page loaded");
  initiateApp();
  uiSetState(UI_IDLE);
};

window.onbeforeunload = function () {
  ws.forEach(client => {
    client.close();
  });
};

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

// ws.forEach((client, videoId) => {
//   debugger;
//   client.onmessage = function (message) {
//     debugger;
//     const jsonMessage = JSON.parse(message.data);
//     console.log("[onmessage] Received message: " + message.data);
//
//     switch (jsonMessage.id) {
//       case 'PROCESS_SDP_ANSWER':
//         handleProcessSdpAnswer(jsonMessage, videoId);
//         break;
//       case 'ADD_ICE_CANDIDATE':
//         handleAddIceCandidate(jsonMessage, videoId);
//         break;
//       // case 'SHOW_CONN_INFO':
//       //   handleShowConnInfo(jsonMessage, videoId);
//       //   break;
//       // case 'SHOW_SDP_ANSWER':
//       //   handleShowSdpAnswer(jsonMessage, videoId);
//       //   break;
//       case 'END_PLAYBACK':
//         handleEndPlayback(jsonMessage, videoId);
//         break;
//       case 'ERROR':
//         handleError(jsonMessage, videoId);
//         break;
//       default:
//         error("[onmessage] Invalid message, id: " + jsonMessage.id);
//         break;
//     }
//   };
// });


// PROCESS_SDP_ANSWER ----------------------------------------------------------

function handleProcessSdpAnswer(jsonMessage, id) {
  debugger;
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
  webRtcPeer[id].addIceCandidate(jsonMessage.candidate, (err) => {
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

function start(id) {
  // id = 0;
  // console.log("[start] Create WebRtcPeerRecvonly");
  // uiSetState(UI_STARTING);
  // showSpinner(videoRtp[id]);
  //
  // const options = {
  //   localVideo: null,
  //   remoteVideo: videoRtp[id],
  //   mediaConstraints: {audio: true, video: true},
  //   onicecandidate: (candidate) => sendMessage({
  //     id: 'ADD_ICE_CANDIDATE',
  //     candidate: candidate,
  //   }),
  // };
  //
  // webRtcPeer[id] = initializeWebRTCPeer(this, options);
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
  // sendMessage({
  //   id: 'START_REC',
  // }, id);
}

function stopRecording(id) {
  console.log("[stopRecording triggered]", id);
  // sendMessage({
  //   id: 'STOP_REC',
  // }, id);
}

/* ================== */
/* ==== UI state ==== */

/* ================== */

function initiateApp() {
  uiEnableElement('#add', 'addNewVideoSetup()');
  // uiEnableElement('#magic', 'magic(' + 0 + ')');
}

let videoCount = -1;


function addNewVideoSetup() {
  videoCount++;

  //create HTML elements
  let video = document.createElement('video');
  video.autoplay = true;
  video.setAttribute('autoplay', '');
  video.setAttribute('playsinline', '');
  video.setAttribute('width', '480');
  video.setAttribute('height', '360');
  // video.setAttribute('poster', '/img/webrtc.png');

  let videoDiv = document.getElementById('videoBig');
  let br = document.createElement('br');

  // let startFunction =

  let stopFunction = function () {
    return stopRecording(videoCount);
  };

  let startRecBtn = createButton("Start Recording", videoCount);
  let stopRecBtn = createButton("Stop Recording", stopFunction, videoCount);

  //push elements into arrays
  // videoRtp.push(document.getElementById('videoRtp'));
  let newWs = new WebSocket('wss://' + location.host + '/rtpreceiver');
  newWs.onmessage = function (message) {
    debugger;
    const jsonMessage = JSON.parse(message.data);
    console.log("[onmessage] Received message: " + message.data);
    //todo: this video count has to be passed separately - to avoid live updating
    switch (jsonMessage.id) {
      case 'PROCESS_SDP_ANSWER':
        handleProcessSdpAnswer(jsonMessage, videoCount);
        break;
      case 'ADD_ICE_CANDIDATE':
        handleAddIceCandidate(jsonMessage, videoCount);
        break;
      // case 'SHOW_CONN_INFO':
      //   handleShowConnInfo(jsonMessage, videoCount);
      //   break;
      // case 'SHOW_SDP_ANSWER':
      //   handleShowSdpAnswer(jsonMessage, videoCount);
      //   break;
      case 'END_PLAYBACK':
        handleEndPlayback(jsonMessage, videoCount);
        break;
      case 'ERROR':
        handleError(jsonMessage, videoCount);
        break;
      default:
        error("[onmessage] Invalid message, id: " + jsonMessage.id);
        break;
    }
  };

  ws.push(newWs);
  videoRtp.push(video);
  startRecordingBtn.push(startRecBtn);
  stopRecordingBtn.push(stopRecBtn);

  //append to DOM
  videoDiv.appendChild(video);
  videoDiv.appendChild(startRecBtn);
  videoDiv.appendChild(stopRecBtn);

  // ---------------------- START LOGIC ---------------------------
  console.log("[start] Create WebRtcPeerRecvonly");
  uiSetState(UI_STARTING, videoCount);
  showSpinner(videoRtp[videoCount]);

  const options = {
    localVideo: null,
    remoteVideo: videoRtp[videoCount],
    mediaConstraints: {audio: true, video: true},
    onicecandidate: (candidate) => sendMessage({
      id: 'ADD_ICE_CANDIDATE',
      candidate: candidate,
    }, videoCount),
  };

  webRtcPeer[videoCount] = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
    function (err) {
      if (err) {
        console.error("[start/WebRtcPeerRecvonly] Error in constructor: "
          + explainUserMediaError(err));
        return;
      }

      console.log("[start/WebRtcPeerRecvonly] Created; generate SDP Offer");
      webRtcPeer[videoCount].generateOffer((err, sdpOffer) => {
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

        let millisecondsToWait = 2000;
        setTimeout(function () {



          sendMessage({
            id: 'PROCESS_SDP_OFFER',
            sdpOffer: sdpOffer,
            useComedia: useComedia,
            useSrtp: useSrtp,
          }, videoCount);

          console.log("[start/WebRtcPeerRecvonly/generateOffer] Done!");
          uiSetState(UI_STARTED);


          }, millisecondsToWait);






      });
    });








}


function createButton(buttonText, id) {
  let startRecBtn = document.createElement('input');
  startRecBtn.type = 'button';
  startRecBtn.id = 'button';
  startRecBtn.value = buttonText;
  startRecBtn.className = "btn btn-info";
  startRecBtn.onclick = higherStartFunction(id);
  return startRecBtn;
}

function higherStartFunction(test) {
  return function () {
    startRecording(test);
  }
};

// function higherOnMessageFunction(id) {
//   return function () {
//     newOnMessage(id);
//   }
// };


function initializeWebRTCPeer(that, options) {

}

function uiSetState(nextState, id) {
  // switch (nextState) {
  //   case UI_IDLE:
  //     uiEnableElement('#start', 'start(id)');
  //     uiDisableElement('#stop');
  //     break;
  //   case UI_STARTING:
  //     uiDisableElement('#start');
  //     uiDisableElement('#stop');
  //     break;
  //   case UI_STARTED:
  //     uiDisableElement('#start');
  //     uiEnableElement('#stop', 'stop()');
  //     break;
  //   default:
  //     console.error("[setState] Unknown state: " + nextState);
  //     return;
  // }
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
