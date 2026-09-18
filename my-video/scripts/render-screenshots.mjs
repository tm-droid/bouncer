#!/usr/bin/env node
// Renders the seven Play Store phone screenshots (the `Screenshots/*` stills in src/Root.tsx) into
// out/screenshots/, then checks each file against Play's rules: PNG, 9:16, every side 320–3840 px,
// at most 8 MB. Exits non-zero on the first violation so a broken still cannot be uploaded unnoticed.
import { execFileSync } from "node:child_process";
import { mkdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const outDir = join(root, "out", "screenshots");
const SHOTS = ["Home", "Overview", "Commentary", "Scorecard", "Series", "News", "Widgets"];
const WIDTH = 1080;
const HEIGHT = 1920;
const MAX_BYTES = 8 * 1024 * 1024;

const pngSize = (file) => {
  const buf = readFileSync(file);
  // IHDR is always the first chunk: width and height are the big-endian u32s at bytes 16 and 20.
  if (buf.toString("latin1", 1, 4) !== "PNG") throw new Error(`${file}: not a PNG`);
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) };
};

mkdirSync(outDir, { recursive: true });
const failures = [];
SHOTS.forEach((name, i) => {
  const nn = String(i + 1).padStart(2, "0");
  const id = `Shot-${nn}-${name}`;
  const file = join(outDir, `${nn}-${name.toLowerCase()}.png`);
  execFileSync("npx", ["remotion", "still", id, file], { cwd: root, stdio: "inherit" });
  const { width, height } = pngSize(file);
  const bytes = statSync(file).size;
  const ok = width === WIDTH && height === HEIGHT && bytes <= MAX_BYTES;
  console.log(`${ok ? "ok  " : "FAIL"} ${file} ${width}x${height} ${(bytes / 1024).toFixed(0)} KB`);
  if (!ok) failures.push(file);
});

if (failures.length > 0) {
  console.error(`\n${failures.length} screenshot(s) violate Play's rules (${WIDTH}x${HEIGHT}, <= 8 MB):\n  ${failures.join("\n  ")}`);
  process.exit(1);
}
console.log(`\n${SHOTS.length} screenshots in ${outDir}, upload in file order.`);
