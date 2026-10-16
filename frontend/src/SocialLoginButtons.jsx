import {useState} from "react";
import api, {errorMessage} from "./api";
import {useLoad} from "./helpers";
import {Notice} from "./ui";
import {startSocialLogin} from "./socialAuth";

export default function SocialLoginButtons({next, disabled}) {
    const req = useLoad(() => api.get("/api/auth/oauth2/providers"));
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    async function start(id) {
        setBusy(true); setError("");
        try { await startSocialLogin(id, next); }
        catch (e) { setError(e.response ? errorMessage(e) : e.message); setBusy(false); }
    }
    return <div className="social-login">
        <p className="muted social-divider">Hoặc tiếp tục với</p>
        <Notice>{error || req.error}</Notice>
        <div className="social-buttons">
            {(req.data || [{id: "google", name: "Google"}, {id: "github", name: "GitHub"}]).map(p =>
                <button type="button" className="secondary" key={p.id} disabled={disabled || busy || !p.enabled}
                        onClick={() => start(p.id)} title={p.enabled ? undefined : p.name + " chưa được bật"}>
                    {p.name}
                </button>)}
        </div>
        {req.data && !req.data.some(p => p.enabled) && <p className="muted">Đăng nhập Google/GitHub hiện chưa được bật.</p>}
    </div>;
}
