import {useState} from "react";
import api, {errorMessage} from "./api";
import {useLoad} from "./helpers";
import {Notice, Status} from "./ui";
import {startSocialLogin} from "./socialAuth";

export default function SocialAccountSettings({hasPassword, onPasswordSet}) {
    const links = useLoad(() => api.get("/api/me/social-links"));
    const providers = useLoad(() => api.get("/api/auth/oauth2/providers"));
    const [password, setPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmation, setConfirmation] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    async function link(provider) {
        setBusy(true); setError(""); setMessage("");
        try {
            const {data} = await api.post("/api/me/social-links/" + provider, {password});
            setPassword("");
            await startSocialLogin(provider, "/profile", data.ticket);
        } catch (e) { setError(e.response ? errorMessage(e) : e.message); setBusy(false); }
    }
    async function setInitialPassword(e) {
        e.preventDefault(); setError(""); setMessage("");
        if (newPassword !== confirmation) { setError("Mật khẩu xác nhận chưa trùng khớp."); return; }
        setBusy(true);
        try {
            await api.post("/api/me/initial-password", {password: newPassword});
            setMessage("Đã thiết lập mật khẩu. Bạn có thể đăng nhập bằng mật khẩu và thêm Passkeys.");
            onPasswordSet();
        } catch (e) { setError(errorMessage(e)); }
        finally { setNewPassword(""); setConfirmation(""); setBusy(false); }
    }
    return <div className="panel social-settings">
        <h2>Tài khoản liên kết</h2>
        <p>Đăng nhập vào cùng tài khoản CodePath bằng Google hoặc GitHub.</p>
        <Notice>{error || providers.error}</Notice><Notice success>{message}</Notice>
        <Status request={links}>
            {links.data?.map(p => <div className="profile-row" key={p.provider}>
                <strong>{p.provider === "google" ? "Google" : "GitHub"}</strong>
                <span>{p.email} · Đã liên kết</span>
            </div>)}
            {links.data?.length === 0 && <p className="muted">Chưa liên kết Google/GitHub.</p>}
        </Status>
        {hasPassword ? <label>Mật khẩu hiện tại để liên kết
            <input type="password" autoComplete="current-password" maxLength={64} value={password}
                   disabled={busy} onChange={e => setPassword(e.target.value)}/>
        </label> : <p className="muted">Liên kết thêm tài khoản và thiết lập mật khẩu trong vòng 5 phút sau khi đăng nhập bằng Google/GitHub.</p>}
        <div className="social-buttons">
            {providers.data?.map(p => <button type="button" className="secondary" key={p.id}
                disabled={busy || !p.enabled || !links.data || links.data.some(l => l.provider === p.id)}
                onClick={() => link(p.id)}>Liên kết {p.name}</button>)}
        </div>
        {providers.data && !providers.data.some(p => p.enabled) && <p className="muted">Đăng nhập Google/GitHub hiện chưa được bật.</p>}
        {!hasPassword && <form onSubmit={setInitialPassword}>
            <h3>Thiết lập mật khẩu</h3>
            <p>Tạo mật khẩu để có thêm cách đăng nhập và quản lý Passkeys.</p>
            <label>Mật khẩu mới (8–64 ký tự)
                <input type="password" autoComplete="new-password" required minLength={8} maxLength={64}
                       value={newPassword} disabled={busy} onChange={e => setNewPassword(e.target.value)}/>
            </label>
            <label>Nhập lại mật khẩu mới
                <input type="password" autoComplete="new-password" required minLength={8} maxLength={64}
                       value={confirmation} disabled={busy} onChange={e => setConfirmation(e.target.value)}/>
            </label>
            <button disabled={busy}>Thiết lập mật khẩu</button>
        </form>}
    </div>;
}
