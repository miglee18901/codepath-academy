import {useState} from "react";
import api, {errorMessage} from "../api";
import {useLoad, date} from "../helpers";
import {Notice, Status} from "../ui";

const currency = n => new Intl.NumberFormat("vi-VN", {style: "currency", currency: "VND"}).format(n || 0);
const day = offset => { const d = new Date(); d.setDate(d.getDate() + offset); return new Intl.DateTimeFormat("en-CA", {timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit"}).format(d); };

export function BusinessReports() {
    const [filters, setFilters] = useState({from: day(-29), to: day(0), courseId: ""});
    const [query, setQuery] = useState(filters);
    const [group, setGroup] = useState("daily");
    const params = {...query, courseId: query.courseId || undefined};
    const overview = useLoad(() => api.get("/api/admin/reports/overview", {params}), [query]);
    const rows = useLoad(() => api.get(`/api/admin/reports/${group}`, {params}), [query, group]);
    const courses = useLoad(() => api.get("/api/admin/courses"));
    const totals = overview.data?.content[0];
    return <div className="panel"><h2>Báo cáo doanh thu và học tập</h2>
        <form className="toolbar business-filters" onSubmit={e => {e.preventDefault(); setQuery({...filters});}}>
            <label>Từ ngày<input type="date" required value={filters.from} onChange={e => setFilters({...filters, from: e.target.value})}/></label>
            <label>Đến ngày<input type="date" required value={filters.to} onChange={e => setFilters({...filters, to: e.target.value})}/></label>
            <label>Khóa học<select value={filters.courseId} onChange={e => setFilters({...filters, courseId: e.target.value})}><option value="">Tất cả</option>{courses.data?.map(c => <option value={c.id} key={c.id}>{c.title}</option>)}</select></label><button>Xem báo cáo</button>
        </form>
        <Status request={overview}>{totals && <div className="stats-grid">{[["Doanh thu", currency(totals.revenue)], ["Đơn đã thanh toán", totals.paid_orders], ["Lượt đăng ký", totals.enrollments], ["Lượt hoàn thành mới", totals.completions], ["Hoàn thành lịch sử", totals.historical_completions]].map(([label, value]) => <div className="stat" key={label}><small>{label}</small><strong>{value}</strong></div>)}</div>}</Status>
        <p className="muted">Ngày báo cáo: Việt Nam (UTC+7). Cập nhật: {date(overview.data?.updatedAt)}. Hoàn thành lịch sử được ghi nhận lúc nhập, không phải ngày hoàn thành thực tế.</p>
        {overview.data?.backfill.some(j => !j.done) && <Notice success>Đang nhập dữ liệu lịch sử. Số liệu sẽ được bổ sung tự động.</Notice>}
        <div className="toolbar"><label>Nhóm theo<select value={group} onChange={e => setGroup(e.target.value)}><option value="daily">Ngày</option><option value="courses">Khóa học</option></select></label><button className="secondary" onClick={() => {overview.refresh(); rows.refresh();}}>Làm mới</button></div>
        <Status request={rows}><div className="table-wrap"><table><thead><tr><th>{group === "daily" ? "Ngày" : "Khóa học"}</th><th>Doanh thu</th><th>Đơn thanh toán</th><th>Đăng ký</th><th>Hoàn thành mới</th><th>Lịch sử</th></tr></thead><tbody>{rows.data?.content.map(r => <tr key={r.bucket}><td>{r.title || String(r.bucket).slice(0, 10)}</td><td>{currency(r.revenue)}</td><td>{r.paid_orders}</td><td>{r.enrollments}</td><td>{r.completions}</td><td>{r.historical_completions}</td></tr>)}</tbody></table></div>{rows.data?.content.length === 0 && <p>Chưa có dữ liệu trong khoảng ngày này.</p>}</Status>
    </div>;
}

export function BusinessEvents() {
    const [page, setPage] = useState(0);
    const [emailPage, setEmailPage] = useState(0);
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const status = useLoad(() => api.get("/api/admin/events/status"));
    const failures = useLoad(() => api.get("/api/admin/events/failures", {params: {page}}), [page]);
    const emails = useLoad(() => api.get("/api/admin/events/emails", {params: {page: emailPage}}), [emailPage]);
    function refresh() {status.refresh(); failures.refresh(); emails.refresh();}
    async function retry(kind, id) {
        setError(""); setBusy(true);
        try {await api.post(`/api/admin/events/${kind}/${id}/retry`); refresh();}
        catch (e) {setError(errorMessage(e));}
        finally {setBusy(false);}
    }
    return <div className="panel"><div className="section-heading"><h2>Vận hành sự kiện</h2><button onClick={refresh}>Làm mới</button></div><Notice>{error}</Notice>
        <Status request={status}><p>Outbox chờ gửi: <strong>{status.data?.outbox.pending}</strong> · Tuổi lâu nhất: {status.data?.outbox.oldest_seconds} giây</p>
            <p>Email: {status.data?.email.map(s => `${s.status}: ${s.count}`).join(" · ") || "Chưa có"}</p></Status>
        <h3>Sự kiện lỗi và xử lý lại</h3><Status request={failures}><div className="table-wrap"><table><thead><tr><th>Consumer</th><th>Mã sự kiện</th><th>Lỗi</th><th>Trạng thái</th><th/></tr></thead><tbody>{failures.data?.content.map(f => <tr key={f.id}><td>{f.consumer}</td><td>{f.event_id}</td><td>{f.error_code}</td><td>{f.status}</td><td><button disabled={busy || f.status !== "FAILED"} onClick={() => retry("failures", f.id)}>Thử lại</button></td></tr>)}</tbody></table></div><Pager data={failures.data} page={page} setPage={setPage}/></Status>
        <h3>Email gửi thất bại</h3><Status request={emails}><div className="table-wrap"><table><thead><tr><th>Mã</th><th>Số lần thử</th><th>Lỗi</th><th/></tr></thead><tbody>{emails.data?.content.map(e => <tr key={e.id}><td>{e.id}</td><td>{e.attempts}</td><td>{e.last_error}</td><td><button disabled={busy} onClick={() => retry("emails", e.id)}>Gửi lại</button></td></tr>)}</tbody></table></div><Pager data={emails.data} page={emailPage} setPage={setEmailPage}/></Status>
    </div>;
}
function Pager({data, page, setPage}) { return data?.totalPages > 1 && <div className="toolbar"><button disabled={page === 0} onClick={() => setPage(page - 1)}>← Trước</button><span>{page + 1}/{data.totalPages}</span><button disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Sau →</button></div>; }
