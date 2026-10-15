import { useState, useEffect } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import ReCAPTCHA from "react-google-recaptcha";
import api, { errorMessage } from "../api";
import { Notice } from "../ui";
export default function Auth({ register = false }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({
    username: "",
    password: "",
    fullName: "",
    email: "",
  });
  const [captcha, setCaptcha] = useState(null);
  const [otp, setOtp] = useState("");
  const [step, setStep] = useState(1);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [wait, setWait] = useState(0);
  useEffect(() => {
    setStep(1);
    setError("");
    setMessage("");
  }, [register]);
  useEffect(() => {
    if (!wait) return;
    const id = setTimeout(() => setWait((w) => w - 1), 1000);
    return () => clearTimeout(id);
  }, [wait]);
  function field(name, label, type = "text") {
    return (
      <label>
        {label}
        <input
          name={name}
          type={type}
          required
          value={form[name]}
          minLength={
            name === "password"
              ? register
                ? 8
                : 3
              : name === "username"
                ? 3
                : undefined
          }
          maxLength={
            name === "password"
              ? 64
              : name === "username"
                ? 20
                : name === "fullName"
                  ? 50
                  : 100
          }
          autoComplete={
            name === "password"
              ? register
                ? "new-password"
                : "current-password"
              : name === "username"
                ? "username"
                : undefined
          }
          onChange={(e) => setForm({ ...form, [name]: e.target.value })}
        />
      </label>
    );
  }
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      if (!register) {
        const { data } = await api.post("/api/auth/login", {
          username: form.username,
          password: form.password,
        });
        localStorage.setItem("token", data.token);
        localStorage.setItem("refreshToken", data.refreshToken);
        localStorage.setItem("user", JSON.stringify(data));
        const next = new URLSearchParams(location.search).get("next");
        navigate(
          next?.startsWith("/") && !next.startsWith("//")
            ? next
            : "/my-courses",
        );
      } else if (step === 1) {
        await api.post("/api/auth/register/init", {
          ...form,
          recaptchaToken: captcha,
        });
        setStep(2);
        setWait(60);
        setMessage("Mã OTP đã được gửi. Vui lòng kiểm tra email.");
      } else {
        await api.post("/api/auth/register/verify", { email: form.email, otp });
        navigate("/login?registered=1");
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function resend() {
    setBusy(true);
    setError("");
    try {
      await api.post("/api/auth/register/resend", { email: form.email });
      setWait(60);
      setMessage("Đã gửi lại OTP.");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="auth-layout">
      <div className="auth-intro">
        <span className="eyebrow">CODE YOUR NEXT CHAPTER</span>
        <h1>
          Kiến thức mới.
          <br />
          <em>Cơ hội mới.</em>
        </h1>
        <p>Một bài học nhỏ hôm nay có thể mở ra một hành trình lớn ngày mai.</p>
        <div className="auth-symbol">{"{ }"}</div>
      </div>
      <div className="panel auth-panel">
        <h2>
          {register
            ? step === 2
              ? "Xác thực email"
              : "Tạo tài khoản học viên"
            : "Chào mừng trở lại"}
        </h2>
        <p className="muted">
          {register
            ? "Bắt đầu hành trình học lập trình của bạn."
            : "Đăng nhập để tiếp tục học tập."}
        </p>
        <Notice>{error}</Notice>
        <Notice success>
          {message ||
            (!register && location.search.includes("registered=1")
              ? "Đăng ký thành công. Bạn có thể đăng nhập."
              : "")}
        </Notice>
        <form onSubmit={submit}>
          {register && step === 2 ? (
            <>
              <p>
                Nhập mã gửi đến <strong>{form.email}</strong>
              </p>
              <label>
                Mã OTP
                <input
                  required
                  inputMode="numeric"
                  pattern="[0-9]{6}"
                  maxLength={6}
                  value={otp}
                  onChange={(e) => setOtp(e.target.value)}
                />
              </label>
            </>
          ) : (
            <>
              {field("username", "Tên đăng nhập")}
              {register && (
                <>
                  {field("fullName", "Họ và tên")}
                  {field("email", "Email", "email")}
                </>
              )}
              {field(
                "password",
                register ? "Mật khẩu (8–64 ký tự)" : "Mật khẩu",
                "password",
              )}
              {register && (
                <ReCAPTCHA
                  sitekey={
                    import.meta.env.VITE_RECAPTCHA_SITE_KEY ||
                    "6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI"
                  }
                  onChange={setCaptcha}
                  onExpired={() => setCaptcha(null)}
                />
              )}
            </>
          )}
          <button
            className="full"
            disabled={busy || (register && step === 1 && !captcha)}
          >
            {busy
              ? "Đang xử lý…"
              : register
                ? step === 2
                  ? "Xác thực tài khoản"
                  : "Tạo tài khoản"
                : "Đăng nhập →"}
          </button>
        </form>
        {register && step === 2 && (
          <button
            className="ghost"
            disabled={wait > 0 || busy}
            onClick={resend}
          >
            Gửi lại OTP {wait > 0 ? "(" + wait + "s)" : ""}
          </button>
        )}
        <p className="auth-switch">
          {register ? "Đã có tài khoản? " : "Chưa có tài khoản? "}
          <Link to={register ? "/login" : "/register"}>
            {register ? "Đăng nhập" : "Đăng ký học viên"}
          </Link>
        </p>
      </div>
    </section>
  );
}
