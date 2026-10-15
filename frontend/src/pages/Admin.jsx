import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import api, { errorMessage } from "../api";
import { Notice, Status } from "../ui";
import { useLoad, money } from "../helpers";
import { OrderTable } from "./Account";
import { SectionEditor } from "./ContentEditor";
export function Admin() {
  const [tab, setTab] = useState("overview");
  const [error, setError] = useState("");
  const [name, setName] = useState("");
  const stats = useLoad(() => api.get("/api/admin/stats"));
  const courses = useLoad(() => api.get("/api/admin/courses"));
  const orders = useLoad(() => api.get("/api/admin/orders"));
  const students = useLoad(() => api.get("/api/admin/students"));
  const categories = useLoad(() => api.get("/api/categories"));
  async function add(e) {
    e.preventDefault();
    setError("");
    try {
      await api.post("/api/admin/categories", { name });
      setName("");
      categories.refresh();
    } catch (e) {
      setError(errorMessage(e));
    }
  }
  async function remove(id) {
    if (!window.confirm("Xóa danh mục trống này?")) return;
    try {
      await api.delete("/api/admin/categories/" + id);
      categories.refresh();
    } catch (e) {
      setError(errorMessage(e));
    }
  }
  async function rename(c) {
    const name = window.prompt("Tên danh mục", c.name);
    if (!name) return;
    try {
      await api.put("/api/admin/categories/" + c.id, { name });
      categories.refresh();
    } catch (e) {
      setError(errorMessage(e));
    }
  }
  async function exportOrders() {
    try {
      const r = await api.get("/api/admin/orders/export", {
        responseType: "blob",
      });
      const url = URL.createObjectURL(r.data);
      const a = document.createElement("a");
      a.href = url;
      a.download = "orders.xlsx";
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setError("Không xuất được danh sách đơn hàng.");
    }
  }
  return (
    <section className="section">
      <div className="eyebrow">KHÔNG GIAN QUẢN TRỊ</div>
      <div className="section-heading">
        <h1>Quản lý nền tảng</h1>
        <Link className="button" to="/admin/courses/new">
          + Tạo khóa học
        </Link>
      </div>
      <div className="filters admin-tabs">
        {[
          ["overview", "Tổng quan"],
          ["courses", "Khóa học"],
          ["categories", "Danh mục"],
          ["orders", "Đơn hàng"],
          ["students", "Tài khoản"],
        ].map(([id, label]) => (
          <button
            className={tab === id ? "active" : ""}
            key={id}
            onClick={() => {
              setTab(id);
              setError("");
            }}
          >
            {label}
          </button>
        ))}
      </div>
      <Notice>{error}</Notice>
      {tab === "overview" && (
        <Status request={stats}>
          {stats.data && (
            <>
              <div className="stats-grid">
                {[
                  ["Doanh thu đã thu", money(stats.data.revenue)],
                  ["Đơn đã thanh toán", stats.data.paidOrders],
                  ["Học viên", stats.data.students],
                  ["Lượt đăng ký học", stats.data.enrollments],
                ].map(([label, value]) => (
                  <div className="panel stat" key={label}>
                    <span>{label}</span>
                    <strong>{value}</strong>
                  </div>
                ))}
              </div>
              <div className="panel">
                <h2>Quy trình xuất bản</h2>
                <p>
                  Tạo danh mục → tạo khóa học → thêm chương và bài học → bật
                  xuất bản. Khóa học ẩn vẫn truy cập được với học viên đã mua.
                </p>
                <p className="muted">
                  Doanh thu chỉ tính các đơn đã được backend xác nhận thanh
                  toán.
                </p>
              </div>
            </>
          )}
        </Status>
      )}
      {tab === "courses" && (
        <Status request={courses}>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Khóa học</th>
                  <th>Giá</th>
                  <th>Nội dung</th>
                  <th>Trạng thái</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {courses.data?.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <strong>{c.title}</strong>
                      <small className="block">{c.categoryName}</small>
                    </td>
                    <td>{money(c.price)}</td>
                    <td>{c.lessonCount} bài</td>
                    <td>
                      <span className={"badge " + (c.published ? "paid" : "")}>
                        {c.published ? "Đã xuất bản" : "Bản nháp"}
                      </span>
                    </td>
                    <td>
                      <Link to={"/admin/courses/" + c.id}>Chỉnh sửa →</Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Status>
      )}
      {tab === "orders" && (
        <Status request={orders}>
          <div className="toolbar">
            <button onClick={exportOrders}>Xuất Excel</button>
            <button className="secondary" onClick={orders.refresh}>
              Làm mới
            </button>
          </div>
          {orders.data && <OrderTable orders={orders.data} admin />}
        </Status>
      )}
      {tab === "students" && (
        <Status request={students}>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Tài khoản</th>
                  <th>Họ tên</th>
                  <th>Email</th>
                  <th>Vai trò</th>
                  <th>Khóa học</th>
                </tr>
              </thead>
              <tbody>
                {students.data?.map((s) => (
                  <tr key={s.id}>
                    <td>{s.username}</td>
                    <td>{s.fullName}</td>
                    <td>{s.email}</td>
                    <td>{s.role}</td>
                    <td>{s.courses}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Status>
      )}
      {tab === "categories" && (
        <Status request={categories}>
          <div className="panel narrow">
            <form className="inline-form" onSubmit={add}>
              <label>
                Tên danh mục
                <input
                  value={name}
                  maxLength={100}
                  required
                  onChange={(e) => setName(e.target.value)}
                />
              </label>
              <button>Thêm danh mục</button>
            </form>
            {categories.data?.map((c) => (
              <div className="list-row" key={c.id}>
                <span>{c.name}</span>
                <div>
                  <button className="ghost" onClick={() => rename(c)}>
                    Sửa
                  </button>
                  <button className="ghost danger" onClick={() => remove(c.id)}>
                    Xóa
                  </button>
                </div>
              </div>
            ))}
          </div>
        </Status>
      )}
    </section>
  );
}
const defaults = {
  categoryId: "",
  title: "",
  description: "",
  price: 0,
  thumbnailUrl: "",
  instructor: "",
  level: "Cơ bản",
  published: false,
};
function CourseForm({ initial, categories, onSaved }) {
  const [form, setForm] = useState({ ...defaults, ...initial });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const change = (e) =>
    setForm({
      ...form,
      [e.target.name]:
        e.target.type === "checkbox" ? e.target.checked : e.target.value,
    });
  async function save(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const input = {
        ...form,
        categoryId: Number(form.categoryId),
        price: Number(form.price),
      };
      const { data } = await (initial?.id
        ? api.put("/api/admin/courses/" + initial.id, input)
        : api.post("/api/admin/courses", input));
      onSaved(data);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <form className="panel editor-form" onSubmit={save}>
      <h2>Thông tin khóa học</h2>
      <Notice>{error}</Notice>
      <label>
        Tên khóa học
        <input
          name="title"
          required
          maxLength={160}
          value={form.title}
          onChange={change}
        />
      </label>
      <div className="form-grid">
        <label>
          Danh mục
          <select
            name="categoryId"
            required
            value={form.categoryId}
            onChange={change}
          >
            <option value="">Chọn danh mục</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Giá bán (VND, 0 = miễn phí)
          <input
            name="price"
            type="number"
            min="0"
            max="999999999"
            step="1"
            required
            value={form.price}
            onChange={change}
          />
        </label>
        <label>
          Giảng viên
          <input
            name="instructor"
            required
            maxLength={100}
            value={form.instructor}
            onChange={change}
          />
        </label>
        <label>
          Trình độ
          <select name="level" value={form.level} onChange={change}>
            {["Nhập môn", "Cơ bản", "Trung cấp", "Nâng cao"].map((x) => (
              <option key={x}>{x}</option>
            ))}
          </select>
        </label>
      </div>
      <label>
        Mô tả
        <textarea
          name="description"
          rows={5}
          maxLength={100000}
          required
          value={form.description}
          onChange={change}
        />
      </label>
      <label>
        URL ảnh bìa
        <input
          type="url"
          name="thumbnailUrl"
          maxLength={1000}
          value={form.thumbnailUrl || ""}
          onChange={change}
        />
      </label>
      <label className="checkbox">
        <input
          type="checkbox"
          name="published"
          checked={form.published}
          onChange={change}
        />
        Xuất bản trên danh sách khóa học
      </label>
      <button disabled={busy}>{busy ? "Đang lưu…" : "Lưu khóa học"}</button>
    </form>
  );
}
export function CourseEditor() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isNew = id === "new";
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [title, setTitle] = useState("");
  const req = useLoad(
    () =>
      isNew ? Promise.resolve({ data: null }) : api.get("/api/courses/" + id),
    [id],
  );
  const cats = useLoad(() => api.get("/api/categories"));
  async function addSection(e) {
    e.preventDefault();
    setError("");
    try {
      await api.post("/api/admin/courses/" + id + "/sections", {
        title,
        position: req.data.sections.length,
      });
      setTitle("");
      req.refresh();
    } catch (e) {
      setError(errorMessage(e));
    }
  }
  return (
    <section className="section">
      <Link className="back" to="/admin">
        ← Quản trị
      </Link>
      <h1>{isNew ? "Tạo khóa học mới" : "Biên tập khóa học"}</h1>
      <Notice success>{message}</Notice>
      <Notice>{error}</Notice>
      <Status request={cats}>
        <Status request={req}>
          {!req.loading && (
            <CourseForm
              key={id}
              initial={req.data}
              categories={cats.data || []}
              onSaved={(data) => {
                setMessage("Đã lưu khóa học.");
                if (isNew) navigate("/admin/courses/" + data.id);
                else req.refresh();
              }}
            />
          )}
        </Status>
      </Status>
      {!isNew && req.data && (
        <div className="spaced">
          <div className="section-heading">
            <h2>Chương và bài học</h2>
            <Link to={"/courses/" + id}>Xem khóa học ↗</Link>
          </div>
          {req.data.sections.map((s) => (
            <SectionEditor
              key={s.id}
              section={s}
              courseId={id}
              refresh={req.refresh}
            />
          ))}
          <form className="panel inline-form" onSubmit={addSection}>
            <label>
              Thêm chương mới
              <input
                required
                maxLength={160}
                value={title}
                onChange={(e) => setTitle(e.target.value)}
              />
            </label>
            <button>+ Thêm chương</button>
          </form>
        </div>
      )}
    </section>
  );
}
