import {apiBase} from "./api";

export function safeNext(path, fallback = "/my-courses") {
    return path?.startsWith("/") && !path.startsWith("//") && !path.includes("\\")
        && !Array.from(path).some(c => c.charCodeAt(0) < 32)
        ? path : fallback;
}

export async function startSocialLogin(provider, next, ticket) {
    if (!window.isSecureContext || !window.crypto?.subtle)
        throw new Error("Đăng nhập Google/GitHub cần HTTPS hoặc localhost.");
    const bytes = crypto.getRandomValues(new Uint8Array(32));
    const encode = buffer => btoa(String.fromCharCode(...new Uint8Array(buffer)))
        .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
    const verifier = encode(bytes);
    const challenge = encode(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
    sessionStorage.setItem("oauth_flow", JSON.stringify({verifier, next: safeNext(next)}));
    const query = new URLSearchParams({challenge});
    if (ticket) query.set("link", ticket);
    window.location.assign(apiBase + "/api/auth/oauth2/authorization/" + encodeURIComponent(provider) + "?" + query);
}

export function socialError(error) {
    return {
        access_denied: "Bạn đã hủy cấp quyền đăng nhập. Có thể thử lại hoặc dùng mật khẩu/Passkey.",
        social_email_unverified: "Cần email đã xác minh. Với GitHub, hãy xác minh email chính rồi thử lại.",
        social_email_exists: "Email này đã thuộc tài khoản hiện có. Hãy đăng nhập tài khoản đó rồi liên kết Google/GitHub trong Hồ sơ cá nhân.",
        social_already_linked: "Tài khoản Google/GitHub này đã được liên kết. Hãy dùng đúng tài khoản hoặc nhà cung cấp khác.",
        social_conflict: "Thông tin tài khoản vừa thay đổi. Vui lòng đăng nhập lại; nếu email đã tồn tại, hãy liên kết từ Hồ sơ cá nhân.",
    }[error] || "Không hoàn tất được đăng nhập Google/GitHub. Vui lòng thử lại.";
}
