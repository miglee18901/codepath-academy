import axios from "axios";

export const apiBase = import.meta.env.VITE_API_URL || "http://localhost:8080";
const api = axios.create({baseURL: apiBase});
let refreshPromise;

export function clearSession() {
    ["token", "refreshToken", "user"].forEach((k) => localStorage.removeItem(k));
}

export function session() {
    try {
        return JSON.parse(localStorage.getItem("user") || "null");
    } catch {
        return null;
    }
}

api.interceptors.request.use((c) => {
    const t = localStorage.getItem("token");
    if (t) c.headers.Authorization = "Bearer " + t;
    c.headers["Accept-Language"] = "vi";
    return c;
});
api.interceptors.response.use(
    (r) => r,
    async (error) => {
        const c = error.config;
        if (
            error.response?.status !== 401 ||
            c?._retry ||
            c?.url?.startsWith("/api/auth/")
        )
            return Promise.reject(error);
        c._retry = true;
        const refreshToken = localStorage.getItem("refreshToken");
        if (!refreshToken) return Promise.reject(error);
        try {
            if (!refreshPromise)
                refreshPromise = axios
                    .post(apiBase + "/api/auth/refresh", {refreshToken})
                    .then(({data}) => {
                        localStorage.setItem("token", data.token);
                        localStorage.setItem("refreshToken", data.refreshToken);
                        localStorage.setItem("user", JSON.stringify(data));
                        return data.token;
                    })
                    .finally(() => {
                        refreshPromise = null;
                    });
            c.headers.Authorization = "Bearer " + (await refreshPromise);
            return api(c);
        } catch (e) {
            clearSession();
            window.location.assign("/login");
            return Promise.reject(e);
        }
    },
);

export function errorMessage(e) {
    const d = e.response?.data;
    return d?.fields
        ? Object.values(d.fields).join(". ")
        : d?.message || "Không kết nối được máy chủ. Vui lòng thử lại.";
}

export default api;
