import {categoryColor, money} from "./helpers";
import {Link, Navigate, useLocation} from "react-router-dom";
import {session} from "./api";

export function Notice({children, success}) {
    return children ? (
        <div
            role={success ? "status" : "alert"}
            className={"notice " + (success ? "success" : "")}
        >
            {children}
        </div>
    ) : null;
}

export function Status({request, children}) {
    if (request.loading && !request.data)
        return <div className="empty">Đang tải dữ liệu…</div>;
    if (request.error)
        return (
            <div className="empty">
                <Notice>{request.error}</Notice>
                <button onClick={request.refresh}>Thử lại</button>
            </div>
        );
    return children;
}

export function Guard({admin = false, children}) {
    const l = useLocation();
    if (!localStorage.getItem("token"))
        return (
            <Navigate to={"/login?next=" + encodeURIComponent(l.pathname)} replace/>
        );
    if (admin && session()?.role !== "ROLE_ADMIN")
        return <div className="empty">Bạn không có quyền quản trị.</div>;
    return children;
}

export function Pagination({data, onChange, loading = false}) {
    if (!data || data.totalElements === 0) return null;
    const {page, size, totalElements, totalPages} = data;
    const pages = totalPages <= 7 ? Array.from({length: totalPages}, (_, i) => i)
        : [...new Set([0, page - 1, page, page + 1, totalPages - 1])]
            .filter(i => i >= 0 && i < totalPages).sort((a, b) => a - b);
    return <div className="pagination-wrap">
        <p className="muted" role="status">
            Hiển thị {Math.min(page * size + 1, totalElements)}–{Math.min((page + 1) * size, totalElements)} / {totalElements} khóa học
        </p>
        {totalPages > 1 && <nav className="pagination" aria-label="Phân trang khóa học">
            <button className="secondary small" disabled={loading || page === 0}
                    onClick={() => onChange(page - 1)} aria-label="Trang trước">← Trước</button>
            {pages.map((index, i) => <span className="pagination-item" key={index}>
                {i > 0 && index > pages[i - 1] + 1 && <span aria-hidden="true">…</span>}
                <button className={(index === page ? "" : "secondary ") + "small"}
                        aria-label={"Trang " + (index + 1)} aria-current={index === page ? "page" : undefined}
                        disabled={loading} onClick={() => onChange(index)}>{index + 1}</button>
            </span>)}
            <button className="secondary small" disabled={loading || page >= totalPages - 1}
                    onClick={() => onChange(page + 1)} aria-label="Trang sau">Sau →</button>
        </nav>}
    </div>;
}

export function CourseCard({course: c, owned = false}) {
    const style = categoryColor(c);
    return (
        <article className="course-card">
            <Link
                to={owned ? "/learn/" + c.id : "/courses/" + c.id}
                className={"course-art " + style}
            >
                {c.thumbnailUrl ? (
                    <img src={c.thumbnailUrl} alt="" loading="lazy"/>
                ) : (
                    <>
                        <span className="art-label">{c.categoryName}</span>
                        <span className="art-code">
              {style === "java"
                  ? "Java"
                  : style === "react"
                      ? "React"
                      : "{ code }"}
            </span>
                        <span className="art-bottom">
              LEARN · BUILD · GROW <b>↗</b>
            </span>
                    </>
                )}
            </Link>
            <div className="card-body">
                <div className="eyebrow">
                    {c.level} <span>•</span> {c.lessonCount} bài học
                </div>
                <h3>
                    <Link to={"/courses/" + c.id}>{c.title}</Link>
                </h3>
                <p>{c.instructor}</p>
                {owned ? (
                    <>
                        <div className="progress">
                            <i
                                style={{
                                    width:
                                        (c.lessonCount
                                            ? (c.completedLessons / c.lessonCount) * 100
                                            : 0) + "%",
                                }}
                            />
                        </div>
                        <div className="card-bottom">
                            <small>
                                {c.completedLessons}/{c.lessonCount} bài hoàn thành
                            </small>
                            <Link to={"/learn/" + c.id}>Học tiếp →</Link>
                        </div>
                    </>
                ) : (
                    <div className="card-bottom">
                        <strong>{money(c.price)}</strong>
                        <Link to={"/courses/" + c.id}>Xem khóa học ↗</Link>
                    </div>
                )}
            </div>
        </article>
    );
}

export function Curriculum({course, active}) {
    return (
        <div className="curriculum">
            {course.sections?.length === 0 && (
                <p>Giảng viên đang cập nhật nội dung.</p>
            )}
            {course.sections?.map((s, i) => (
                <div className="chapter" key={s.id}>
                    <h3>
                        <span>{String(i + 1).padStart(2, "0")}</span>
                        {s.title}
                    </h3>
                    {s.lessons.map((l) => (
                        <Link
                            className={
                                "lesson-link " +
                                (String(active) === String(l.id) ? "selected" : "")
                            }
                            key={l.id}
                            to={"/learn/" + course.id + "/" + l.id}
                        >
              <span>
                {l.completed
                    ? "✓"
                    : l.preview ||
                    course.enrolled ||
                    session()?.role === "ROLE_ADMIN"
                        ? "▷"
                        : "🔒"}{" "}
                  {l.title}
              </span>
                            <small>
                                {l.preview && <b>Xem thử · </b>}
                                {l.durationMinutes} phút
                            </small>
                        </Link>
                    ))}
                </div>
            ))}
        </div>
    );
}
