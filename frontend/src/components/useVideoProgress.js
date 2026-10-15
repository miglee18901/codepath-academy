import {useEffect, useRef, useState} from "react";
import api, {apiBase, session} from "../api";

function prefix() {
    return "codepath:video:" + (session()?.username || "guest");
}

function read(key) {
    try {
        return JSON.parse(localStorage.getItem(key) || "null");
    } catch {
        return null;
    }
}

function write(key, value) {
    try {
        localStorage.setItem(key, JSON.stringify(value));
    } catch { /* Private browsing or storage quota. */
    }
}

export function localResumeLesson(courseId) {
    return read(prefix() + ":course:" + courseId)?.lessonId;
}

export function useVideoProgress({lesson, canSave, onCompleted}) {
    const playerRef = useRef(null);
    const callbacks = useRef({canSave, onCompleted});
    callbacks.current = {canSave, onCompleted};
    const storageKey = useRef(prefix() + ":lesson:" + lesson.id).current;
    const courseKey = useRef(prefix() + ":course:" + lesson.courseId).current;
    const [initial] = useState(() => {
        const local = read(storageKey);
        return local && local.updatedAt > Number(lesson.positionUpdatedAt || 0)
            ? Number(local.positionSeconds) || 0 : Number(lesson.positionSeconds) || 0;
    });
    const [saveError, setSaveError] = useState("");
    const [resumePrompt, setResumePrompt] = useState(null);
    const awaitingChoice = useRef(false);
    const ready = useRef(false);
    const mounted = useRef(true);
    const latest = useRef(null);
    const inFlight = useRef(false);
    const lastSent = useRef("");
    const lastAttempt = useRef(0);
    const completionNotified = useRef(false);
    const sampleRef = useRef(null);

    async function flush() {
        if (!callbacks.current.canSave || inFlight.current || !latest.current) return;
        const snapshot = latest.current;
        const signature = JSON.stringify(snapshot.payload);
        if (signature === lastSent.current) return;
        inFlight.current = true;
        lastAttempt.current = Date.now();
        try {
            const {data} = await api.put("/api/me/progress/" + lesson.id, snapshot.payload);
            lastSent.current = signature;
            if (latest.current === snapshot) {
                write(storageKey, {positionSeconds: data.positionSeconds, updatedAt: data.positionUpdatedAt});
            }
            if (mounted.current) {
                setSaveError("");
                if (data.completed && !completionNotified.current) {
                    completionNotified.current = true;
                    callbacks.current.onCompleted?.();
                }
            }
        } catch {
            if (mounted.current) setSaveError("Chưa đồng bộ được vị trí xem. Vị trí vẫn được giữ trên trình duyệt và sẽ được thử lưu lại.");
        } finally {
            inFlight.current = false;
            // Serialize saves so an older response cannot overwrite a later pause/seek.
            if (latest.current !== snapshot) void flush();
        }
    }

    function sample(force = false) {
        const player = playerRef.current;
        if (ready.current && player && Number.isFinite(player.duration) && player.duration > 0) {
            const durationSeconds = Math.max(1, Math.floor(player.duration));
            const positionSeconds = Math.max(0, Math.min(durationSeconds, Math.floor(player.currentTime || 0)));
            const payload = {positionSeconds, durationSeconds};
            if (JSON.stringify(latest.current?.payload) !== JSON.stringify(payload)) {
                latest.current = {payload};
                write(storageKey, {
                    positionSeconds: positionSeconds >= durationSeconds * 0.95 ? 0 : positionSeconds,
                    updatedAt: Date.now()
                });
                write(courseKey, {lessonId: lesson.id});
            }
        }
        if (force || Date.now() - lastAttempt.current >= 10000) void flush();
    }

    sampleRef.current = sample;

    useEffect(() => {
        mounted.current = true;
        const visibility = () => {
            if (document.visibilityState === "hidden") sampleRef.current(true);
        };
        const pagehide = () => {
            sampleRef.current(false);
            if (!callbacks.current.canSave || inFlight.current || !latest.current) return;
            const token = localStorage.getItem("token");
            if (token) void fetch(apiBase + "/api/me/progress/" + lesson.id, {
                method: "PUT", keepalive: true,
                headers: {"Content-Type": "application/json", Authorization: "Bearer " + token},
                body: JSON.stringify(latest.current.payload),
            }).catch(() => {
            });
        };
        const timer = window.setInterval(() => sampleRef.current(true), 10000);
        document.addEventListener("visibilitychange", visibility);
        window.addEventListener("pagehide", pagehide);
        return () => {
            mounted.current = false;
            sampleRef.current(true);
            window.clearInterval(timer);
            document.removeEventListener("visibilitychange", visibility);
            window.removeEventListener("pagehide", pagehide);
        };
    }, [lesson.id]);

    function loaded() {
        if (ready.current || awaitingChoice.current) return;
        const player = playerRef.current;
        if (!player || !Number.isFinite(player.duration) || player.duration <= 0) return;
        const target = initial >= Math.floor(player.duration) * 0.95 ? 0 : Math.min(initial, player.duration);
        if (target > 0) {
            awaitingChoice.current = true;
            player.pause();
            setResumePrompt({positionSeconds: target, durationSeconds: Math.floor(player.duration)});
            return;
        }
        player.currentTime = 0;
        ready.current = true;
    }

    function chooseResume(continueWatching) {
        const player = playerRef.current;
        if (!player || !resumePrompt) return;
        player.currentTime = continueWatching ? resumePrompt.positionSeconds : 0;
        awaitingChoice.current = false;
        ready.current = true;
        setResumePrompt(null);
        sample(true);
        // The choice is a user gesture; if playback is blocked, the normal play control remains available.
        player.play()?.catch(() => {
        });
    }

    return {
        playerRef, saveError, resumePrompt, chooseResume,
        events: {
            onLoadedMetadata: loaded,
            onCanPlay: loaded,
            onPlay: () => {
                if (awaitingChoice.current) playerRef.current?.pause();
            },
            onTimeUpdate: () => sample(false),
            onPause: () => sample(true),
            onSeeked: () => sample(true),
            onEnded: () => sample(true),
        },
    };
}
