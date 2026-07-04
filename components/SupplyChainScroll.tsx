"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { transform, useMotionValueEvent, useScroll } from "motion/react";
import styles from "./SupplyChainScroll.module.css";

const FRAME_COUNT = 320;
const FRAME_WIDTH = 960;
const FRAME_HEIGHT = 540;
// Frames 1-80 are enough to start scrolling (beat 1 -> 2); the rest stream in behind.
const PRIORITY_COUNT = 80;

const JOURNEY_STEPS = [
  "Manufacturer",
  "In transit",
  "Checkpoint",
  "Resolution",
  "Delivered",
];
// Aligned with the frame-range beats: 1-80 leaves the manufacturer, 81-160
// checkpoint alert, 161-240 resolution, 241-320 delivery.
const STEP_THRESHOLDS = [0, 0.08, 0.25, 0.5, 0.75];

// Overlay opacity curves, one per story beat. These run imperatively in the
// scroll callback (not through motion style bindings): Chromium promotes
// scroll-linked style animations to WAAPI, which can override inline styles
// and freeze an overlay mid-fade.
const introOpacityAt = transform([0, 0.15, 0.2], [1, 1, 0]);
const fraudOpacityAt = transform([0.2, 0.24, 0.41, 0.45], [0, 1, 1, 0]);
const resolveOpacityAt = transform([0.45, 0.49, 0.66, 0.7], [0, 1, 1, 0]);
const deliveredOpacityAt = transform([0.7, 0.75, 1], [0, 1, 1]);
const riskScoreAt = transform([0.22, 0.4], [0, 87]);

function frameSrc(index: number): string {
  return `/frames/frame_${String(index).padStart(4, "0")}.jpg`;
}

