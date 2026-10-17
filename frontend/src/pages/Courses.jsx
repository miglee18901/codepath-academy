import {useEffect, useState} from "react";
import {Link, useNavigate, useParams, useSearchParams} from "react-router-dom";
import api, {errorMessage} from "../api";
import {Notice, Status, CourseCard, Curriculum, Pagination} from "../ui";
import {useLoad, money, categoryColor} from "../helpers";
import {DirectLessonVideo, MuxLessonVideo} from "../components/MuxVideo";
import {localResumeLesson} from "../components/useVideoProgress";

export function Catalog() {
    const [params, setParams] = useSearchParams();
    const search = params.get("q") || "";
    const category = params.get("categoryId") || "";
    const smart = params.get("mode") === "smart";
    const capabilities = useLoad(() => api.get("/api/ai/capabilities"));
    const requestedPage = Number(params.get("page") || 1);
    const rawPage = Number.isInteger(requestedPage) && requestedPage > 0 && requestedPage <= 2147483647
        ? requestedPage - 1 : 0;
    const page = smart ? Math.min(rawPage, 4) : rawPage;
    const [q, setQ] = useState(search);
    useEffect(() => setQ(search), [search]);
    const categories = useLoad(() => api.get("/api/categories"));
    const req = useLoad(
        () =>
            api.get(smart ? "/api/search/courses" : "/api/courses", {
                params: smart
                    ? {q: search, categoryId: category || undefined, offset: Math.min(page * 12, 48), limit: 12}
                    : {q: search, categoryId: category || undefined, page, size: 12},
            }),
        [search, category, page, smart],
    );
    function filter(key, value) {
        const next = new URLSearchParams(params);
        if (value) next.set(key, value);
        else next.delete(key);
        next.delete("page");
        setParams(next);
    }
    function changePage(index) {
        const next = new URLSearchParams(params);
        if (index === 0) next.delete("page");
        else next.set("page", String(index + 1));
        setParams(next);
        document.getElementById("catalog")?.scrollIntoView({behavior: "smooth"});
    }
    useEffect(() => {
        if (smart || req.loading || !req.data || req.data.page !== page) return;
        const last = Math.max(0, req.data.totalPages - 1);
        if (page > last) {
            const next = new URLSearchParams(params);
            if (last === 0) next.delete("page");
            else next.set("page", String(last + 1));
            setParams(next, {replace: true});
        }
    }, [req.loading, req.data, page, params, setParams, smart]);
    return (
        <>
            <section className="hero">
                <div className="hero-copy">
                    <span className="pill">LỘ TRÌNH CHO NGƯỜI MUỐN BẮT ĐẦU</span>
                    <h1>
                        Biến ý tưởng thành
                        <br/>
                        <em>dòng code đầu tiên.</em>
                    </h1>
                    <p>
                        Khóa học lập trình có cấu trúc, ví dụ thực tế và bài học từng bước.
                        Chọn điểm xuất phát, học theo nhịp của bạn.
                    </p>
                    <a className="button" href="#catalog">
                        Khám phá khóa học ↗
                    </a>
                    <div className="hero-note">
                        <span>✓ Nội dung theo chương</span>
                        <span>✓ Theo dõi tiến độ</span>
                        <span>✓ Có bài học xem thử</span>
                    </div>
                </div>
                <div className="code-window">
                    <div className="window-head">
                        <i/>
                        <i/>
                        <i/>
                        <span>your-next-chapter.js</span>
                    </div>
                    <pre>
            <span className="muted">// Mỗi hành trình bắt đầu từ một bước</span>
                        {"\n\n"}
                        <span className="purple">const</span> developer = {"{\n"}
                        {"  "}name: <span className="yellow">'You'</span>,{"\n"}
                        {"  "}curiosity: <span className="yellow">'unlimited'</span>,{"\n"}
                        {"  "}skills: [<span className="yellow">'learning'</span>]
                        {"\n};\n\n"}
                        <span className="purple">function</span>{" "}
                        <span className="mint">buildYourFuture</span>() {"{\n"}
                        {"  "}learn();{"\n"}
                        {"  "}practice();{"\n"}
                        {"  "}
                        <span className="purple">return</span>{" "}
                        <span className="yellow">'something great'</span>;{"\n}"}
          </pre>
                    <div className="terminal">
                        <span>›</span> Hành trình mới đã sẵn sàng
                        <span className="terminal-dot"/>
                    </div>
                </div>
            </section>
            <section className="catalog section" id="catalog">
                <div className="section-heading">
                    <div>
                        <div className="eyebrow">HỌC HÔM NAY, XÂY NGÀY MAI</div>
                        <h2>Chọn hướng đi của bạn</h2>
                    </div>
                    <form
                        className="search"
                        onSubmit={(e) => {
                            e.preventDefault();
                            filter("q", q.trim());
                        }}
                    >
                        <input
                            aria-label="Tìm khóa học"
                            placeholder="Tìm khóa học…"
                            value={q}
                            onChange={(e) => setQ(e.target.value)}
                        />
                        <button>Tìm kiếm</button>
                        {(capabilities.data?.search || smart) && <select aria-label="Chế độ tìm kiếm"
                            value={smart ? "smart" : "keyword"} onChange={e => filter("mode", e.target.value === "smart" ? "smart" : "")}>
                            <option value="keyword">Từ khóa</option><option value="smart">Thông minh</option>
                        </select>}
                    </form>
                </div>
                <div className="filters">
                    <button
                        className={!category ? "active" : ""}
                        onClick={() => filter("categoryId", "")}
                    >
                        Tất cả khóa học
                    </button>
                    {categories.data?.map((c) => (
                        <button
                            key={c.id}
                            className={category === String(c.id) ? "active" : ""}
                            onClick={() => filter("categoryId", String(c.id))}
                        >
                            {c.name}
                        </button>
                    ))}
                </div>
                <Notice>{categories.error}</Notice>
                <Status request={req}>
                    <div className="course-grid" aria-busy={req.loading}>
                        {req.data?.content.map((c) => (
                            <CourseCard key={c.id} course={c}/>
                        ))}
                    </div>
                    {smart ? <div className="toolbar">
                        <button className="secondary" disabled={page === 0 || req.loading} onClick={() => changePage(Math.max(0, page - 1))}>Trang trước</button>
                        <span>Trang {Math.min(page, 4) + 1} · Tối đa 60 kết quả phù hợp</span>
                        <button className="secondary" disabled={!req.data?.hasMore || page >= 4 || req.loading} onClick={() => changePage(page + 1)}>Trang sau</button>
                        {req.data?.mode === "keyword" && <span>Đang dùng tìm kiếm từ khóa.</span>}
                    </div> : <Pagination data={req.data} onChange={changePage} loading={req.loading}/>}
                    {req.data?.content.length === 0 && (
                        <div className="empty">
                            Chưa có khóa học phù hợp. Hãy thử từ khóa hoặc danh mục khác.
                        </div>
                    )}
                </Status>
            </section>
            <section className="benefits section">
                <div>
                    <span>01 / HỌC CÓ ĐỊNH HƯỚNG</span>
                    <h3>Từng chương, từng bước.</h3>
                    <p>Nội dung được sắp xếp để bạn luôn biết nên học gì tiếp theo.</p>
                </div>
                <div>
                    <span>02 / HIỂU QUA THỰC HÀNH</span>
                    <h3>Từ kiến thức đến sản phẩm.</h3>
                    <p>Đọc bài, xem ví dụ và áp dụng vào những bài toán cụ thể.</p>
                </div>
                <div>
                    <span>03 / NHÌN THẤY TIẾN BỘ</span>
                    <h3>Giữ nhịp học mỗi ngày.</h3>
                    <p>Lưu tiến độ và quay lại đúng nơi bạn đã dừng.</p>
                </div>
            </section>
        </>
    );
}

