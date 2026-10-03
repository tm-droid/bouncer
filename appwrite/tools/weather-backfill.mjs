#!/usr/bin/env node
//
// Historical weather for every match in the Cricsheet archive, from Open-Meteo.
//
// Cricsheet records where and when a match was played and nothing about the weather, so a model
// cannot learn from weather without this. It geocodes each match's city, asks the archive for the
// days the match was scheduled on, and writes one small feature row per match.
//
// TWO SOURCES, ON PURPOSE. A live match is served a FORECAST. Scoring a holdout on what the
// weather turned out to be would credit the model with hindsight it will never have, so matches in
// the holdout window are also fetched from the historical-forecast API — what was predicted at the
// time — and the lab scores the holdout on those. The archive (what happened) is for training.
//
// Resumable: every answer is cached under the cache directory and a rerun asks only for what is
// missing. Open-Meteo's free tier allows about 10,000 calls a day; `--limit` stops a run short of
// that, and the whole archive takes three runs on three days.
//
// USAGE
//   node appwrite/tools/weather-backfill.mjs [--zip path] [--limit 9000] [--forecast-from 2024-12-27]
//
// Output: <cache>/weather-features.json (archive) and <cache>/weather-forecast-features.json,
// each `{ "<zip entry name>": [rainMm, humidity, dewSpread, windKph, cloud, 1] }`.

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const trainerDir = path.resolve(here, "../functions/train-win-probability");
const require = createRequire(path.join(trainerDir, "package.json"));
const AdmZip = require("adm-zip");
const { formatClassOf } = await import(path.join(trainerDir, "src/training.js"));

const argv = process.argv.slice(2);
const flag = (name, fallback) => {
  const i = argv.indexOf(name);
  return i === -1 ? fallback : argv[i + 1];
};
const cacheDir = flag("--cache", path.join(os.homedir(), "Library/Caches/bouncer-winprob-lab"));
const zipPath = flag("--zip", path.join(cacheDir, "all_json.zip"));
const limit = Number(flag("--limit", "9000"));
const forecastFrom = flag("--forecast-from", "2024-12-27");

const DAILY = "precipitation_sum,precipitation_hours,temperature_2m_mean,relative_humidity_2m_mean,dew_point_2m_mean,wind_speed_10m_max,cloud_cover_mean";
const ARCHIVE = "https://archive-api.open-meteo.com/v1/archive";
const HISTORICAL_FORECAST = "https://historical-forecast-api.open-meteo.com/v1/forecast";
const GEOCODE = "https://geocoding-api.open-meteo.com/v1/search";
/** Open-Meteo asks for no more than 600 calls a minute; this keeps well under it. */
const GAP_MS = 220;

const load = (name, fallback) => {
  const file = path.join(cacheDir, name);
  return fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, "utf8")) : fallback;
};
const save = (name, value) => fs.writeFileSync(path.join(cacheDir, name), JSON.stringify(value));

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let calls = 0;
async function getJson(url) {
  calls++;
  await sleep(GAP_MS);
  for (let attempt = 1; ; attempt++) {
    let res;
    try {
      res = await fetch(url, { signal: AbortSignal.timeout(30_000) });
    } catch (e) {
      // A dropped connection is not an answer. Only a run of them stops the backfill.
      if (attempt >= 5) throw new Error(`${e?.message ?? e} (${attempt} attempts) for ${url}`);
      await sleep(3000 * attempt);
      continue;
    }
    if (res.ok) return res.json();
    if (res.status === 429) throw new Error("429 — the daily allowance is spent; rerun tomorrow");
    if (attempt >= 5 || res.status < 500) throw new Error(`HTTP ${res.status} for ${url}`);
    await sleep(3000 * attempt);
  }
}

// A country as Open-Meteo names it, for a side as Cricsheet names it. A city name alone is
// ambiguous — "Hamilton" is Ontario before it is Waikato — and the sides that have played there
// are the only hint the archive carries.
const TEAM_COUNTRY = {
  England: "United Kingdom", Scotland: "United Kingdom", Wales: "United Kingdom",
  "United Arab Emirates": "United Arab Emirates", "United States of America": "United States",
  "Hong Kong": "Hong Kong", "Papua New Guinea": "Papua New Guinea",
};
const WEST_INDIES = new Set(["Barbados", "Jamaica", "Trinidad and Tobago", "Guyana", "Antigua and Barbuda", "Saint Lucia", "Grenada", "Saint Kitts and Nevis", "Saint Vincent and the Grenadines", "Dominica"]);
const CRICKET_COUNTRIES = ["India", "Australia", "United Kingdom", "South Africa", "New Zealand", "Pakistan", "Sri Lanka", "Bangladesh", "Zimbabwe", "Ireland", "United Arab Emirates", "Afghanistan", "Netherlands", "Nepal", "Oman", "Namibia", "Kenya", "Uganda", "Malaysia", "Singapore", "Hong Kong", "Canada", "United States"];

function chooseCandidate(results, teamCounts) {
  if (!results?.length) return null;
  let best = null;
  let bestScore = -1;
  for (const r of results) {
    let score = 0;
    for (const [team, n] of teamCounts) {
      const country = TEAM_COUNTRY[team] ?? team;
      if (country === r.country || (team === "West Indies" && WEST_INDIES.has(r.country))) score += n * 1000;
    }
    const rank = CRICKET_COUNTRIES.indexOf(r.country);
    if (WEST_INDIES.has(r.country)) score += 50;
    else if (rank >= 0) score += 100 - rank;
    score += Math.min((r.population ?? 0) / 1e7, 1);
    if (score > bestScore) {
      bestScore = score;
      best = r;
    }
  }
  return best;
}

