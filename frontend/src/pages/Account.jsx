import {useState, useEffect} from "react";
import {Link, useNavigate, useSearchParams} from "react-router-dom";
import api, {errorMessage} from "../api";
import {Notice, Status} from "../ui";
import {useLoad, money, date} from "../helpers";
import PasskeySettings from "../PasskeySettings";
import SocialAccountSettings from "../SocialAccountSettings";

export function OrderTable({orders, admin, checkout, reconcile, busy}) {
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
                  {o.status === "PAID" ? "Đã thanh toán" : o.status === "CANCELLED"
                      ? "Đã hủy" : o.status === "EXPIRED" ? "Đã hết hạn" : "Chờ thanh toán"}
                </span>
                        </td>
                        <td>{date(o.createdAt)}</td>
                        {!admin && (
                            <td>
                                {o.status === "PENDING" ? (
                                    <>
                                    <button
                                        className="small"
                                        disabled={busy === o.id}
                                        onClick={() => checkout(o.id)}
                                    >
                                        {busy === o.id ? "Đang xử lý…" : "Thanh toán ↗"}
                                    </button>
                                    {o.checkoutUrl && <button className="small secondary"
                                        disabled={busy === o.id} onClick={() => reconcile(o.id)}>
                                        Kiểm tra thanh toán
                                    </button>}
                                    </>
                                ) : o.status === "PAID" ? (
                                    <Link to={"/learn/" + o.items[0]?.courseId}>Vào học →</Link>
                                ) : <Link to={"/courses/" + o.items[0]?.courseId}>Đăng ký lại</Link>}
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
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const selectedId = params.get("order");
    const [error, setError] = useState("");
    const [verificationError, setVerificationError] = useState("");
    const [busy, setBusy] = useState(null);
    const req = useLoad(async () => {
        const response = await api.get("/api/orders");
        const selected = response.data.find(o => String(o.id) === selectedId);
        if (selected?.status === "PENDING" && selected.checkoutUrl) {
            try {
                const {data} = await api.post("/api/orders/" + selected.id + "/reconcile");
                response.data = response.data.map(o => o.id === data.id ? data : o);
                setVerificationError("");
            } catch (e) {
                setVerificationError(errorMessage(e));
            }
        }
        return response;
    }, [selectedId]);
    const selected = req.data?.find(o => String(o.id) === selectedId);
    useEffect(() => {
        const t = setInterval(req.refresh, 10000);
        return () => clearInterval(t);
    }, [req.refresh]);

    async function checkout(id) {
        setBusy(id);
        setError("");
        try {
            const {data} = await api.post("/api/orders/" + id + "/checkout");
            if (data.status === "PAID") {
                navigate("/learn/" + data.items[0].courseId);
            } else if (data.status !== "PENDING") {
                setError("Đơn hàng đã hủy hoặc hết hạn. Vui lòng đăng ký lại khóa học.");
                req.refresh();
            } else {
                window.location.assign(data.checkoutUrl);
            }
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setBusy(null);
        }
    }

    async function reconcile(id) {
        setBusy(id);
        setError("");
        try {
            await api.post("/api/orders/" + id + "/reconcile");
            setVerificationError("");
            req.refresh();
        } catch (e) {
            setError(errorMessage(e));
        } finally {
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
            <Notice>{verificationError}</Notice>
            {selected?.status === "PAID" && <div className="panel">
                <Notice success>Đã xác nhận thanh toán đơn #{selected.id}. Khóa học đã được thêm vào Học tập.</Notice>
                <Link className="button" to={"/learn/" + selected.items[0]?.courseId}>Vào học ngay →</Link>
                <Link className="button secondary" to="/my-courses">Học tập</Link>
            </div>}
            {selected?.status === "PENDING" && <p role="status">
                Đơn #{selected.id} đang chờ xác nhận. Hệ thống kiểm tra lại mỗi 10 giây;
                nếu bạn đã chuyển khoản, hãy chọn “Kiểm tra thanh toán”.
            </p>}
            <Status request={req}>
                {req.data && (
                    <OrderTable orders={req.data} checkout={checkout} reconcile={reconcile} busy={busy}/>
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
            {req.data && <>
                <SocialAccountSettings hasPassword={req.data.passwordLoginEnabled !== false} onPasswordSet={req.refresh}/>
                {req.data.passwordLoginEnabled !== false ? <PasskeySettings/> : <div className="panel">
                    <h2>Passkeys</h2><p>Thiết lập mật khẩu ở trên để thêm và quản lý Passkeys.</p>
                </div>}
            </>}
        </section>
    );
}
