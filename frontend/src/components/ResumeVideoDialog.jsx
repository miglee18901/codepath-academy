import {useEffect, useId, useRef} from "react";

function timestamp(seconds) {
    const value = Math.max(0, Math.floor(seconds));
    const hours = Math.floor(value / 3600);
    const minutes = Math.floor((value % 3600) / 60);
    const remainder = String(value % 60).padStart(2, "0");
    return hours ? `${hours}:${String(minutes).padStart(2, "0")}:${remainder}` : `${String(minutes).padStart(2, "0")}:${remainder}`;
}

export default function ResumeVideoDialog({title, progress}) {
    const dialogRef = useRef(null);
    const titleId = useId();
    const descriptionId = useId();
    const resume = progress.resumePrompt;

    useEffect(() => {
        const dialog = dialogRef.current;
        if (!resume || !dialog) return;
        const overflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";
        dialog.showModal();
        dialog.querySelector("button")?.focus();
        return () => {
            dialog.close();
            document.body.style.overflow = overflow;
        };
    }, [resume]);

    if (!resume) return null;
    return (
        <dialog ref={dialogRef} className="resume-video-dialog" aria-labelledby={titleId}
                aria-describedby={descriptionId} onCancel={(event) => event.preventDefault()}>
            <div className="eyebrow">TIẾP TỤC HÀNH TRÌNH HỌC</div>
            <h2 id={titleId}>Bạn muốn xem tiếp?</h2>
            <p className="resume-video-title">{title}</p>
            <p id={descriptionId}>
                Lần trước bạn đã xem đến <strong>{timestamp(resume.positionSeconds)}</strong>
                {" / "}{timestamp(resume.durationSeconds)}. Bạn muốn tiếp tục từ vị trí này hay xem lại từ đầu?
            </p>
            <div className="resume-video-actions">
                <button type="button" onClick={() => progress.chooseResume(true)}>
                    Tiếp tục xem từ {timestamp(resume.positionSeconds)}
                </button>
                <button type="button" className="secondary" onClick={() => progress.chooseResume(false)}>
                    Xem lại từ đầu
                </button>
            </div>
        </dialog>
    );
}
