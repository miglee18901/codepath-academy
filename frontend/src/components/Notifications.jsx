import {useEffect, useState} from "react";
import {Link} from "react-router-dom";
import api, {errorMessage} from "../api";
import {useLoad, date} from "../helpers";
import {Notice, Status} from "../ui";

export function NotificationBell() {
    const [count, setCount] = useState(0);
    useEffect(() => {
        let active = true;
        const controller = new AbortController();
        async function refresh() {
            if (document.hidden) return;
            try { const {data} = await api.get("/api/me/notifications/unread-count", {signal: controller.signal}); if (active) setCount(data.count); }
            catch { /* The notifications page provides an explicit retry on failure. */ }
        }
        refresh();
        const timer = setInterval(refresh, 30000);
        document.addEventListener("visibilitychange", refresh);
        window.addEventListener("codepath:notifications", refresh);
        return () => { active = false; controller.abort(); clearInterval(timer); document.removeEventListener("visibilitychange", refresh); window.removeEventListener("codepath:notifications", refresh); };
    }, []);
    return <Link className="notification-bell" to="/notifications" onClick={() => window.dispatchEvent(new Event("codepath:notifications"))}
                 aria-label={`Thông báo${count ? `, ${count} chưa đọc` : ""}`} title="Thông báo">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/></svg>
        {count > 0 && <span className="badge paid">{count > 99 ? "99+" : count}</span>}
    </Link>;
}

export default function Notifications() {
    const [page, setPage] = useState(0);
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const req = useLoad(() => api.get("/api/me/notifications", {params: {page, size: 20}}), [page]);
    async function read(id) {
        setBusy(true); setError("");
        try { await api.put(id ? `/api/me/notifications/${id}/read` : "/api/me/notifications/read-all"); req.refresh(); window.dispatchEvent(new Event("codepath:notifications")); }
        catch (e) { setError(errorMessage(e)); }
        finally { setBusy(false); }
    }
    return <section className="section"><div className="section-heading"><h1>Thông báo</h1><button disabled={busy} onClick={() => read()}>Đánh dấu tất cả đã đọc</button></div>
        <Notice>{error}</Notice><Status request={req}>
            {req.data?.content.length === 0 && <p className="empty">Bạn chưa có thông báo.</p>}
            <div className="notification-list">{req.data?.content.map(n => <article className="panel" key={n.id}>
                <div className="section-heading"><h2>{n.title}</h2>{!n.read_at && <span className="badge paid">Chưa đọc</span>}</div>
                <p>{n.message}</p><small className="muted">{date(n.created_at)}</small>
                <div className="toolbar"><Link to={/^\/(orders|learn\/\d+)$/.test(n.target) ? n.target : "/my-courses"} onClick={() => read(n.id)}>Xem chi tiết →</Link>
                    {!n.read_at && <button className="ghost" disabled={busy} onClick={() => read(n.id)}>Đánh dấu đã đọc</button>}</div>
            </article>)}</div>
            {req.data?.totalPages > 1 && <div className="toolbar"><button disabled={page === 0 || req.loading} onClick={() => setPage(page - 1)}>← Trước</button><span>Trang {page + 1}/{req.data.totalPages}</span><button disabled={page + 1 >= req.data.totalPages || req.loading} onClick={() => setPage(page + 1)}>Sau →</button></div>}
        </Status></section>;
}
