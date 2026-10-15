import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import api, { errorMessage } from "../api";
import { Notice, Status, CourseCard, Curriculum } from "../ui";
import { useLoad, money, categoryColor } from "../helpers";
export function Catalog() {
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const categories = useLoad(() => api.get("/api/categories"));
  const req = useLoad(
    () =>
      api.get("/api/courses", {
        params: { q: search, categoryId: category || undefined },
      }),
    [search, category],
  );
  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <span className="pill">LỘ TRÌNH CHO NGƯỜI MUỐN BẮT ĐẦU</span>
          <h1>
            Biến ý tưởng thành
            <br />
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
            <i />
            <i />
            <i />
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
            <span className="terminal-dot" />
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
              setSearch(q);
            }}
          >
            <input
              aria-label="Tìm khóa học"
              placeholder="Tìm khóa học…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
            <button>Tìm kiếm</button>
          </form>
        </div>
        <div className="filters">
          <button
            className={!category ? "active" : ""}
            onClick={() => setCategory("")}
          >
            Tất cả khóa học
          </button>
          {categories.data?.map((c) => (
            <button
              key={c.id}
              className={category === String(c.id) ? "active" : ""}
              onClick={() => setCategory(String(c.id))}
            >
              {c.name}
            </button>
          ))}
        </div>
        <Notice>{categories.error}</Notice>
        <Status request={req}>
          <div className="course-grid">
            {req.data?.map((c) => (
              <CourseCard key={c.id} course={c} />
            ))}
          </div>
          {req.data?.length === 0 && (
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
  const { id } = useParams();
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const req = useLoad(() => api.get("/api/courses/" + id), [id]);
  const c = req.data;
  async function buy() {
    if (!localStorage.getItem("token")) {
      navigate("/login?next=" + encodeURIComponent("/courses/" + id));
      return;
    }
    setBusy(true);
    setError("");
    try {
      const { data } = await api.post("/api/orders", { courseId: Number(id) });
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
                <Curriculum course={c} />
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
                ) : (
                  <button className="full" disabled={busy} onClick={buy}>
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
  const { courseId, lessonId } = useParams();
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const course = useLoad(() => api.get("/api/courses/" + courseId), [courseId]);
  const all = course.data?.sections.flatMap((s) => s.lessons) || [];
  const selectedId = lessonId || all[0]?.id;
  const lesson = useLoad(
    () =>
      selectedId
        ? api.get("/api/lessons/" + selectedId)
        : Promise.resolve({ data: null }),
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
                      {lesson.data.videoUrl && (
                        <video
                          className="lesson-video"
                          src={lesson.data.videoUrl}
                          controls
                          controlsList="nodownload"
                          preload="metadata"
                        />
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
              <Curriculum course={course.data} active={selectedId} />
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
            <CourseCard key={c.id} course={c} owned />
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
