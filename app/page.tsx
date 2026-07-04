import SupplyChainScroll from "@/components/SupplyChainScroll";
import styles from "./page.module.css";

const features = [
  {
    title: "Tamper-evident custody chain",
    body: "Every handoff is hash-linked to the one before it. Edit history anywhere and the chain breaks — visibly, and exactly where it happened.",
  },
  {
    title: "AI fraud scoring",
    body: "Route deviations, timing anomalies, condition breaches, quantity mismatches and duplicate serials roll into one explainable risk score per checkpoint.",
  },
  {
    title: "Counterfeit detection",
    body: "Serial and QR verification at every scan. A serial already delivered elsewhere — or one that doesn't exist — is rejected on the spot.",
  },
  {
    title: "Admin investigation",
    body: "Flagged shipments queue by risk score with the full custody chain and every contributing factor laid out, so cases resolve in minutes.",
  },
];

export default function Home() {
  return (
    <main>
      <SupplyChainScroll />

      <section className={styles.features}>
        <div className={styles.featuresInner}>
          <h2 className={styles.featuresHeading}>
            Trust, built into every handoff
          </h2>
          <p className={styles.featuresLead}>
            LogiTrust tracks goods across manufacturers, distributors,
            couriers, retailers and customers — treating every handoff as a
            security event, not a status update.
          </p>
          <div className={styles.grid}>
            {features.map((f) => (
              <div key={f.title} className={styles.card}>
                <h3 className={styles.cardTitle}>{f.title}</h3>
                <p className={styles.cardBody}>{f.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <footer className={styles.footer}>
        <span>LogiTrust — AI-powered supply chain integrity</span>
      </footer>
    </main>
  );
}