console.error(`Reading ${zipPath} ...`);
const zip = new AdmZip(zipPath);
const matches = [];
for (const entry of zip.getEntries()) {
  if (entry.isDirectory || !entry.entryName.endsWith(".json")) continue;
  try {
    const info = JSON.parse(entry.getData().toString("utf8")).info;
    const formatClass = formatClassOf(info);
    if (!formatClass || !info.dates?.length) continue;
    const place = info.city ?? String(info.venue ?? "").split(",").pop().trim();
    if (!place) continue;
    matches.push({ file: entry.entryName, formatClass, place, teams: info.teams ?? [], dates: info.dates.map((d) => String(d).slice(0, 10)) });
  } catch {
    // An unreadable entry has no weather to fetch.
  }
}
// Tests first — rain decides draws, so that is where weather is most likely to earn its place —
// then everything else, newest first, so the holdout window is covered on the first day.
matches.sort((a, b) => (a.formatClass === "TEST" ? 0 : 1) - (b.formatClass === "TEST" ? 0 : 1) || b.dates[0].localeCompare(a.dates[0]));
console.error(`${matches.length} matches with a date and a place`);

const aliases = fs.existsSync(path.join(here, "venue-aliases.json")) ? JSON.parse(fs.readFileSync(path.join(here, "venue-aliases.json"), "utf8")) : {};
const geocode = load("geocode.json", {});
const teamsByPlace = new Map();
for (const m of matches) {
  const counts = teamsByPlace.get(m.place) ?? new Map();
  for (const t of m.teams) counts.set(t, (counts.get(t) ?? 0) + 1);
  teamsByPlace.set(m.place, counts);
}

const raw = { archive: load("weather-raw.json", {}), forecast: load("weather-forecast-raw.json", {}) };
let spent = false;

async function locate(place) {
  if (place in geocode) return geocode[place];
  if (aliases[place]) return (geocode[place] = aliases[place]);
  const body = await getJson(`${GEOCODE}?name=${encodeURIComponent(place)}&count=10`);
  const hit = chooseCandidate(body.results, teamsByPlace.get(place) ?? new Map());
  geocode[place] = hit ? { lat: hit.latitude, lon: hit.longitude, country: hit.country, name: hit.name } : null;
  return geocode[place];
}

async function fetchDays(base, at, m) {
  const start = m.dates[0];
  const end = m.dates[m.dates.length - 1];
  const body = await getJson(`${base}?latitude=${at.lat}&longitude=${at.lon}&start_date=${start}&end_date=${end}&daily=${DAILY}&timezone=auto`);
  return body.daily ?? null;
}

try {
  for (const m of matches) {
    if (calls >= limit) break;
    const wantArchive = !(m.file in raw.archive);
    const wantForecast = m.dates[0] >= forecastFrom && !(m.file in raw.forecast);
    if (!wantArchive && !wantForecast) continue;
    const at = await locate(m.place);
    if (!at) {
      if (wantArchive) raw.archive[m.file] = null;
      if (wantForecast) raw.forecast[m.file] = null;
      continue;
    }
    if (wantArchive) raw.archive[m.file] = await fetchDays(ARCHIVE, at, m);
    if (wantForecast) raw.forecast[m.file] = await fetchDays(HISTORICAL_FORECAST, at, m);
    if (calls % 500 === 0) {
      save("geocode.json", geocode);
      save("weather-raw.json", raw.archive);
      save("weather-forecast-raw.json", raw.forecast);
      console.error(`${calls} calls`);
    }
  }
} catch (e) {
  spent = true;
  console.error(`Stopped: ${e.message}`);
}
save("geocode.json", geocode);
save("weather-raw.json", raw.archive);
save("weather-forecast-raw.json", raw.forecast);

const mean = (list) => {
  const values = (list ?? []).filter((v) => typeof v === "number");
  return values.length ? values.reduce((a, b) => a + b, 0) / values.length : null;
};

/**
 * One match's weather as model features, each centred near zero for an ordinary day.
 * Null when the source had nothing — the lab then leaves the group masked for that match.
 */
export function weatherFeatures(daily) {
  if (!daily) return null;
  const rain = mean(daily.precipitation_sum);
  const humidity = mean(daily.relative_humidity_2m_mean);
  const temperature = mean(daily.temperature_2m_mean);
  const dew = mean(daily.dew_point_2m_mean);
  const wind = mean(daily.wind_speed_10m_max);
  const cloud = mean(daily.cloud_cover_mean);
  if ([rain, humidity, temperature, dew, wind, cloud].some((v) => v === null)) return null;
  return [
    Math.min(rain, 30) / 10,
    humidity / 100 - 0.65,
    (temperature - dew) / 10 - 0.8,
    wind / 30 - 0.5,
    cloud / 100 - 0.5,
    1,
  ];
}

const derive = (source) => {
  const out = {};
  for (const [file, daily] of Object.entries(source)) {
    const features = weatherFeatures(daily);
    if (features) out[file] = features.map((v) => Math.round(v * 1000) / 1000);
  }
  return out;
};
save("weather-features.json", derive(raw.archive));
save("weather-forecast-features.json", derive(raw.forecast));

const have = Object.values(raw.archive).filter(Boolean).length;
const unplaced = Object.values(geocode).filter((v) => v === null).length;
console.error(
  `${calls} calls this run${spent ? " (stopped early)" : ""}. Archive weather for ${have}/${matches.length} matches, ` +
  `forecast for ${Object.values(raw.forecast).filter(Boolean).length}; ${unplaced} places could not be geocoded.`
);
