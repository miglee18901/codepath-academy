import {useEffect, useRef, useState} from "react";
import {Link, useNavigate} from "react-router-dom";
import api, {errorMessage, saveSession} from "../api";
import {Notice} from "../ui";
import {safeNext, socialError} from "../socialAuth";

export default function OAuthCallback() {
    const navigate = useNavigate();
    const pending = useRef(null);
    const [error, setError] = useState("");
    const [input] = useState(() => {
        let flow;
        try { flow = JSON.parse(sessionStorage.getItem("oauth_flow") || "null"); } catch { /* restart login below */ }
        const hash = new URLSearchParams(window.location.hash.slice(1));
        return {code: hash.get("code"), error: hash.get("error"), flow};
    });
    useEffect(() => {
        let active = true;
        // Remove the one-time code before making any other requests or rendering links.
        window.history.replaceState(null, "", "/oauth/callback");
        sessionStorage.removeItem("oauth_flow");
        if (input.error || !input.code || !input.flow?.verifier) {
            setError(input.error ? socialError(input.error) : "Phiên đăng nhập không hợp lệ. Hãy bắt đầu lại từ trang đăng nhập.");
            return;
        }
        // React StrictMode must not consume the same server code twice.
        if (!pending.current) pending.current = api.post("/api/auth/oauth2/exchange", {
            code: input.code, verifier: input.flow.verifier,
        });
        pending.current.then(({data}) => {
            if (!active) return;
            saveSession(data);
            navigate(safeNext(input.flow.next), {replace: true});
        }).catch(e => { if (active) setError(errorMessage(e)); });
        return () => { active = false; };
    }, [input, navigate]);
    return <section className="section narrow"><div className="panel">
        <h1>{error ? "Chưa thể đăng nhập" : "Đang hoàn tất đăng nhập…"}</h1>
        <Notice>{error}</Notice>
        {error && <Link className="button" to="/login">Quay lại đăng nhập</Link>}
    </div></section>;
}
