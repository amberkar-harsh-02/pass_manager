// --- 1. CRYPTOGRAPHY ENGINE ---

// Generate a secure, random 256-bit (32 byte) AES key
function generateEphemeralKey() {
    const keyArray = new Uint8Array(32);
    window.crypto.getRandomValues(keyArray);
    // Convert the raw bytes into a Base64 string so it fits nicely in a QR code
    return btoa(String.fromCharCode.apply(null, keyArray));
}

// Generate a random 6-character Room ID
function generateRoomId() {
    return Math.random().toString(36).substring(2, 8).toUpperCase();
}

// The native Web Crypto engine to decrypt the Android payload
async function decryptPayload(base64Ciphertext, base64Iv, base64Key) {
    try {
        const keyBuffer = Uint8Array.from(atob(base64Key), c => c.charCodeAt(0));
        const ivBuffer = Uint8Array.from(atob(base64Iv), c => c.charCodeAt(0));
        const cipherBuffer = Uint8Array.from(atob(base64Ciphertext), c => c.charCodeAt(0));

        const cryptoKey = await window.crypto.subtle.importKey(
            "raw", keyBuffer, { name: "AES-GCM" }, false, ["decrypt"]
        );

        const decryptedBuffer = await window.crypto.subtle.decrypt(
            { name: "AES-GCM", iv: ivBuffer }, cryptoKey, cipherBuffer
        );

        return new TextDecoder().decode(decryptedBuffer);
    } catch (error) {
        console.error("Decryption Failed! The payload was tampered with or the key is wrong.", error);
        return null;
    }
}


// --- 2. EXTENSION INITIALIZATION ---

// Lock in the session variables instantly
const roomId = generateRoomId();
const sessionKey = generateEphemeralKey();

// Ensure LEDGER_CONFIG is loaded before this script runs!
const RELAY_URL = `wss://s16353.nyc1.piesocket.com/v3/1?api_key=${LEDGER_CONFIG.PIESOCKET_API_KEY}&notify_self=1`;

const statusText = document.getElementById('status-text');
const qrImage = document.getElementById('qr-code');

const socket = new WebSocket(RELAY_URL);

socket.onerror = function() {
    statusText.innerText = "Connection Failed.";
    statusText.className = "status-badge error";
};

socket.onopen = function() {
    statusText.innerText = "Awaiting Encrypted Payload...";
    statusText.className = "status-badge waiting";

    // Embed BOTH the Room ID and the AES Key into the QR code
    const qrData = JSON.stringify({ room: roomId, key: sessionKey });
    qrImage.src = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(qrData)}`;
    qrImage.style.display = "inline";
};


// --- 3. THE SECURE LISTENER ---

socket.onmessage = async function(event) {
    try {
        const incomingData = JSON.parse(event.data);

        // We now expect { room, username, payload (ciphertext), iv }
        if (incomingData.room === roomId && incomingData.payload && incomingData.iv) {

            statusText.innerText = "Decrypting Payload...";
            statusText.className = "status-badge waiting";

            // Feed the ciphertext and IV into the decryption engine using our optical key
            const decryptedPassword = await decryptPayload(incomingData.payload, incomingData.iv, sessionKey);

            if (decryptedPassword) {
                statusText.innerText = "Target Injected!";
                statusText.className = "status-badge success";

                chrome.tabs.query({active: true, currentWindow: true}, function(tabs) {
                    chrome.scripting.executeScript({
                        target: {tabId: tabs[0].id},
                        func: injectCredentials,
                        args: [incomingData.username, decryptedPassword]
                    });
                });
            } else {
                statusText.innerText = "Decryption Failed!";
                statusText.className = "status-badge error";
            }
        }
    } catch (e) {
        console.error("JSON Parse Error", e);
    }
};


// --- 4. THE PROXIMITY HEURISTIC (Unchanged) ---

function injectCredentials(username, password) {
    const inputs = Array.from(document.querySelectorAll('input'));
    const passFields = inputs.filter(input => input.type.toLowerCase() === 'password');

    if (passFields.length > 0) {
        passFields.forEach(field => {
            field.value = password;
            field.dispatchEvent(new Event('input', { bubbles: true }));
            field.dispatchEvent(new Event('change', { bubbles: true }));
        });

        const firstPassIndex = inputs.indexOf(passFields[0]);

        for (let i = firstPassIndex - 1; i >= 0; i--) {
            const type = inputs[i].type.toLowerCase();
            const isVisible = inputs[i].style.display !== 'none' && inputs[i].type !== 'hidden';

            if ((type === 'text' || type === 'email') && isVisible) {
                const userField = inputs[i];
                userField.value = username;
                userField.dispatchEvent(new Event('input', { bubbles: true }));
                userField.dispatchEvent(new Event('change', { bubbles: true }));
                break;
            }
        }
        alert("New Account Credentials Injected!");
    } else {
        alert("Error: No password fields found on this registration page.");
    }
}