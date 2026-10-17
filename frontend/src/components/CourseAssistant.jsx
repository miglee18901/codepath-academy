import {useEffect, useRef, useState} from "react";
import {Link} from "react-router-dom";
import api, {errorMessage, session} from "../api";
import {Notice} from "../ui";
import "./CourseAssistant.css";

function restore(key) {
    try {
        const messages = JSON.parse(sessionStorage.getItem(key) || "[]");
        return Array.isArray(messages) ? messages.slice(-6).filter(m =>
            ["user", "assistant"].includes(m.role) && typeof m.content === "string") : [];
    } catch { return []; }
}

export default function CourseAssistant({courseId}) {
    const user = session();
    const key = `codepath:assistant:${user?.id}:${courseId}`;
    // Remount on identity/course changes to prevent carrying context between courses.
    if (!user) return <p><Link to="/login">Đăng nhập</Link> để hỏi trợ lý về nội dung khóa học.</p>;
    return <AssistantSession key={key} storageKey={key} courseId={courseId}/>;
}

function AssistantSession({storageKey, courseId}) {
    const [messages, setMessages] = useState(() => restore(storageKey));
    const [question, setQuestion] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const request = useRef(null);
    const messagesEnd = useRef(null);
    useEffect(() => {
        messagesEnd.current?.scrollIntoView({block: "nearest"});
    }, [messages, busy]);
    useEffect(() => () => request.current?.abort(), []);
    useEffect(() => {
        try { sessionStorage.setItem(storageKey, JSON.stringify(messages.slice(-6))); } catch { /* Storage may be disabled. */ }
    }, [messages, storageKey]);
    async function submit(event) {
        event.preventDefault();
        const text = question.trim();
        if (!text || busy) return;
        setBusy(true);
        setError("");
        const controller = new AbortController();
        request.current = controller;
        try {
            const {data} = await api.post(`/api/courses/${courseId}/assistant`, {
                question: text,
                history: messages.slice(-6).map(({role, content}) => ({role, content})),
            }, {signal: controller.signal, timeout: 300000});
            setMessages(previous => [...previous, {role: "user", content: text},
                {role: "assistant", content: data.answer, sources: data.sources}].slice(-6));
            setQuestion("");
        } catch (e) {
            if (e.code !== "ERR_CANCELED") setError(errorMessage(e));
        } finally {
            if (request.current === controller) {
                request.current = null;
                setBusy(false);
            }
        }
    }
    return <section className="course-assistant panel" aria-label="Trợ lý học tập">
        <h2>Trợ lý học tập</h2>
        <p>Hỏi về nội dung khóa học bạn được phép xem. Câu trả lời AI có thể sai; hãy kiểm tra nguồn.</p>
        <div className="assistant-messages" aria-live="polite" aria-busy={busy}>
            {messages.map((message, index) => <div className={`assistant-message ${message.role}`} key={index}>
                <strong>{message.role === "user" ? "Bạn" : "Trợ lý"}</strong>
                <p>{message.content}</p>
                {message.sources?.length > 0 && <ul aria-label="Nguồn tham khảo">
                    {message.sources.filter(s => /^\/(courses\/\d+|learn\/\d+\/\d+)$/.test(s.url)).map(source =>
                        <li key={source.id}><Link to={source.url}>[{source.id}] {source.title}</Link>
                            <p className="muted">{source.excerpt}</p></li>)}
                </ul>}
            </div>)}
            {busy && <p role="status">Đang tìm nội dung và soạn câu trả lời… Lần đầu chạy có thể mất vài phút.</p>}
            <div ref={messagesEnd}/>
        </div>
        <Notice>{error}</Notice>
        <form onSubmit={submit}>
            <label>Câu hỏi về khóa học
                <textarea value={question} onChange={e => setQuestion(e.target.value)} required
                    maxLength={2000} rows={3} disabled={busy} placeholder="Ví dụ: Bài học giải thích dependency injection như thế nào?"/>
            </label>
            <div className="toolbar">
                <button disabled={busy || !question.trim()}>Gửi câu hỏi</button>
                <button className="ghost" type="button" disabled={busy || !messages.length} onClick={() => setMessages([])}>Xóa hội thoại</button>
            </div>
        </form>
    </section>;
}