export default function SupplyChainScroll() {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const introRef = useRef<HTMLDivElement>(null);
  const fraudRef = useRef<HTMLDivElement>(null);
  const resolveRef = useRef<HTMLDivElement>(null);
  const deliveredRef = useRef<HTMLDivElement>(null);
  const hintRef = useRef<HTMLDivElement>(null);
  const scoreRef = useRef<HTMLSpanElement>(null);
  const trackerRef = useRef<HTMLDivElement>(null);

  const imagesRef = useRef<(HTMLImageElement | null)[]>(
    new Array(FRAME_COUNT).fill(null)
  );
  const currentFrameRef = useRef(1);

  const [loadedCount, setLoadedCount] = useState(0);
  const [ready, setReady] = useState(false);

  const { scrollYProgress } = useScroll({
    target: wrapperRef,
    offset: ["start start", "end end"],
  });

  const drawFrame = useCallback((index: number) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = "high";

    const clamped = Math.min(FRAME_COUNT, Math.max(1, Math.round(index)));
    // If the exact frame hasn't loaded yet, fall back to the nearest earlier
    // loaded one so fast scrolling never leaves a blank canvas.
    let img = imagesRef.current[clamped - 1];
    if (!img) {
      for (let i = clamped - 1; i >= 1; i--) {
        if (imagesRef.current[i - 1]) {
          img = imagesRef.current[i - 1];
          break;
        }
      }
    }
    if (!img) return;

    currentFrameRef.current = clamped;
    // Cover-fit: fill the whole viewport and center-crop the overflow, so the
    // frame's own background never has to line up with the page background.
    const cw = canvas.width;
    const ch = canvas.height;
    const scale = Math.max(cw / FRAME_WIDTH, ch / FRAME_HEIGHT);
    const dw = FRAME_WIDTH * scale;
    const dh = FRAME_HEIGHT * scale;
    ctx.clearRect(0, 0, cw, ch);
    ctx.drawImage(img, (cw - dw) / 2, (ch - dh) / 2, dw, dh);
  }, []);

  const applyProgress = useCallback(
    (p: number) => {
      drawFrame(1 + p * (FRAME_COUNT - 1));

      const fade = (el: HTMLElement | null, opacity: number) => {
        if (!el) return;
        el.style.opacity = String(opacity);
        // A faded-out overlay must also be hidden so it can never ghost on
        // top of the overlay that is currently active.
        el.style.visibility = opacity > 0.02 ? "visible" : "hidden";
      };

      const intro = introOpacityAt(p);
      fade(introRef.current, intro);
      fade(hintRef.current, intro);
      fade(fraudRef.current, fraudOpacityAt(p));
      fade(resolveRef.current, resolveOpacityAt(p));
      fade(deliveredRef.current, deliveredOpacityAt(p));

      if (scoreRef.current) {
        scoreRef.current.textContent = String(Math.round(riskScoreAt(p)));
      }

      const tracker = trackerRef.current;
      if (tracker) {
        let active = 0;
        for (let i = 0; i < STEP_THRESHOLDS.length; i++) {
          if (p >= STEP_THRESHOLDS[i]) active = i;
        }
        Array.from(tracker.children).forEach((child, i) => {
          child.classList.toggle(styles.stepActive, i === active);
          child.classList.toggle(styles.stepDone, i < active);
        });
      }
    },
    [drawFrame]
  );

  useMotionValueEvent(scrollYProgress, "change", applyProgress);

  // Preload queue: priority batch first, remainder in the background.
  useEffect(() => {
    let cancelled = false;
    let loaded = 0;

    const load = (index: number) =>
      new Promise<void>((resolve) => {
        const img = new Image();
        img.onload = () => {
          if (cancelled) return resolve();
          imagesRef.current[index - 1] = img;
          loaded++;
          setLoadedCount(loaded);
          // Repaint if a late arrival is the frame we are currently sitting on.
          if (index === currentFrameRef.current) drawFrame(index);
          resolve();
        };
        img.onerror = () => resolve();
        img.src = frameSrc(index);
      });

    const loadRange = async (from: number, to: number, concurrency: number) => {
      let next = from;
      const workers = Array.from({ length: concurrency }, async () => {
        while (next <= to && !cancelled) {
          const i = next++;
          await load(i);
        }
      });
      await Promise.all(workers);
    };

    (async () => {
      await loadRange(1, PRIORITY_COUNT, 12);
      if (cancelled) return;
      setReady(true);
      applyProgress(scrollYProgress.get());
      await loadRange(PRIORITY_COUNT + 1, FRAME_COUNT, 8);
    })();

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [drawFrame, applyProgress]);

  // Keep the canvas backing store matched to its displayed size so frames stay
  // crisp on retina screens and nothing distorts on window resize.
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const resize = () => {
      const rect = canvas.getBoundingClientRect();
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = Math.round(rect.width * dpr);
      canvas.height = Math.round(rect.height * dpr);
      drawFrame(currentFrameRef.current);
    };

    resize();
    const observer = new ResizeObserver(resize);
    observer.observe(canvas);
    return () => observer.disconnect();
  }, [drawFrame]);

  const progressPct = Math.round((loadedCount / FRAME_COUNT) * 100);

  return (
    <div ref={wrapperRef} className={styles.wrapper}>
      <div className={styles.sticky}>
        <div className={styles.stage}>
          <canvas
            ref={canvasRef}
            className={styles.canvas}
            aria-label="Animated journey of a package through the LogiTrust supply chain"
            role="img"
          />

          {!ready && (
            <div className={styles.loader}>
              <span className={styles.loaderMark} aria-hidden="true" />
              <p className={styles.loaderBrand}>LogiTrust</p>
              <div
                className={styles.loaderTrack}
                role="progressbar"
                aria-valuenow={progressPct}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-label="Loading animation frames"
              >
                <div
                  className={styles.loaderBar}
                  style={{ width: `${progressPct}%` }}
                />
              </div>
              <p className={styles.loaderPct}>{progressPct}%</p>
            </div>
          )}

          {/* 0% – 20%: opening headline */}
          <div
            ref={introRef}
            className={`${styles.overlay} ${styles.overlayIntro} ${styles.panel}`}
          >
            <span className={styles.eyebrow}>
              AI-powered supply chain integrity
            </span>
            <h1 className={styles.heading}>
              Every shipment,{" "}
              <span className={styles.headingAccent}>tracked.</span>
            </h1>
            <p className={styles.subtext}>
              From warehouse to doorstep, verified at every step.
            </p>
          </div>

          {/* 20% – 45%: checkpoint alert with ticking risk score */}
          <div
            ref={fraudRef}
            className={`${styles.overlay} ${styles.overlayFraud}`}
            style={{ opacity: 0, visibility: "hidden" }}
          >
            <div className={styles.fraudCard}>
              <div className={styles.fraudHead}>
                <span className={styles.pulseDot} aria-hidden="true" />
                AI Fraud Detection
              </div>
              <div className={styles.fraudScoreRow}>
                <span className={styles.fraudScoreLabel}>Risk score</span>
                <span ref={scoreRef} className={styles.fraudScoreValue}>
                  0
                </span>
              </div>
            </div>
          </div>

          {/* 45% – 70%: admin resolution */}
          <div
            ref={resolveRef}
            className={`${styles.overlay} ${styles.overlayResolve} ${styles.panel}`}
            style={{ opacity: 0, visibility: "hidden" }}
          >
            <span className={styles.label}>Instantly investigated</span>
            <p className={styles.subtext}>
              Every flag reviewed, every factor explained
            </p>
          </div>

          {/* 70% – 100%: delivery */}
          <div
            ref={deliveredRef}
            className={`${styles.overlay} ${styles.overlayDelivered}`}
            style={{ opacity: 0, visibility: "hidden" }}
          >
            <span className={styles.panelChip}>
              <svg
                className={styles.checkIcon}
                viewBox="0 0 24 24"
                fill="none"
                aria-hidden="true"
              >
                <circle cx="12" cy="12" r="11" fill="#067a58" />
                <path
                  d="M7 12.5l3.2 3.2L17 9"
                  stroke="#fff"
                  strokeWidth="2.4"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
              Delivered. Verified. Trusted.
            </span>
          </div>

          <div ref={hintRef} className={styles.scrollHint} aria-hidden="true">
            Scroll to follow the journey
            <span className={styles.scrollHintArrow}>↓</span>
          </div>

          <div
            ref={trackerRef}
            className={styles.tracker}
            aria-hidden="true"
          >
            {JOURNEY_STEPS.map((step, i) => (
              <div
                key={step}
                className={`${styles.step} ${i === 0 ? styles.stepActive : ""}`}
              >
                <span className={styles.stepDot} />
                <span className={styles.stepLabel}>{step}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
