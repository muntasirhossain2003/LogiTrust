import Reveal from "@/components/Reveal";
import SiteHeader from "@/components/SiteHeader";
import SupplyChainScroll from "@/components/SupplyChainScroll";
import styles from "./page.module.css";

const features = [
  {
    title: "Tamper-evident custody chain",
    body: "Every handoff record is SHA-256 hash-linked to the one before it. Edit history anywhere and the chain breaks — visibly, and exactly where it happened.",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7" />
        <path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7" />
      </svg>
    ),
    wide: true,
  },
  {
    title: "AI fraud scoring",
    body: "Route deviations, timing anomalies, condition breaches, quantity mismatches and duplicate serials roll into one explainable 0–100 risk score per checkpoint.",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M12 2v4M12 18v4M2 12h4M18 12h4" />
        <circle cx="12" cy="12" r="7" />
        <circle cx="12" cy="12" r="2.5" fill="currentColor" stroke="none" />
      </svg>
    ),
    wide: true,
  },
  {
    title: "Counterfeit detection",
    body: "Serial and QR verification at every scan. A serial already delivered elsewhere — or one that doesn't exist — is rejected on the spot.",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <rect x="3" y="3" width="7" height="7" rx="1.5" />
        <rect x="14" y="3" width="7" height="7" rx="1.5" />
        <rect x="3" y="14" width="7" height="7" rx="1.5" />
        <path d="M14 14h3v3h-3zM20 14h1M14 20h1M18 18h3v3h-3z" />
      </svg>
    ),
    wide: false,
  },
  {
    title: "Admin investigation",
    body: "Flagged shipments queue by risk score with the full custody chain and every contributing factor laid out, so cases resolve in minutes — not weeks.",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6l7-3z" />
        <circle cx="11" cy="11" r="2.6" />
        <path d="M13 13l2.6 2.6" />
      </svg>
    ),
    wide: false,
  },
  {
    title: "Real-time alerts & SLA watch",
    body: "Automated notifications the moment a shipment is flagged, frozen or delayed — with scheduled sweeps catching SLA breaches every 15 minutes.",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M18 8a6 6 0 1 0-12 0c0 7-3 8-3 8h18s-3-1-3-8" />
        <path d="M10.3 21a2 2 0 0 0 3.4 0" />
      </svg>
    ),
    wide: false,
  },
];

const journey = [
  {
    step: "01",
    title: "Created & serialized",
    body: "The manufacturer creates the shipment; every item gets a unique serial and QR code.",
  },
  {
    step: "02",
    title: "Tracked in transit",
    body: "Couriers log location and condition at each checkpoint along the expected route.",
  },
  {
    step: "03",
    title: "Scored at the checkpoint",
    body: "The AI engine scores every event for fraud risk the moment it happens.",
  },
  {
    step: "04",
    title: "Investigated instantly",
    body: "High-risk shipments freeze automatically and land in the admin's queue, factors attached.",
  },
  {
    step: "05",
    title: "Delivered & verified",
    body: "The receiver confirms the handoff and the custody chain closes — intact and provable.",
  },
];

const stats = [
  { value: "0–100", label: "explainable risk scoring" },
  { value: "SHA-256", label: "hash-chained custody records" },
  { value: "6 roles", label: "from manufacturer to customer" },
  { value: "<500ms", label: "fraud scoring per checkpoint" },
];

export default function Home() {
  return (
    <main>
      <SiteHeader />
      <SupplyChainScroll />

      {/* Sheet: slides over the hero as it unpins, hiding the seam */}
      <div className={styles.sheet}>
        <section id="features" className={styles.features}>
          <div className={styles.inner}>
            <Reveal className={styles.sectionHead}>
              <span className={styles.kicker}>Why LogiTrust</span>
              <h2 className={styles.sectionHeading}>
                Trust, built into every handoff
              </h2>
              <p className={styles.sectionLead}>
                LogiTrust tracks goods across manufacturers, distributors,
                couriers, retailers and customers — treating every handoff as
                a security event, not a status update.
              </p>
            </Reveal>

            <div className={styles.bento}>
              {features.map((f, i) => (
                <Reveal
                  key={f.title}
                  delay={i * 0.06}
                  className={`${styles.card} ${f.wide ? styles.cardWide : ""}`}
                >
                  <span className={styles.cardIcon}>{f.icon}</span>
                  <h3 className={styles.cardTitle}>{f.title}</h3>
                  <p className={styles.cardBody}>{f.body}</p>
                </Reveal>
              ))}
            </div>
          </div>
        </section>

        <section className={styles.statsBand}>
          <div className={`${styles.inner} ${styles.statsRow}`}>
            {stats.map((s, i) => (
              <Reveal key={s.label} delay={i * 0.06} className={styles.stat}>
                <span className={styles.statValue}>{s.value}</span>
                <span className={styles.statLabel}>{s.label}</span>
              </Reveal>
            ))}
          </div>
        </section>

        <section id="journey" className={styles.journey}>
          <div className={styles.inner}>
            <Reveal className={styles.sectionHead}>
              <span className={styles.kicker}>How it works</span>
              <h2 className={styles.sectionHeading}>
                Five steps. Zero blind spots.
              </h2>
            </Reveal>

            <div className={styles.timeline}>
              {journey.map((j, i) => (
                <Reveal
                  key={j.step}
                  delay={i * 0.07}
                  className={styles.timelineItem}
                >
                  <span className={styles.timelineStep}>{j.step}</span>
                  <h3 className={styles.timelineTitle}>{j.title}</h3>
                  <p className={styles.timelineBody}>{j.body}</p>
                </Reveal>
              ))}
            </div>
          </div>
        </section>

        <section className={styles.cta}>
          <div className={styles.inner}>
            <Reveal className={styles.ctaPanel}>
              <h2 className={styles.ctaHeading}>
                Ship with proof, not promises.
              </h2>
              <p className={styles.ctaLead}>
                Give every party in your supply chain a history they can
                verify and a risk score they can act on.
              </p>
              <div className={styles.ctaActions}>
                <a href="#features" className={styles.ctaPrimary}>
                  Get started
                </a>
                <a href="#journey" className={styles.ctaSecondary}>
                  See how it works
                </a>
              </div>
            </Reveal>
          </div>
        </section>

        <footer className={styles.footer}>
          <div className={`${styles.inner} ${styles.footerGrid}`}>
            <div className={styles.footerBrand}>
              <span className={styles.footerMark} aria-hidden="true" />
              <span className={styles.footerName}>LogiTrust</span>
              <p className={styles.footerTag}>
                AI-powered supply chain integrity — from first handoff to
                final doorstep.
              </p>
            </div>
            <nav className={styles.footerCol} aria-label="Platform">
              <span className={styles.footerColTitle}>Platform</span>
              <a href="#features">Custody chain</a>
              <a href="#features">Fraud scoring</a>
              <a href="#features">Counterfeit detection</a>
              <a href="#features">Analytics</a>
            </nav>
            <nav className={styles.footerCol} aria-label="Roles">
              <span className={styles.footerColTitle}>Built for</span>
              <a href="#journey">Manufacturers</a>
              <a href="#journey">Distributors & couriers</a>
              <a href="#journey">Retailers</a>
              <a href="#journey">Auditors</a>
            </nav>
          </div>
          <div className={styles.footerBar}>
            <div className={styles.inner}>
              <span>© 2026 LogiTrust. Every shipment, tracked.</span>
            </div>
          </div>
        </footer>
      </div>
    </main>
  );
}
