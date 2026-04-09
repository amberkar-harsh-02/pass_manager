// --- 1. CRYPTOGRAPHY ENGINE ---

function generateEphemeralKey() {
    const keyArray = new Uint8Array(32);
    window.crypto.getRandomValues(keyArray);
    return btoa(String.fromCharCode.apply(null, keyArray));
}

function generateRoomId() {
    return Math.random().toString(36).substring(2, 8).toUpperCase();
}

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

const roomId = generateRoomId();
const sessionKey = generateEphemeralKey();
const RELAY_URL = `wss://s16353.nyc1.piesocket.com/v3/1?api_key=${LEDGER_CONFIG.PIESOCKET_API_KEY}&notify_self=1`;

const statusText = document.getElementById('status-text');
const qrImage = document.getElementById('qr-code');

const socket = new WebSocket(RELAY_URL);

socket.onerror = function() {
    statusText.innerText = "⚠️ Connection offline";
    statusText.className = "status-badge error";
};

socket.onopen = function() {
    statusText.innerText = "⏳ Waiting for scan...";
    statusText.className = "status-badge waiting";

    const qrData = JSON.stringify({ room: roomId, key: sessionKey });
    qrImage.src = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(qrData)}`;
    qrImage.style.display = "inline";
};


// --- 3. THE SECURE LISTENER ---

socket.onmessage = async function(event) {
    try {
        const incomingData = JSON.parse(event.data);

        if (incomingData.room === roomId && incomingData.payload && incomingData.iv) {

            statusText.innerText = "🔓 Unlocking credentials...";
            statusText.className = "status-badge waiting";

            const decryptedString = await decryptPayload(incomingData.payload, incomingData.iv, sessionKey);

            if (decryptedString) {
                statusText.innerText = "✅ Password filled!";
                statusText.className = "status-badge success";

                let parsedPassword = "";
                let parsedTotp = null;

                // Detect if the payload is the new JSON bundle or the old plaintext string
                try {
                    const secureBundle = JSON.parse(decryptedString);
                    parsedPassword = secureBundle.password;
                    parsedTotp = secureBundle.totp || null;
                } catch (e) {
                    // Fallback just in case you test with the old Android app version
                    parsedPassword = decryptedString;
                }

                chrome.tabs.query({active: true, currentWindow: true}, function(tabs) {
                    chrome.scripting.executeScript({
                        target: {tabId: tabs[0].id},
                        func: injectCredentials,
                        // Pass the 2FA code as a 3rd argument!
                        args: [incomingData.username, parsedPassword, parsedTotp]
                    });
                });
            } else {
                statusText.innerText = "❌ Scan failed. Please try again.";
                statusText.className = "status-badge error";
            }
        }
    } catch (e) {
        console.error("JSON Parse Error", e);
    }
};


// --- 4. THE PROXIMITY HEURISTIC & 2FA TRIPWIRE ---

function injectCredentials(username, password, totpCode) {
    console.log("Ledger: Commencing payload injection sequence...");

    function firePasswordPayload() {
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
            return true;
        }
        return false;
    }

    // --- PHASE 3: The TOTP 2FA Tripwire ---
    function deployTotpTripwire() {
        if (!totpCode) return;
        console.warn("Ledger: 2FA Token loaded. Deploying TOTP Tripwire...");

        function tryInjectTotp() {
            const inputs = Array.from(document.querySelectorAll('input'));
            // Look for common 2FA field heuristics
            const totpField = inputs.find(input =>
                input.autocomplete === 'one-time-code' ||
                input.name.toLowerCase().includes('totp') ||
                input.name.toLowerCase().includes('code') ||
                input.id.toLowerCase().includes('totp') ||
                (input.type === 'text' && input.maxLength === 6)
            );

            if (totpField && totpField.style.display !== 'none') {
                totpField.value = totpCode;
                totpField.dispatchEvent(new Event('input', { bubbles: true }));
                totpField.dispatchEvent(new Event('change', { bubbles: true }));
                console.log("Ledger: TOTP Code Injected!");
                return true;
            }
            return false;
        }

        if (tryInjectTotp()) return;

        // If 2FA box isn't visible yet, wait for it (often loads on next screen)
        const totpObserver = new MutationObserver((mutations, obs) => {
            if (tryInjectTotp()) {
                obs.disconnect();
            }
        });

        totpObserver.observe(document.body, { childList: true, subtree: true });

        // Give the user 60 seconds to reach the 2FA screen before killing the watcher
        setTimeout(() => {
            totpObserver.disconnect();
            console.log("Ledger: TOTP Tripwire timed out.");
        }, 60000);
    }

    // --- PHASE 1: Immediate Strike ---
    if (firePasswordPayload()) {
        console.log("Ledger: Target Credentials Injected!");
        deployTotpTripwire(); // Deploy 2FA watcher immediately after password fills
        return;
    }

    // --- PHASE 2: The Phantom Field Hunt ---
    console.warn("Ledger: Password field missing. Deploying DOM Tripwire...");

    const allInputs = Array.from(document.querySelectorAll('input'));
    const possibleUserFields = allInputs.filter(input =>
        (input.type.toLowerCase() === 'text' || input.type.toLowerCase() === 'email') &&
        input.style.display !== 'none' && input.type !== 'hidden'
    );

    if (possibleUserFields.length > 0) {
        const userField = possibleUserFields[0];
        userField.value = username;
        userField.dispatchEvent(new Event('input', { bubbles: true }));
        userField.dispatchEvent(new Event('change', { bubbles: true }));
    }

    const observer = new MutationObserver((mutations, obs) => {
        const passCheck = document.querySelectorAll('input[type="password"]');
        if (passCheck.length > 0) {
            console.log("Ledger: Tripwire Snapped! Phantom field detected.");
            obs.disconnect();
            firePasswordPayload();
            deployTotpTripwire(); // Deploy 2FA watcher once password fills
        }
    });

    observer.observe(document.body, { childList: true, subtree: true });

    setTimeout(() => {
        observer.disconnect();
        console.log("Ledger: Tripwire timed out after 15 seconds.");
    }, 15000);
}