export function CourseDetail() {
    const {id} = useParams();
    const navigate = useNavigate();
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const req = useLoad(() => api.get("/api/courses/" + id), [id]);
    const purchases = useLoad(() => localStorage.getItem("token")
        ? api.get("/api/orders") : Promise.resolve({data: []}), [id]);
    const c = req.data;
    const pending = purchases.data?.find(o => o.status === "PENDING"
        && o.items.some(item => String(item.courseId) === String(id)));

    async function buy() {
        if (!localStorage.getItem("token")) {
            navigate("/login?next=" + encodeURIComponent("/courses/" + id));
            return;
        }
        setBusy(true);
        setError("");
        try {
            const {data} = await api.post("/api/orders", {courseId: Number(id)});
            navigate(
                data.status === "PAID" ? "/learn/" + id : "/orders?order=" + data.id,
            );
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setBusy(false);
        }
    }

    return (
        <div className="section">
            <Status request={req}>
                {c && (
                    <>
                        <Link className="back" to="/">
                            ← Tất cả khóa học
                        </Link>
                        <div className="detail-grid">
                            <div>
                                <div className="eyebrow">
                                    {c.categoryName} / {c.level}
                                </div>
                                <h1 className="page-title">{c.title}</h1>
                                <p className="lead">{c.description}</p>
                                <p className="muted">
                                    Giảng viên: {c.instructor} · {c.lessonCount} bài học ·{" "}
                                    {c.durationMinutes} phút
                                </p>
                                <h2 className="spaced">Nội dung khóa học</h2>
                                <Curriculum course={c}/>
                            </div>
                            <aside className="purchase-panel">
                                <div className={"purchase-art course-art " + categoryColor(c)}>
                                    <span className="art-code">&lt;/&gt;</span>
                                </div>
                                <h2>{money(c.price)}</h2>
                                <p>Học trực tuyến theo tốc độ của bạn.</p>
                                <Notice>{error}</Notice>
                                {c.enrolled ? (
                                    <Link className="button full" to={"/learn/" + id}>
                                        Vào học ngay →
                                    </Link>
                                ) : pending ? (
                                    <>
                                        <p>Bạn đã tạo đơn #{pending.id}, đang chờ xác nhận thanh toán.</p>
                                        <Link className="button full" to={"/orders?order=" + pending.id}>
                                            Kiểm tra đơn / Tiếp tục thanh toán
                                        </Link>
                                    </>
                                ) : (
                                    <button className="full" disabled={busy || purchases.loading} onClick={buy}>
                                        {busy
                                            ? "Đang tạo đơn…"
                                            : Number(c.price) === 0
                                                ? "Đăng ký học miễn phí"
                                                : "Đăng ký khóa học"}
                                    </button>
                                )}
                                <ul className="checklist">
                                    <li>Nội dung chia theo chương</li>
                                    <li>Lưu tiến độ từng bài học</li>
                                    <li>Học trên trình duyệt</li>
                                </ul>
                                {!c.published && (
                                    <span className="badge">Khóa học đang ẩn</span>
                                )}
                            </aside>
                        </div>
                    </>
                )}
            </Status>
        </div>
    );
}

