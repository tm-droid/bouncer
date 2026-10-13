#!/usr/bin/env node
//
// Run one Appwrite function's handler outside Appwrite, as a scheduled execution.
//
// USAGE
//   node run-function.mjs train-win-probability
//
// Exists for `bouncer-train-win-probability`. The weekly refit peaks near 760 MB, and Appwrite's
// Free plan fixes every function at 512 MB / 0.5 CPU, so the job runs on a GitHub Actions runner
// instead (`.github/workflows/train-win-probability.yml`) and writes the same files to the same
// bucket. The handler is imported unchanged: it still reads the endpoint, project and key from the
// environment Appwrite would have injected, which the caller sets.
//
// Exits non-zero when the handler answers 4xx/5xx or throws, so a failed refit fails the workflow.

import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const name = process.argv[2];
if (!name) {
  console.error("usage: node run-function.mjs <function directory name>");
  process.exit(1);
}
for (const key of ["APPWRITE_FUNCTION_API_ENDPOINT", "APPWRITE_FUNCTION_PROJECT_ID", "APPWRITE_API_KEY"]) {
  if (!process.env[key]) {
    console.error(`missing ${key}`);
    process.exit(1);
  }
}

const toolsDir = path.dirname(fileURLToPath(import.meta.url));
const entry = path.resolve(toolsDir, "..", "functions", name, "src", "main.js");
const { default: handler } = await import(pathToFileURL(entry).href);

const res = {
  json: (body, status = 200) => ({ body, status }),
  text: (body, status = 200) => ({ body, status }),
  empty: () => ({ body: "", status: 204 }),
};
const out = await handler({
  req: { headers: { "x-appwrite-trigger": "schedule" }, body: "", bodyRaw: "", path: "/", method: "POST" },
  res,
  log: (...a) => console.log(...a),
  error: (...a) => console.error(...a),
});
console.log(`status ${out?.status}: ${JSON.stringify(out?.body).slice(0, 2000)}`);
process.exitCode = out && out.status < 400 ? 0 : 1;
