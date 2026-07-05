import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Allows viewing the dev server from other devices on the LAN (e.g.
  // testing the scroll animation on a phone) without the HMR warning/block.
  allowedDevOrigins: ["192.168.0.111"],
};

export default nextConfig;
