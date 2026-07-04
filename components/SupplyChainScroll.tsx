"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  motion,
  useMotionValueEvent,
  useScroll,
  useTransform,
} from "motion/react";
import styles from "./SupplyChainScroll.module.css";

const FRAME_COUNT = 320;
const FRAME_WIDTH = 960;
const FRAME_HEIGHT = 540;
// Frames 1-80 are enough to start scrolling (beat 1 -> 2); the rest stream in behind.
const PRIORITY_COUNT = 80;

function frameSrc(index: number): string {
  return `/frames/frame_${String(index).padStart(4, "0")}.jpg`;
}

export default function SupplyChainScroll() {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
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

  // Scroll progress 0..1 mapped linearly onto frame index 1..320.
  const frameIndex = useTransform(scrollYProgress, [0, 1], [1, FRAME_COUNT]);

  const drawFrame = useCallback((index: number) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

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
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
  }, []);

  // Drive the canvas imperatively from the motion value — no React state on
  // scroll ticks, so scrubbing stays smooth in both directions.
  useMotionValueEvent(frameIndex, "change", (latest) => {
    drawFrame(latest);
  });

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
      drawFrame(frameIndex.get());
      await loadRange(PRIORITY_COUNT + 1, FRAME_COUNT, 8);
    })();

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [drawFrame]);

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

  // Overlay opacities, each tied to its scroll-progress window with short
  // fade ramps so copy never snaps on/off or overlaps the next beat.
  const introOpacity = useTransform(
    scrollYProgress,
    [0, 0.15, 0.2],
    [1, 1, 0]
  );
  const fraudOpacity = useTransform(
    scrollYProgress,
    [0.2, 0.24, 0.41, 0.45],
    [0, 1, 1, 0]
  );
  const resolveOpacity = useTransform(
    scrollYProgress,
    [0.45, 0.49, 0.66, 0.7],
    [0, 1, 1, 0]
  );
  const deliveredOpacity = useTransform(
    scrollYProgress,
    [0.7, 0.75, 1],
    [0, 1, 1]
  );

  // Risk score ticks 0 -> 87 while the checkpoint beat plays.
  const riskScore = useTransform(scrollYProgress, [0.22, 0.4], [0, 87], {
    clamp: true,
  });
  const riskScoreText = useTransform(riskScore, (v) => `${Math.round(v)}`);

  const progressPct = Math.round((loadedCount / FRAME_COUNT) * 100);

  return (
    <div ref={wrapperRef} className={styles.wrapper}>
      <div className={styles.sticky}>
        <div className={styles.stage}>
          <canvas
            ref={canvasRef}
            className={styles.canvas}
            style={{ aspectRatio: `${FRAME_WIDTH} / ${FRAME_HEIGHT}` }}
            aria-label="Animated journey of a package through the LogiTrust supply chain"
            role="img"
          />

          {!ready && (
            <div className={styles.loader}>
              <p className={styles.loaderLabel}>Loading journey…</p>
              <div className={styles.loaderTrack}>
                <div
                  className={styles.loaderBar}
                  style={{ width: `${progressPct}%` }}
                />
              </div>
            </div>
          )}

          {/* 0% – 20%: opening headline */}
          <motion.div
            className={`${styles.overlay} ${styles.overlayIntro}`}
            style={{ opacity: introOpacity }}
          >
            <h1 className={styles.heading}>Every shipment, tracked.</h1>
            <p className={styles.subtext}>
              From warehouse to doorstep, verified at every step.
            </p>
          </motion.div>

          {/* 20% – 45%: checkpoint alert with ticking risk score */}
          <motion.div
            className={`${styles.overlay} ${styles.overlayFraud}`}
            style={{ opacity: fraudOpacity }}
          >
            <span className={styles.label}>AI Fraud Detection</span>
            <span className={styles.riskScore}>
              Risk score <motion.span>{riskScoreText}</motion.span>
            </span>
          </motion.div>

          {/* 45% – 70%: admin resolution */}
          <motion.div
            className={`${styles.overlay} ${styles.overlayResolve}`}
            style={{ opacity: resolveOpacity }}
          >
            <span className={styles.label}>Instantly investigated</span>
            <p className={styles.subtext}>
              Every flag reviewed, every factor explained
            </p>
          </motion.div>

          {/* 70% – 100%: delivery */}
          <motion.div
            className={`${styles.overlay} ${styles.overlayDelivered}`}
            style={{ opacity: deliveredOpacity }}
          >
            <span className={styles.labelLarge}>
              Delivered. Verified. Trusted.
            </span>
          </motion.div>

          <motion.div
            className={styles.scrollHint}
            style={{ opacity: introOpacity }}
            aria-hidden="true"
          >
            Scroll
            <span className={styles.scrollHintArrow}>↓</span>
          </motion.div>
        </div>
      </div>
    </div>
  );
}
