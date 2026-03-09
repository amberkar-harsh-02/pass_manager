// The Pub/Sub Relay Server
// PieSocket's Official Demo Cluster
const RELAY_URL = "wss://s16271.nyc1.piesocket.com/v3/1?api_key=3lorUztBvwOhTfiMNJ4W3CAWJiCRAXEjWzwBciIS&notify_self=1"
const roomId = Math.random().toString(36).substring(2, 8).toUpperCase();
const sessionKey = Math.random().toString(36).substring(2, 10);

const statusText = document.getElementById('status-text');
const qrImage = document.getElementById('qr-code');

const socket = new WebSocket(RELAY_URL);

socket.onerror = function() {
    statusText.innerText = "Connection Failed.";
    statusText.style.color = "red";
};

socket.onopen = function() {
    statusText.innerText = "Awaiting Payload...";

    const qrData = JSON.stringify({ room: roomId, key: sessionKey });
    qrImage.src = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(qrData)}`;
    qrImage.style.display = "inline";
};

socket.onmessage = function(event) {
    // 1. THE WIRETAP: Print literally everything the server sends us
    console.log("📡 RAW DATA RECEIVED FROM PIEHOST:", event.data);

    try {
        const incomingData = JSON.parse(event.data);
        console.log("🧩 PARSED JSON:", incomingData);

        // Check if the room matches
        if (incomingData.room === roomId) {
            console.log("✅ Room matched! Payload:", incomingData.password);

            statusText.innerText = "Payload Received! Injecting...";
            statusText.style.color = "green";

            chrome.tabs.query({active: true, currentWindow: true}, function(tabs) {
                chrome.scripting.executeScript({
                    target: {tabId: tabs[0].id},
                    func: injectPasswordIntoPage,
                    args: [incomingData.password]
                });
            });
        } else {
            console.warn("⚠️ Ignored message. Wrong room. Expected:", roomId, "Got:", incomingData.room);
        }
    } catch (e) {
        console.error("❌ JSON Parse Error. The server sent text we couldn't read:", e);
    }
};

function injectPasswordIntoPage(password) {
    const passwordFields = document.querySelectorAll('input[type="password"]');
    if (passwordFields.length > 0) {
        passwordFields[0].value = password;
        alert("Ledger Payload Injected!");
    } else {
        alert("Ledger Error: No password field found on this page.");
    }
}