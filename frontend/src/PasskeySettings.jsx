import {useState} from "react";
import api from "./api";
import {useLoad} from "./helpers";
import {Notice, Status} from "./ui";
import {registerPasskey, supportsPasskeys, passkeyError} from "./passkeys";

export default function PasskeySettings() {
    const req = useLoad(() => api.get("/api/me/passkeys"));
    const [label, setLabel] = useState("");
    const [password, setPassword] = useState("");
    const [removing, setRemoving] = useState(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    const supported = supportsPasskeys();

    async function submit(e) {
        e.preventDefault();
        setBusy(true);
        setError("");
        setMessage("");
        try {
            if (removing) {
                await api.delete("/api/me/passkeys/" + removing.id, {data: {password}});
                setMessage("Đã xóa Passkey khỏi tài khoản. Bạn có thể xóa bản lưu trong trình quản lý Passkeys của thiết bị.");
            } else {
                await registerPasskey(label, password);
                setMessage("Đã thêm Passkey. Bạn có thể dùng Passkey tại trang đăng nhập.");
                setLabel("");
            }
            setRemoving(null);
            req.refresh();
        } catch (e) {
            setError(passkeyError(e));
        } finally {
            setPassword("");
            setBusy(false);
        }
    }

    return <div className="panel passkey-settings">
        <h2>Passkeys</h2>
        <p>Dùng vân tay, khuôn mặt hoặc PIN của thiết bị để đăng nhập. Bạn vẫn có thể đăng nhập bằng mật khẩu khi không có Passkey.</p>
        <Notice>{error}</Notice>
        <Notice success>{message}</Notice>
        {!supported && <p role="status">Thêm Passkey cần trình duyệt hỗ trợ và kết nối HTTPS hoặc localhost.</p>}
        <Status request={req}>
            {req.data?.length === 0 && <p className="muted">Bạn chưa có Passkey.</p>}
            {req.data?.map(p => <div className="profile-row" key={p.id}>
                <div><strong>{p.label}</strong><br/>
                    <small>Tạo: {new Date(p.createdAt + "Z").toLocaleString("vi-VN")}{p.lastUsedAt
                        ? " · Dùng gần nhất: " + new Date(p.lastUsedAt + "Z").toLocaleString("vi-VN") : " · Chưa sử dụng"}</small>
                </div>
                <button className="small secondary" disabled={busy} onClick={() => {
                    setRemoving(p); setPassword(""); setError(""); setMessage("");
                }}>Xóa</button>
            </div>)}
        </Status>
        <form onSubmit={submit}>
            {removing ? <p>Xác nhận xóa Passkey <strong>{removing.label}</strong>.</p> : <label>
                Tên Passkey
                <input value={label} onChange={e => setLabel(e.target.value)} required maxLength={80}
                       disabled={busy || !supported} placeholder="Ví dụ: Windows Hello cá nhân"/>
            </label>}
            <label>Mật khẩu hiện tại để xác nhận
                <input type="password" value={password} onChange={e => setPassword(e.target.value)} required
                       maxLength={64} autoComplete="current-password" disabled={busy || (!removing && !supported)}/>
            </label>
            <button disabled={busy || (!removing && !supported) || (!removing && req.data?.length >= 10)}>
                {busy ? "Đang xử lý…" : removing ? "Xác nhận xóa Passkey" : "Thêm Passkey"}
            </button>
            {removing && <button type="button" className="secondary" disabled={busy} onClick={() => {
                setRemoving(null); setPassword("");
            }}>Hủy</button>}
        </form>
    </div>;
}
