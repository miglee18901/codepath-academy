import {useEffect} from "react";
import {
    BrowserRouter,
    Routes,
    Route,
    Link,
    NavLink,
    useNavigate,
    useLocation,
} from "react-router-dom";
import {clearSession, session} from "./api";
import {Guard} from "./ui";
import {Catalog, CourseDetail, Learn, Library} from "./pages/Courses";
import {Orders, Profile} from "./pages/Account";
import OAuthCallback from "./pages/OAuthCallback";
import Auth from "./pages/Auth";
import {Admin, CourseEditor} from "./pages/Admin";
import "./App.css";

function Layout() {
    const navigate = useNavigate();
    const location = useLocation();
    const user = session();
    useEffect(() => {
        window.scrollTo(0, 0);
    }, [location.pathname]);
    return (
        <>
            <header className="topbar">
                <Link className="brand" to="/">
                    <span className="brand-icon">&lt;/&gt;</span>CodePath
                    <span className="brand-dot">.</span>
                </Link>
                <nav aria-label="Điều hướng chính">
                    <NavLink to="/" end>
                        Khóa học
                    </NavLink>
                    {user && (
                        <>
                            <NavLink to="/my-courses">Học tập</NavLink>
                            <NavLink to="/orders">Đơn hàng</NavLink>
                        </>
                    )}
                    {user?.role === "ROLE_ADMIN" && (
                        <NavLink to="/admin">Quản trị</NavLink>
                    )}
                </nav>
                <div className="account">
                    {user ? (
                        <>
                            <Link to="/profile" className="avatar" title={user.username}>
                                {user.username.slice(0, 1).toUpperCase()}
                            </Link>
                            <button
                                className="ghost"
                                onClick={() => {
                                    clearSession();
                                    navigate("/");
                                }}
                            >
                                Đăng xuất
                            </button>
                        </>
                    ) : (
                        <>
                            <Link to="/login">Đăng nhập</Link>
                            <Link to="/register" className="button small">
                                Bắt đầu học ↗
                            </Link>
                        </>
                    )}
                </div>
            </header>
            <main>
                <Routes>
                    <Route path="/" element={<Catalog/>}/>
                    <Route path="/courses/:id" element={<CourseDetail/>}/>
                    <Route path="/learn/:courseId/:lessonId?" element={<Learn/>}/>
                    <Route path="/login" element={<Auth/>}/>
                    <Route path="/oauth/callback" element={<OAuthCallback/>}/>
                    <Route path="/register" element={<Auth register/>}/>
                    <Route
                        path="/my-courses"
                        element={
                            <Guard>
                                <Library/>
                            </Guard>
                        }
                    />
                    <Route
                        path="/orders"
                        element={
                            <Guard>
                                <Orders/>
                            </Guard>
                        }
                    />
                    <Route
                        path="/profile"
                        element={
                            <Guard>
                                <Profile/>
                            </Guard>
                        }
                    />
                    <Route
                        path="/admin"
                        element={
                            <Guard admin>
                                <Admin/>
                            </Guard>
                        }
                    />
                    <Route
                        path="/admin/courses/:id"
                        element={
                            <Guard admin>
                                <CourseEditor/>
                            </Guard>
                        }
                    />
                    <Route
                        path="*"
                        element={
                            <div className="empty">
                                <h1>Không tìm thấy trang</h1>
                                <Link to="/">Về danh sách khóa học</Link>
                            </div>
                        }
                    />
                </Routes>
            </main>
            <footer>
                <Link className="brand" to="/">
                    CodePath.
                </Link>
                <span>Học từ nền tảng. Xây điều bạn muốn.</span>
                <span>Java · React · Programming</span>
            </footer>
        </>
    );
}

export default function App() {
    return (
        <BrowserRouter>
            <Layout/>
        </BrowserRouter>
    );
}
