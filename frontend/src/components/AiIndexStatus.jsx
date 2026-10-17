import {useState} from "react";
import api, {errorMessage} from "../api";
import {useLoad} from "../helpers";
import {Notice, Status} from "../ui";

export default function AiIndexStatus() {
    const state = useLoad(() => api.get("/api/admin/ai/index"));
    const capabilities = useLoad(() => api.get("/api/ai/capabilities"));
    const [message, setMessage] = useState("");
    const [busy, setBusy] = useState(false);
    async function reindex(courseId) {
        setBusy(true);
        setMessage("");
        try {
            const {data} = await api.post("/api/admin/ai/reindex", null, {params: {courseId}});
            setMessage(data.message);
            state.refresh();
        } catch (e) { setMessage(errorMessage(e)); }
        finally { setBusy(false); }
    }
    return <section className="panel">
        <h2>Chỉ mục AI</h2>
        <p>Tìm kiếm: {capabilities.data?.search ? "đã bật" : "đang tắt"} · Trợ lý: {capabilities.data?.assistant ? "đã bật" : "đang tắt"}</p>
        <Notice>{message}</Notice>
        <div className="toolbar"><button disabled={busy} onClick={() => reindex()}>Lập lại toàn bộ chỉ mục</button>
            <button className="secondary" onClick={state.refresh}>Cập nhật trạng thái</button></div>
        <Status request={state}>
            <p>Đang chờ: {state.data?.backlog} · Đã hết lượt thử: {state.data?.failed}</p>
            <p>Hiển thị tối đa 200 khóa. Worker phải được bật để xử lý các yêu cầu.</p>
            <div className="table-wrap"><table><thead><tr><th>Khóa</th><th>Phiên bản / đã lập chỉ mục</th><th>Lần thử lỗi</th><th>Trạng thái</th><th/></tr></thead>
                <tbody>{state.data?.jobs.map(job => <tr key={job.course_id}>
                    <td>#{job.course_id}</td><td>{job.revision} / {job.indexed_revision}</td><td>{job.attempts}</td>
                    <td>{job.last_error || (job.revision === job.indexed_revision ? "Sẵn sàng" : "Đang chờ")}</td>
                    <td><button className="ghost" disabled={busy} onClick={() => reindex(job.course_id)}>Lập lại / Thử lại</button></td>
                </tr>)}</tbody></table></div>
        </Status>
    </section>;
}
