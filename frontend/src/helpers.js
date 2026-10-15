import { useState, useEffect, useRef, useCallback } from "react";
export const money = (n) =>
  Number(n) === 0
    ? "Miễn phí"
    : new Intl.NumberFormat("vi-VN", {
        style: "currency",
        currency: "VND",
      }).format(n);
export const date = (s) => (s ? new Date(s).toLocaleString("vi-VN") : "—");
export const categoryColor = (c) =>
  c.title.toLowerCase().includes("java")
    ? "java"
    : c.title.toLowerCase().includes("react")
      ? "react"
      : "code";
export function useLoad(loader, deps = []) {
  const [state, set] = useState({ data: null, loading: true, error: "" });
  const [version, setVersion] = useState(0);
  const loaderRef = useRef(loader);
  useEffect(() => {
    loaderRef.current = loader;
  }, [loader]);
  const inputs = JSON.stringify(deps);
  useEffect(() => {
    let active = true;
    set((s) => ({ ...s, loading: true, error: "" }));
    loaderRef
      .current()
      .then((r) => {
        if (active) set({ data: r.data, loading: false, error: "" });
      })
      .catch((e) => {
        if (active)
          set({
            data: null,
            loading: false,
            error:
              e.response?.data?.message ||
              "Không tải được dữ liệu. Hãy thử lại.",
          });
      });
    return () => {
      active = false;
    };
  }, [inputs, version]);
  const refresh = useCallback(() => setVersion((v) => v + 1), []);
  return { ...state, refresh };
}