export function Learn() {
    const {courseId, lessonId} = useParams();
    const navigate = useNavigate();
    const [message, setMessage] = useState("");
    const [busy, setBusy] = useState(false);
    const course = useLoad(() => api.get("/api/courses/" + courseId), [courseId]);
    const all = course.data?.sections.flatMap((s) => s.lessons) || [];
    const resumeId = course.data?.resumeLessonId || localResumeLesson(courseId);
    const selectedId = lessonId || all.find(l => String(l.id) === String(resumeId))?.id || all[0]?.id;
    useEffect(() => {
        if (!lessonId && selectedId) navigate("/learn/" + courseId + "/" + selectedId, {replace: true});
    }, [courseId, lessonId, selectedId, navigate]);
    const lesson = useLoad(
        () =>
            selectedId
                ? api.get("/api/lessons/" + selectedId)
                : Promise.resolve({data: null}),
        [selectedId, courseId],
    );
    const current = all.find((l) => String(l.id) === String(selectedId));
    const index = all.indexOf(current);

    async function complete() {
        setBusy(true);
        setMessage("");
        try {
            await api.put("/api/me/progress/" + selectedId, {
                completed: !current.completed,
            });
            course.refresh();
        } catch (e) {
            setMessage(errorMessage(e));
        } finally {
            setBusy(false);
        }
    }

    return (
        <div className="section">
            <Link className="back" to={"/courses/" + courseId}>
                ← Chi tiết khóa học
            </Link>
            <Status request={course}>
                {course.data && (
                    <div className="learning-grid">
                        <div>
                            <div className="eyebrow">{course.data.title}</div>
                            <Status request={lesson}>
                                {lesson.data &&
                                    (String(lesson.data.courseId) === String(courseId) ? (
                                        <>
                                            <h1>{lesson.data.title}</h1>
                                            {lesson.data.muxStatus && <MuxLessonVideo key={lesson.data.id}
                                                                                      lessonId={lesson.data.id}
                                                                                      status={lesson.data.muxStatus}
                                                                                      lesson={lesson.data}
                                                                                      canSave={course.data.enrolled}
                                                                                      onCompleted={course.refresh}
                                                                                      title={lesson.data.title}/>}
                                            {!lesson.data.muxStatus && lesson.data.videoUrl && (
                                                <DirectLessonVideo key={lesson.data.id} lesson={lesson.data}
                                                                   canSave={course.data.enrolled}
                                                                   onCompleted={course.refresh}/>
                                            )}
                                            <article className="lesson-content">
                                                {lesson.data.content}
                                            </article>
                                            <Notice>{message}</Notice>
                                            <div className="toolbar">
                                                {course.data.enrolled ? (
                                                    <button disabled={busy} onClick={complete}>
                                                        {current?.completed
                                                            ? "✓ Đã hoàn thành · Đánh dấu chưa học"
                                                            : "Đánh dấu hoàn thành"}
                                                    </button>
                                                ) : (
                                                    <Link className="button" to={"/courses/" + courseId}>
                                                        Đăng ký để lưu tiến độ
                                                    </Link>
                                                )}
                                                {index >= 0 && index < all.length - 1 && (
                                                    <Link
                                                        className="button secondary"
                                                        to={"/learn/" + courseId + "/" + all[index + 1].id}
                                                    >
                                                        Bài tiếp theo →
                                                    </Link>
                                                )}
                                            </div>
                                        </>
                                    ) : (
                                        <Notice>Bài học không thuộc khóa học này.</Notice>
                                    ))}
                            </Status>
                            {!selectedId && (
                                <div className="empty">Khóa học chưa có bài học.</div>
                            )}
                            {lesson.error && (
                                <Link className="button" to={"/courses/" + courseId}>
                                    Xem thông tin đăng ký
                                </Link>
                            )}
                        </div>
                        <aside>
                            <h2>Lộ trình học</h2>
                            <Curriculum course={course.data} active={selectedId}/>
                        </aside>
                    </div>
                )}
            </Status>
        </div>
    );
}

export function Library() {
    const req = useLoad(() => api.get("/api/me/courses"));
    return (
        <section className="section">
            <div className="eyebrow">KHÔNG NGỪNG TIẾN BỘ</div>
            <h1>Khóa học của tôi</h1>
            <p className="lead">Tiếp tục hành trình từ nơi bạn đã dừng.</p>
            <Status request={req}>
                <div className="course-grid">
                    {req.data?.map((c) => (
                        <CourseCard key={c.id} course={c} owned/>
                    ))}
                </div>
                {req.data?.length === 0 && (
                    <div className="empty">
                        <h3>Bắt đầu một điều mới.</h3>
                        <p>Bạn chưa đăng ký khóa học nào.</p>
                        <Link className="button" to="/">
                            Khám phá khóa học
                        </Link>
                    </div>
                )}
            </Status>
        </section>
    );
}
