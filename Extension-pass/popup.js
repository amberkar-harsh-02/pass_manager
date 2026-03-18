// The Pub/Sub Relay Server
// PieSocket's Official Demo Cluster
const RELAY_URL = "wss://s16353.nyc1.piesocket.com/v3/1?api_key=GK8axGZJUQtEZarRXFD2Q22Qyk4ypJEJk2QHrBbp&notify_self=1"
const roomId = Math.random().toString(36).substring(2, 8).toUpperCase();
const sessionKey = Math.random().toString(36).substring(2, 10);

const statusText = document.getElementById('status-text');
const qrImage = document.getElementById('qr-code');

const socket = new WebSocket(RELAY_URL);

socket.onerror = function() {
    statusText.innerText = "Connection Failed.";
    statusText.className = "status-badge error"; // Uses the red theme
};

socket.onopen = function() {
    statusText.innerText = "Awaiting Payload...";
    statusText.className = "status-badge waiting";

    const qrData = JSON.stringify({ room: roomId, key: sessionKey });
    qrImage.src = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(qrData)}`;
    qrImage.style.display = "inline";
};

socket.onmessage = function(event) {
    try {
        const incomingData = JSON.parse(event.data);

        if (incomingData.room === roomId && incomingData.password) {
                    statusText.innerText = "Target Injected!";
                    statusText.className = "status-badge success";

            chrome.tabs.query({active: true, currentWindow: true}, function(tabs) {
                chrome.scripting.executeScript({
                    target: {tabId: tabs[0].id},
                    func: injectCredentials,
                    // Pass BOTH arguments to the injected script
                    args: [incomingData.username, incomingData.password]
                });
            });
        }
    } catch (e) {
        console.error("JSON Parse Error", e);
    }
};

// --- THE PROXIMITY HEURISTIC ---
function injectCredentials(username, password) {
    // 1. Grab every single input field on the page
    const inputs = Array.from(document.querySelectorAll('input'));

    // 2. Filter out ONLY the password fields
    const passFields = inputs.filter(input => input.type.toLowerCase() === 'password');

    if (passFields.length > 0) {
        // 3. Inject the generated password into EVERY password field found
        // This instantly handles both "Password" and "Confirm Password"
        passFields.forEach(field => {
            field.value = password;
            field.dispatchEvent(new Event('input', { bubbles: true }));
            field.dispatchEvent(new Event('change', { bubbles: true }));
        });

        // 4. Find the exact index of the FIRST password field in the main inputs array
        const firstPassIndex = inputs.indexOf(passFields[0]);

        // 5. Walk BACKWARDS up the DOM from that first password field to find the username
        for (let i = firstPassIndex - 1; i >= 0; i--) {
            const type = inputs[i].type.toLowerCase();
            const isVisible = inputs[i].style.display !== 'none' && inputs[i].type !== 'hidden';

            // The first visible text or email box right above the password is our target
            if ((type === 'text' || type === 'email') && isVisible) {
                const userField = inputs[i];
                userField.value = username;
                userField.dispatchEvent(new Event('input', { bubbles: true }));
                userField.dispatchEvent(new Event('change', { bubbles: true }));
                break; // Stop looking once we fill the username
            }
        }
        alert("New Account Credentials Injected!");
    } else {
        alert("Error: No password fields found on this registration page.");
    }
}