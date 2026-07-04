import styles from "./SiteHeader.module.css";

export default function SiteHeader() {
  return (
    <header className={styles.header}>
      <a href="#" className={styles.brand}>
        <span className={styles.brandMark} aria-hidden="true" />
        LogiTrust
      </a>
      <nav className={styles.nav} aria-label="Main">
        <a href="#features" className={styles.navLink}>
          Features
        </a>
        <a href="#features" className={styles.cta}>
          Get started
        </a>
      </nav>
    </header>
  );
}
