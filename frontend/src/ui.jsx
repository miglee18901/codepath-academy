import { categoryColor, money } from "./helpers";
import { Link, Navigate, useLocation } from "react-router-dom";
import { session } from "./api";
export function Notice({ children, success }) {
  return children ? (
    <div
      role={success ? "status" : "alert"}
      className={"notice " + (success ? "success" : "")}
    >
      {children}
    </div>
  ) : null;
}
export function Status({ request, children }) {
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
export function Guard({ admin = false, children }) {
  const l = useLocation();
  if (!localStorage.getItem("token"))
    return (
      <Navigate to={"/login?next=" + encodeURIComponent(l.pathname)} replace />
    );
  if (admin && session()?.role !== "ROLE_ADMIN")
    return <div className="empty">Bạn không có quyền quản trị.</div>;
  return children;
}
export function CourseCard({ course: c, owned = false }) {
  const style = categoryColor(c);
  return (
    <article className="course-card">
      <Link
        to={owned ? "/learn/" + c.id : "/courses/" + c.id}
        className={"course-art " + style}
      >
        {c.thumbnailUrl ? (
          <img src={c.thumbnailUrl} alt="" loading="lazy" />
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
export function Curriculum({ course, active }) {
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
