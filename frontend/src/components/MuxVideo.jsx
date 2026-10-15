import {lazy, Suspense, useCallback, useEffect, useRef, useState} from "react";
import {createUpload} from "@mux/upchunk";
import api, {errorMessage} from "../api";
import {Notice} from "../ui";
import {useVideoProgress} from "./useVideoProgress";
import ResumeVideoDialog from "./ResumeVideoDialog";

const MuxPlayer = lazy(() => import("@mux/mux-player-react"));

const labels = {
    none: "Chưa có video Mux",
    waiting: "Đang chờ tải video lên",
    asset_created: "Đang xử lý video",
    preparing: "Đang xử lý video",
    ready: "Video sẵn sàng",
    errored: "Xử lý video thất bại. Xóa video và thử tệp khác.",
    timed_out: "Phiên tải lên đã hết hạn. Xóa phiên này để tải lại.",
    cancelled: "Phiên tải lên đã bị hủy",
    deleted: "Video đã bị xóa trên Mux",
};

export function MuxUpload({lessonId}) {
    const [video, setVideo] = useState(null);
    const [error, setError] = useState("");
    const [progress, setProgress] = useState(0);
    const [busy, setBusy] = useState(false);
    const [uploading, setUploading] = useState(false);
    const uploadRef = useRef(null);
    const active = useRef(false);
    const path = "/api/admin/lessons/" + lessonId + "/mux";

    const refresh = useCallback(async () => {
        try {
            const {data} = await api.post(path + "/refresh");
            if (active.current) {
                setVideo(data);
                setError("");
            }
        } catch (e) {
            if (active.current) setError(errorMessage(e));
        }
    }, [path]);

    useEffect(() => {
        active.current = true;
        refresh();
        return () => {
            active.current = false;
            uploadRef.current?.abort();
        };
    }, [refresh]);

    useEffect(() => {
        if (uploading || !["waiting", "asset_created", "preparing"].includes(video?.status)) return;
        const timer = window.setInterval(refresh, 5000);
        return () => window.clearInterval(timer);
    }, [video?.status, uploading, refresh]);

    async function upload(file) {
        if (!file) return;
        if (file.size === 0 || file.size > 2 * 1024 ** 3) {
            setError("Chọn video có dung lượng từ 1 byte đến 2 GB.");
            return;
        }
        setError("");
        setProgress(0);
        setBusy(true);
        setUploading(true);
        try {
            const {data} = await api.post(path + "/upload");
            if (!active.current) return;
            setVideo({configured: true, status: "waiting"});
            const task = createUpload({endpoint: data.uploadUrl, file, chunkSize: 5120});
            uploadRef.current = task;
            task.on("progress", (event) => {
                if (active.current && uploadRef.current === task) setProgress(Math.round(event.detail));
            });
            task.on("success", () => {
                if (!active.current || uploadRef.current !== task) return;
                setUploading(false);
                setBusy(false);
                setProgress(100);
                refresh();
            });
            task.on("error", () => {
                if (!active.current || uploadRef.current !== task) return;
                setUploading(false);
                setBusy(false);
                setError("Tải video thất bại. Xóa phiên tải lên rồi thử lại; giữ trang mở trong khi tải.");
            });
        } catch (e) {
            if (active.current) {
                setError(errorMessage(e));
                setUploading(false);
                setBusy(false);
            }
        }
    }

    async function remove() {
        if (!window.confirm("Xóa video hoặc phiên tải lên này khỏi Mux? Video đã xử lý sẽ bị xóa vĩnh viễn.")) return;
        setBusy(true);
        setError("");
        try {
            uploadRef.current?.abort();
            uploadRef.current = null;
            const {data} = await api.delete(path);
            setVideo(data);
            setProgress(0);
            setUploading(false);
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setBusy(false);
        }
    }

    return (
        <div className="mux-upload panel">
            <h4>Video Mux</h4>
            <Notice>{error}</Notice>
            <p role="status">{video ? labels[video.status] || video.status : "Đang kiểm tra cấu hình…"}</p>
            {video && !video.configured && <p>Chưa kết nối dịch vụ video. Cấu hình Mux trên máy chủ để bật tải lên.</p>}
            {video?.configured && video.status === "none" && (
                <label>
                    Chọn video để tải lên (tối đa 2 GB)
                    <input type="file" accept="video/*,.mkv,.mov" disabled={busy}
                           onChange={(e) => upload(e.target.files?.[0])}/>
                </label>
            )}
            {uploading && <div aria-live="polite">
                <progress max="100" value={progress}/>
                {progress}% — Giữ trang mở đến khi tải xong.</div>}
            <div className="toolbar">
                <button type="button" className="secondary small" disabled={busy} onClick={refresh}>Cập nhật trạng
                    thái
                </button>
                {video?.status && video.status !== "none" && (
                    <button type="button" className="ghost danger" disabled={busy && !uploading} onClick={remove}>
                        {uploading ? "Hủy tải lên" : "Xóa video Mux"}
                    </button>
                )}
            </div>
        </div>
    );
}

export function MuxLessonVideo({lessonId, status, title, lesson, canSave, onCompleted}) {
    const progress = useVideoProgress({lesson, canSave, onCompleted});
    const [playback, setPlayback] = useState(null);
    const [error, setError] = useState("");
    const [version, setVersion] = useState(0);

    useEffect(() => {
        if (status !== "ready") return;
        let active = true;

        async function load() {
            try {
                const {data} = await api.get("/api/lessons/" + lessonId + "/playback");
                if (active) {
                    setPlayback(data);
                    setError("");
                }
            } catch (e) {
                if (active) {
                    setPlayback(null);
                    setError(errorMessage(e));
                }
            }
        }

        load();
        const timer = window.setInterval(load, 15 * 60 * 1000);
        return () => {
            active = false;
            window.clearInterval(timer);
        };
    }, [lessonId, status, version]);

    if (!status) return null;
    if (status !== "ready") return <p role="status">Video chưa sẵn sàng. Vui lòng quay lại sau.</p>;
    return (
        <div className="mux-lesson-video">
            <ResumeVideoDialog title={title} progress={progress}/>
            <Notice>{error}</Notice>
            <Notice>{progress.saveError}</Notice>
            {playback && <Suspense fallback={<p>Đang tải trình phát…</p>}><MuxPlayer playbackId={playback.playbackId}
                                                                                     ref={progress.playerRef}
                                                                                     {...progress.events}
                                                                                     tokens={playback.tokens}
                                                                                     streamType="on-demand"
                                                                                     preload="metadata"
                                                                                     metadata={{video_title: title}}
                                                                                     onError={() => setError("Không phát được video. Hãy tải lại quyền phát hoặc thử lại sau.")}/></Suspense>
            }
            {!playback && !error && <p role="status">Đang tải video…</p>}
            <button type="button" className="ghost small" onClick={() => setVersion((v) => v + 1)}>Tải lại quyền phát
                video
            </button>
        </div>
    );
}

export function DirectLessonVideo({lesson, canSave, onCompleted}) {
    const progress = useVideoProgress({lesson, canSave, onCompleted});
    return <>
        <ResumeVideoDialog title={lesson.title} progress={progress}/>
        <video ref={progress.playerRef} {...progress.events} className="lesson-video"
               src={lesson.videoUrl} controls controlsList="nodownload" preload="metadata"/>
        <Notice>{progress.saveError}</Notice>
    </>;
}
