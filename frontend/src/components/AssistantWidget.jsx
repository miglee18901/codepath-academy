import {useEffect, useRef, useState} from "react";
import {Link, matchPath, useLocation} from "react-router-dom";
import api, {errorMessage, session} from "../api";
import {useLoad} from "../helpers";
import {Notice} from "../ui";
import CourseAssistant from "./CourseAssistant";
import "./AssistantWidget.css";

export default function AssistantWidget() {
    const capabilities = useLoad(() => api.get("/api/ai/capabilities"));
    const [open, setOpen] = useState(false);
    const [visited, setVisited] = useState(false);
    const launcher = useRef(null);
    const closeButton = useRef(null);
    function close() {
        setOpen(false);
        launcher.current?.focus();
    }
    useEffect(() => {
        if (!open) return;
        closeButton.current?.focus();
        function escape(event) {
            if (event.key === "Escape") {
                setOpen(false);
                launcher.current?.focus();
            }
        }
        document.addEventListener("keydown", escape);
        return () => document.removeEventListener("keydown", escape);
    }, [open]);
    if (!capabilities.data?.assistant && !capabilities.data?.search) return null;
    return <aside className="assistant-widget" aria-label="Chatbot CodePath">
        {visited && <div id="codepath-chatbot" role="dialog" aria-modal="false" aria-labelledby="chatbot-title"
            className="assistant-window" hidden={!open}>
            <div className="assistant-window-heading">
                <div><h2 id="chatbot-title">Trợ lý CodePath</h2><span>Tìm khóa học · Học cùng AI</span></div>
                <button ref={closeButton} type="button" onClick={close} aria-label="Đóng chatbot">×</button>
            </div>
            <WidgetContent capabilities={capabilities.data}/>
        </div>}
        <button ref={launcher} className="assistant-launcher" aria-label={open ? "Thu gọn chatbot" : "Mở chatbot"}
            aria-expanded={open} aria-controls={visited ? "codepath-chatbot" : undefined}
            onClick={() => { if (open) close(); else { setVisited(true); setOpen(true); } }}>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
                <path d="M20 11.5a8 8 0 0 1-8 8H5l-3 2v-10a9 9 0 0 1 18 0Z"/>
                <path d="M7 10h8M7 14h5"/>
            </svg>
            <span>Trợ lý AI</span>
        </button>
    </aside>;
}

function WidgetContent({capabilities}) {
    const {pathname, search} = useLocation();
    const user = session();
    const routeCourse = matchPath("/courses/:id", pathname)?.params.id
        || matchPath("/learn/:courseId/:lessonId?", pathname)?.params.courseId;
    const [selected, setSelected] = useState(null);
    const [tab, setTab] = useState(capabilities.assistant ? "assistant" : "search");
    const courseId = routeCourse || selected?.id;
    const course = useLoad(() => courseId ? api.get(`/api/courses/${courseId}`) : Promise.resolve({data: null}), [courseId, user?.id]);
    const library = useLoad(() => user ? api.get("/api/me/courses") : Promise.resolve({data: []}), [user?.id]);
    return <div className="assistant-window-body">
        <div className="assistant-modes" role="group" aria-label="Chế độ trợ lý">
            {capabilities.assistant && <button aria-pressed={tab === "assistant"} onClick={() => setTab("assistant")}>Hỏi khóa học</button>}
            {capabilities.search && <button aria-pressed={tab === "search"} onClick={() => setTab("search")}>Tìm khóa học</button>}
        </div>
        <div hidden={tab !== "search"}>
            {capabilities.search && <CourseFinder onSelect={c => { setSelected(c); setTab("assistant"); }}
                canAsk={capabilities.assistant && !routeCourse}/>}
        </div>
        <div hidden={tab !== "assistant"}>
            {!user ? <p><Link to={`/login?next=${encodeURIComponent(pathname + search)}`}>Đăng nhập</Link> để hỏi nội dung khóa học. Bạn có thể tìm khóa học mà chưa cần đăng nhập.</p>
                : <>
                    {routeCourse ? <p className="assistant-context">Đang hỗ trợ khóa học trên trang này.</p>
                        : <label>Khóa học đang hỏi
                            <select value={selected?.id || ""} onChange={e => {
                                setSelected(library.data?.find(c => String(c.id) === e.target.value) || null);
                            }}>
                                <option value="">Chọn khóa học</option>
                                {selected && !library.data?.some(c => String(c.id) === String(selected.id)) && <option value={selected.id}>{selected.title}</option>}
                                {library.data?.map(c => <option key={c.id} value={c.id}>{c.title}</option>)}
                            </select>
                        </label>}
                    {!routeCourse && capabilities.search && <p className="muted">Chọn khóa đã đăng ký hoặc dùng “Tìm khóa học” để chọn khóa khác.</p>}
                    <Notice>{library.error}</Notice>
                    {courseId && course.loading && <p role="status">Đang tải khóa học…</p>}
                    <Notice>{course.error}</Notice>
                    {courseId && !course.loading && course.data && <>
                        <h3 className="assistant-course-title">{course.data.title}</h3>
                        <CourseAssistant key={`${user.id}:${courseId}`} courseId={courseId}/>
                    </>}
                </>}
        </div>
    </div>;
}

function CourseFinder({onSelect, canAsk}) {
    const [query, setQuery] = useState("");
    const [results, setResults] = useState(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const request = useRef(null);
    useEffect(() => () => request.current?.abort(), []);
    async function find(event) {
        event.preventDefault();
        setBusy(true); setError(""); setResults(null);
        const controller = new AbortController();
        request.current = controller;
        try {
            const {data} = await api.get("/api/search/courses", {
                params: {q: query.trim(), offset: 0, limit: 5}, signal: controller.signal, timeout: 180000,
            });
            setResults(data);
        } catch (e) {
            if (e.code !== "ERR_CANCELED") setError(errorMessage(e));
        } finally { setBusy(false); }
    }
    return <section aria-label="Tìm khóa học trong chatbot">
        <p>Mô tả điều bạn muốn học để tìm khóa phù hợp.</p>
        <form className="assistant-find" onSubmit={find}>
            <label>Bạn muốn học gì?<input value={query} onChange={e => setQuery(e.target.value)} maxLength={500}
                placeholder="Ví dụ: xây dựng API với Java" disabled={busy}/></label>
            <button disabled={busy}>{busy ? "Đang tìm…" : "Tìm khóa phù hợp"}</button>
        </form>
        <Notice>{error}</Notice>
        <div aria-live="polite" aria-busy={busy}>
            {results?.mode === "keyword" && <p className="muted">Đang dùng tìm kiếm từ khóa.</p>}
            {results?.content.length === 0 && <p>Chưa tìm thấy khóa phù hợp. Hãy thử mô tả khác.</p>}
            {results?.content.map(c => <article className="assistant-course-result" key={c.id}>
                <Link to={`/courses/${c.id}`}>{c.title}</Link>
                <p>{c.description?.slice(0, 180)}</p>
                {canAsk && <button className="secondary" onClick={() => onSelect(c)}>Hỏi về khóa này</button>}
            </article>)}
            {results?.hasMore && <Link to={`/?q=${encodeURIComponent(query.trim())}&mode=smart`}>Xem thêm khóa học →</Link>}
        </div>
    </section>;
}
