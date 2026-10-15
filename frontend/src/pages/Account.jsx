import { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import api, { errorMessage } from "../api";
import { Notice, Status } from "../ui";
import { useLoad, money, date } from "../helpers";
export function OrderTable({ orders, admin, checkout, busy }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Đơn hàng</th>
            {admin && <th>Học viên</th>}
            <th>Khóa học</th>
            <th>Thành tiền</th>
            <th>Trạng thái</th>
            <th>Ngày tạo</th>
            {!admin && <th>Thao tác</th>}
          </tr>
        </thead>
        <tbody>
          {orders.map((o) => (
            <tr key={o.id}>
              <td>#{o.id}</td>
              {admin && <td>{o.username}</td>}
              <td>
                {o.items.map((i) => (
                  <div key={i.id}>
                    <Link to={"/courses/" + i.courseId}>{i.courseTitle}</Link>
                  </div>
                ))}
              </td>
              <td>{money(o.total)}</td>
              <td>
                <span
                  className={"badge " + (o.status === "PAID" ? "paid" : "")}
                >
                  {o.status === "PAID" ? "Đã thanh toán" : "Chờ thanh toán"}
                </span>
              </td>
              <td>{date(o.createdAt)}</td>
              {!admin && (
                <td>
                  {o.status === "PENDING" ? (
                    <button
                      className="small"
                      disabled={busy === o.id}
                      onClick={() => checkout(o.id)}
                    >
                      {busy === o.id ? "Đang xử lý…" : "Thanh toán ↗"}
                    </button>
                  ) : (
                    <Link to={"/learn/" + o.items[0]?.courseId}>Vào học →</Link>
                  )}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      {orders.length === 0 && <div className="empty">Chưa có đơn hàng.</div>}
    </div>
  );
}
export function Orders() {
  const req = useLoad(() => api.get("/api/orders"));
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(null);
  useEffect(() => {
    const t = setInterval(req.refresh, 10000);
    return () => clearInterval(t);
  }, [req.refresh]);
  async function checkout(id) {
    setBusy(id);
    setError("");
    try {
      const { data } = await api.post("/api/orders/" + id + "/checkout");
      window.location.assign(data.checkoutUrl);
    } catch (e) {
      setError(errorMessage(e));
      setBusy(null);
    }
  }
  return (
    <section className="section">
      <div className="section-heading">
        <div>
          <div className="eyebrow">LỊCH SỬ MUA HÀNG</div>
          <h1>Đơn hàng của tôi</h1>
        </div>
        <button className="secondary" onClick={req.refresh}>
          Làm mới
        </button>
      </div>
      <p className="lead">
        Sau khi thanh toán được xác nhận, khóa học sẽ xuất hiện trong mục Học
        tập.
      </p>
      <Notice>{error}</Notice>
      <Status request={req}>
        {req.data && (
          <OrderTable orders={req.data} checkout={checkout} busy={busy} />
        )}
      </Status>
    </section>
  );
}
export function Profile() {
  const req = useLoad(() => api.get("/api/me"));
  return (
    <section className="section narrow">
      <h1>Hồ sơ cá nhân</h1>
      <Status request={req}>
        {req.data && (
          <div className="panel">
            <dl>
              {[
                ["Tên đăng nhập", req.data.username],
                ["Họ tên", req.data.fullName],
                ["Email", req.data.email],
                [
                  "Vai trò",
                  req.data.role === "ADMIN" ? "Quản trị viên" : "Học viên",
                ],
              ].map(([k, v]) => (
                <div className="profile-row" key={k}>
                  <dt>{k}</dt>
                  <dd>{v}</dd>
                </div>
              ))}
            </dl>
            <Link className="button" to="/my-courses">
              Khóa học của tôi →
            </Link>
          </div>
        )}
      </Status>
    </section>
  );
}
