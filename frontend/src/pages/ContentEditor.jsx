import {useState} from "react";
import api, {errorMessage} from "../api";
import {Notice} from "../ui";
import {MuxUpload} from "../components/MuxVideo";

export function SectionEditor({section, courseId, refresh}) {
    const [title, setTitle] = useState(section.title);
    const [position, setPosition] = useState(section.position);
    const [editing, setEditing] = useState(null);
    const [error, setError] = useState("");

    async function save(e) {
        e.preventDefault();
        try {
            await api.put(
                "/api/admin/courses/" + courseId + "/sections/" + section.id,
                {title, position: Number(position)},
            );
            refresh();
        } catch (e) {
            setError(errorMessage(e));
        }
    }

    async function remove(type, id) {
        if (
            !window.confirm(
                "Xóa " +
                (type === "sections"
                    ? "chương và toàn bộ bài học bên trong"
                    : "bài học") +
                "? Tiến độ của nội dung bị xóa cũng sẽ mất.",
            )
        )
            return;
        try {
            await api.delete("/api/admin/" + type + "/" + id);
            refresh();
        } catch (e) {
            setError(errorMessage(e));
        }
    }

    async function edit(id) {
        try {
            const {data} = await api.get("/api/lessons/" + id);
            setEditing(data);
        } catch (e) {
            setError(errorMessage(e));
        }
    }

    return (
        <div className="panel section-editor">
            <Notice>{error}</Notice>
            <form className="inline-form" onSubmit={save}>
                <label>
                    Tên chương
                    <input
                        required
                        maxLength={160}
                        value={title}
                        onChange={(e) => setTitle(e.target.value)}
                    />
                </label>
                <label className="position">
                    Thứ tự
                    <input
                        type="number"
                        required
                        min={0}
                        value={position}
                        onChange={(e) => setPosition(e.target.value)}
                    />
                </label>
                <button className="secondary">Lưu chương</button>
                <button
                    className="ghost danger"
                    type="button"
                    onClick={() => remove("sections", section.id)}
                >
                    Xóa
                </button>
            </form>
            {section.lessons.map((l) => (
                <div className="list-row" key={l.id}>
          <span>
            {l.position + 1}. {l.title}{" "}
              {l.preview && <span className="badge">Xem thử</span>}
          </span>
                    <div>
                        <button className="ghost" onClick={() => edit(l.id)}>
                            Sửa
                        </button>
                        <button
                            className="ghost danger"
                            onClick={() => remove("lessons", l.id)}
                        >
                            Xóa
                        </button>
                    </div>
                </div>
            ))}
            <button
                className="secondary small"
                onClick={() =>
                    setEditing({
                        title: "",
                        content: "",
                        videoUrl: "",
                        durationMinutes: 0,
                        position: section.lessons.length,
                        preview: false,
                    })
                }
            >
                + Thêm bài học
            </button>
            {editing && (
                <LessonEditor
                    key={editing.id || "new"}
                    lesson={editing}
                    sectionId={section.id}
                    onClose={() => setEditing(null)}
                    onSaved={() => {
                        setEditing(null);
                        refresh();
                    }}
                />
            )}
        </div>
    );
}

function LessonEditor({lesson, sectionId, onClose, onSaved}) {
    const [form, setForm] = useState(lesson);
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const change = (e) =>
        setForm({
            ...form,
            [e.target.name]:
                e.target.type === "checkbox" ? e.target.checked : e.target.value,
        });

    async function save(e) {
        e.preventDefault();
        setBusy(true);
        try {
            const payload = {
                ...form,
                durationMinutes: Number(form.durationMinutes),
                position: Number(form.position),
            };
            const path = "/api/admin/sections/" + sectionId + "/lessons";
            await (form.id
                ? api.put(path + "/" + form.id, payload)
                : api.post(path, payload));
            onSaved();
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setBusy(false);
        }
    }

    return (
        <form className="lesson-editor editor-form" onSubmit={save}>
            <h3>{form.id ? "Chỉnh sửa bài học" : "Bài học mới"}</h3>
            <Notice>{error}</Notice>
            <label>
                Tên bài học
                <input
                    required
                    name="title"
                    maxLength={160}
                    value={form.title}
                    onChange={change}
                />
            </label>
            <label>
                Nội dung (văn bản thuần)
                <textarea
                    required
                    rows={12}
                    name="content"
                    maxLength={1000000}
                    value={form.content}
                    onChange={change}
                />
            </label>
            <label>
                URL video (file phát trực tiếp như MP4)
                <input
                    name="videoUrl"
                    type="url"
                    maxLength={1000}
                    value={form.videoUrl || ""}
                    onChange={change}
                />
            </label>
            <div className="form-grid">
                <label>
                    Thời lượng (phút)
                    <input
                        required
                        type="number"
                        name="durationMinutes"
                        min={0}
                        max={10000}
                        value={form.durationMinutes}
                        onChange={change}
                    />
                </label>
                <label>
                    Thứ tự
                    <input
                        required
                        type="number"
                        name="position"
                        min={0}
                        value={form.position}
                        onChange={change}
                    />
                </label>
            </div>
            <label className="checkbox">
                <input
                    type="checkbox"
                    name="preview"
                    checked={form.preview}
                    onChange={change}
                />
                Cho phép xem thử miễn phí
            </label>
            <div className="toolbar">
                <button disabled={busy}>{busy ? "Đang lưu…" : "Lưu bài học"}</button>
                <button className="ghost" type="button" onClick={onClose}>
                    Đóng
                </button>
            </div>
            {form.id ? <MuxUpload lessonId={form.id}/> : <p>Lưu bài học trước để tải video Mux lên.</p>}
        </form>
    );
}
