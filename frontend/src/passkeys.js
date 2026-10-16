import api, {errorMessage} from "./api";

export function supportsPasskeys() {
    return window.isSecureContext && !!window.PublicKeyCredential && !!navigator.credentials;
}

function decode(value) {
    const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
    return Uint8Array.from(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, "=")), c => c.charCodeAt(0));
}

function encode(value) {
    return btoa(String.fromCharCode(...new Uint8Array(value)))
        .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function serialize(credential) {
    if (!credential) throw new Error("Không nhận được Passkey. Vui lòng thử lại.");
    const response = {clientDataJSON: encode(credential.response.clientDataJSON)};
    for (const key of ["attestationObject", "authenticatorData", "signature", "userHandle"]) {
        if (credential.response[key] != null) response[key] = encode(credential.response[key]);
    }
    if (credential.response.getTransports) response.transports = credential.response.getTransports();
    return JSON.stringify({
        id: credential.id, rawId: encode(credential.rawId), type: credential.type,
        response, clientExtensionResults: credential.getClientExtensionResults(),
        ...(credential.authenticatorAttachment ? {authenticatorAttachment: credential.authenticatorAttachment} : {}),
    });
}

async function ceremony(path, input, registration) {
    if (!supportsPasskeys()) throw new Error("Trình duyệt chưa hỗ trợ Passkeys. Hãy dùng HTTPS hoặc localhost và trình duyệt mới.");
    const {data} = await api.post(path + "/options", input);
    const publicKey = data.options.publicKey;
    publicKey.challenge = decode(publicKey.challenge);
    if (registration) publicKey.user.id = decode(publicKey.user.id);
    for (const key of ["allowCredentials", "excludeCredentials"]) {
        if (publicKey[key]) publicKey[key] = publicKey[key].map(c => ({...c, id: decode(c.id)}));
    }
    const credential = registration
        ? await navigator.credentials.create({publicKey})
        : await navigator.credentials.get({publicKey});
    return (await api.post(path + "/verify", {requestId: data.requestId, credential: serialize(credential)})).data;
}

export const loginWithPasskey = () => ceremony("/api/auth/passkeys", {}, false);
export const registerPasskey = (label, password) => ceremony("/api/me/passkeys", {label, password}, true);

export function passkeyError(error) {
    if (error.name === "NotAllowedError" || error.name === "AbortError")
        return "Thao tác Passkey đã hủy hoặc hết thời gian. Bạn có thể thử lại hoặc đăng nhập bằng mật khẩu.";
    if (error.name === "InvalidStateError") return "Passkey này đã được đăng ký. Hãy chọn Passkey khác.";
    if (error.name === "SecurityError") return "Passkey không dùng được trên địa chỉ này. Hãy mở đúng tên miền đã đăng ký.";
    if (error.response) return errorMessage(error);
    return error.message || "Không thực hiện được Passkey. Vui lòng thử lại.";
}
