/**
 * What is known about a match before and beside the score: the two sides, the ground, the
 * elevens and the forecast.
 *
 * Built once per match and kept — in memory for the container, and in the bucket as
 * `mc-{matchId}` so a cold container and the device both read the same thing. It costs no
 * database reads: every input is a bucket file the trainer publishes, a Cricbuzz page, or
 * Open-Meteo.
 *
 * Nothing here is allowed to become a guess. A side the archive does not know, a ground whose
 * name will not join, a player who cannot be identified — each stays absent, and the model has a
 * neutral value for every absence. A wrong join would be worse than none: it would carry someone
 * else's record into the probability as a fact.
 */

import {
  UNKNOWN_RATING,
  XI_MIN_KNOWN,
  h2hAdvantage,
  pairKey,
  shardOf,
  sideContext,
  squash,
  venueName,
  venueView,
  xiStrength,
} from "./contextmath.js";
import { resolvePlayer } from "./playerjoin.js";

export const CONTEXT_VERSION = 1;
export const contextFileId = (matchId) => `mc-${String(matchId).replace(/[^a-zA-Z0-9._-]/g, "_").slice(0, 32)}`;

/** A team sheet is asked for again this often until it carries a playing eleven. */
export const SQUAD_RETRY_MS = 10 * 60_000;
/** A forecast older than this is refreshed. */
export const FORECAST_TTL_MS = 60 * 60_000;
/** Hours of forecast kept. A limited-overs match is over well inside this; a Test day fits. */
const FORECAST_HOURS = 30;

const FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
const FORECAST_FIELDS = "temperature_2m,relative_humidity_2m,dew_point_2m,precipitation,precipitation_probability,wind_speed_10m,cloud_cover";

const round = (v, places) => (typeof v === "number" && Number.isFinite(v) ? Number(v.toFixed(places)) : null);

// ── joins ────────────────────────────────────────────────────────────────────

/** A side's name as the archive writes it, or null when the archive has no such side in this slice. */
export function archiveTeamName(name, teamStats, gender, format) {
  const wanted = squash(String(name ?? "").replace(/\s+Women(?:'s)?$/i, ""));
  if (!wanted) return null;
  const suffix = `|${gender}|${format}`;
  for (const key of Object.keys(teamStats ?? {})) {
    if (key.endsWith(suffix) && squash(key.slice(0, key.length - suffix.length)) === wanted) {
      return key.slice(0, key.length - suffix.length);
    }
  }
  return null;
}

/**
 * The archive's record of a ground, or null.
 *
 * A name can be several grounds ("County Ground"), so the city decides between them; a name the
 * archive has exactly once is accepted without one, because Cricbuzz and Cricsheet do not always
 * agree on what a ground's city is called.
 */
export function pickGround(grounds, city) {
  if (!Array.isArray(grounds) || grounds.length === 0) return null;
  const wanted = squash(city);
  const sameCity = grounds.filter((g) => g.city === wanted);
  if (sameCity.length === 1) return sameCity[0];
  return grounds.length === 1 ? grounds[0] : null;
}

/** `[avg, strike, recent, bowl, batBalls, bowlBalls]` as the rating object `xiStrength` reads. */
function ratingOf(row) {
  if (!Array.isArray(row)) return UNKNOWN_RATING;
  return { avg: row[0], strike: row[1], recent: row[2], bowl: row[3], batBalls: row[4], bowlBalls: row[5] };
}

const rosterEntries = (list) => (Array.isArray(list) ? list.map(([id, names, ratings, gender]) => ({ id, names, ratings, gender })) : []);

/**
 * One eleven joined to the archive.
 *
 * A player is looked for in the side's own recent roster first, then — for a player new to that
 * side — among everyone of that gender whose surname starts with the same letter. Unjoined
 * players count as unknowns, and below `XI_MIN_KNOWN` joined the side has no eleven at all,
 * exactly as it was thinned in training.
 */
export function joinEleven({ players, roster, surnamePools, gender, format }) {
  if (!Array.isArray(players) || players.length < 9) return { strength: null, joined: 0, listed: players?.length ?? 0, members: [] };
  let joined = 0;
  const members = [];
  const ratings = players.map((p) => {
    let hit = resolvePlayer(roster, p.name);
    if (!hit) {
      const letter = squash(p.name).split(" ").pop()?.[0];
      const pool = (surnamePools?.[letter] ?? []).filter((e) => e.gender === gender);
      hit = resolvePlayer(pool, p.name);
    }
    members.push({ cbId: p.id, name: p.name, pid: hit?.id ?? null });
    if (!hit) return UNKNOWN_RATING;
    joined++;
    return ratingOf(hit.ratings?.[format]);
  });
  return { strength: joined >= XI_MIN_KNOWN ? xiStrength(ratings) : null, joined, listed: players.length, members };
}

// ── forecast ─────────────────────────────────────────────────────────────────

/**
 * The next `FORECAST_HOURS` hours at a ground, or null.
 *
 * This is a FORECAST. It is published and labelled as one, with the time it was fetched; nothing
 * downstream may say it is raining because of it.
 */
export async function fetchForecast({ lat, lon }, fetchImpl = fetch, now = Date.now()) {
  if (typeof lat !== "number" || typeof lon !== "number") return null;
  const url = `${FORECAST_URL}?latitude=${lat}&longitude=${lon}&hourly=${FORECAST_FIELDS}&forecast_days=3&timeformat=unixtime&timezone=GMT`;
  const res = await fetchImpl(url, { signal: AbortSignal.timeout(4_000) });
  if (!res.ok) throw new Error(`Open-Meteo HTTP ${res.status}`);
  const hourly = (await res.json())?.hourly;
  if (!Array.isArray(hourly?.time)) return null;
  const hours = [];
  for (let i = 0; i < hourly.time.length && hours.length < FORECAST_HOURS; i++) {
    const at = hourly.time[i] * 1000;
    if (at < now - 3_600_000) continue;
    hours.push({
      t: at,
      tempC: round(hourly.temperature_2m?.[i], 1),
      humidity: round(hourly.relative_humidity_2m?.[i], 0),
      dewC: round(hourly.dew_point_2m?.[i], 1),
      rainMm: round(hourly.precipitation?.[i], 1),
      rainChance: round(hourly.precipitation_probability?.[i], 0),
      windKph: round(hourly.wind_speed_10m?.[i], 0),
      cloud: round(hourly.cloud_cover?.[i], 0),
    });
  }
  return hours.length ? { source: "open-meteo", fetchedAt: now, hours } : null;
}

// ── the context ──────────────────────────────────────────────────────────────

/**
 * Build a match's context from its meta, its team sheet and the published tables.
 *
 * Pure: every table arrives as an argument, so this is the part that is tested.
 *
 * @param meta     `parseMatchMeta` of the match page
 * @param squads   `parseSquads` of the team-sheet page, or null
 * @param model    the win-probability artifact (`teamStats`, `means`, `asOf`)
 * @param tables   `{ rosters, venues, h2h, surnames }` — the shard entries this match touches
 */
export function buildContext({ matchId, meta, squads, model, format, gender, tables, previous = null, now = Date.now() }) {
  const slice = `${gender}|${format}`;
  const means = model?.means?.[slice] ?? null;
  const teamStats = model?.teamStats ?? {};

  const sheets = new Map();
  for (const side of [squads?.team1, squads?.team2]) if (side?.teamId) sheets.set(String(side.teamId), side);

  const teams = (meta?.teams ?? []).map((t) => {
    const key = archiveTeamName(t.name, teamStats, gender, format);
    const stats = key ? teamStats[`${key}|${slice}`] : null;
    const sheet = sheets.get(String(t.id));
    const eleven = joinEleven({
      players: sheet?.playingXi?.length ? sheet.playingXi : null,
      roster: rosterEntries(tables?.rosters?.[`${key}|${gender}`]),
      surnamePools: tables?.surnames,
      gender,
      format,
    });
    return {
      id: t.id,
      name: t.name,
      shortName: t.shortName,
      key,
      known: Boolean(stats),
      elo: stats?.elo ?? null,
      form: stats?.form ?? null,
      winRate: stats?.winRate ?? null,
      games: stats?.matchesPlayed ?? 0,
      visits: 0,
      xi: means ? eleven.strength : null,
      joined: eleven.joined,
      listed: eleven.listed,
      members: eleven.members,
    };
  });

  let venue = null;
  if (meta?.venue) {
    const ground = pickGround(tables?.venues?.[venueName(meta.venue.name)], meta.venue.city);
    const view = means ? venueView(ground?.slices?.[slice] ? countsOf(ground.slices[slice]) : null, means) : null;
    for (const t of teams) t.visits = t.key ? ground?.visits?.[`${t.key}|${gender}`] ?? 0 : 0;
    venue = {
      ...meta.venue,
      known: Boolean(ground),
      matches: view?.matches ?? 0,
      scoring: round(view?.scoring ?? 1, 3),
      chaseAdvantage: round(view?.chaseAdvantage ?? 0, 3),
      drawAdvantage: round(view?.drawAdvantage ?? 0, 3),
    };
  }

  let h2h = null;
  if (teams.length === 2 && teams[0].key && teams[1].key) {
    const record = tables?.h2h?.[pairKey(teams[0].key, teams[1].key, gender, format)];
    if (Array.isArray(record)) {
      const firstIsSortedFirst = [teams[0].key, teams[1].key].sort()[0] === teams[0].key;
      h2h = {
        games: record[0],
        firstWins: firstIsSortedFirst ? record[1] : record[2],
        secondWins: firstIsSortedFirst ? record[2] : record[1],
      };
    }
  }

  const body = {
    v: CONTEXT_VERSION,
    matchId: String(matchId),
    format,
    gender,
    modelAsOf: model?.asOf ?? null,
    venue,
    toss: meta?.toss ?? null,
    dayNight: meta?.dayNight ?? null,
    startMs: meta?.startMs ?? null,
    teams,
    h2h,
    forecast: previous?.forecast ?? null,
  };
  return { ...body, rev: (previous?.rev ?? 0) + 1, builtAt: now };
}

function countsOf(row) {
  const [matches, firstRatioSum, firstRatioN, chases, chaseWins, draws] = row;
  return { matches, firstRatioSum, firstRatioN, chases, chaseWins, draws };
}

/** Whether both sides have an eleven the model can use. */
export const hasElevens = (context) => context?.teams?.length === 2 && context.teams.every((t) => t.xi);

/**
 * [context] as the model reads it from the side with Cricbuzz team id [ownId], or null when that
 * id is not one of the two sides.
 */
export function sideContextFor(context, ownId) {
  const teams = context?.teams;
  if (!Array.isArray(teams) || teams.length !== 2) return null;
  const ownIndex = teams.findIndex((t) => String(t.id) === String(ownId));
  if (ownIndex < 0) return null;
  const own = teams[ownIndex];
  const opp = teams[1 - ownIndex];
  // A side the archive does not know has no Elo, no form and no record; left as nulls here they
  // become the neutral values inside `sideContext`. One known side against one unknown must not
  // read as an edge, so the team terms are used only when both are known.
  const bothKnown = own.known && opp.known;
  const of = (t) => ({
    elo: bothKnown ? t.elo ?? undefined : undefined,
    form: bothKnown ? t.form ?? undefined : undefined,
    winRate: bothKnown ? t.winRate ?? undefined : undefined,
    visits: t.visits,
    xi: t.xi,
  });
  const h = context.h2h;
  const ownWins = h ? (ownIndex === 0 ? h.firstWins : h.secondWins) : 0;
  const oppWins = h ? (ownIndex === 0 ? h.secondWins : h.firstWins) : 0;
  return sideContext({
    own: of(own),
    opp: of(opp),
    h2hOwn: h ? h2hAdvantage(ownWins, oppWins, h.games) : 0,
    venue: context.venue?.known ? context.venue : null,
  });
}

/** The table files a match needs, by what they are for. */
export function shardIdsFor({ teamKeys, gender, format, venue }) {
  const ids = new Set();
  for (const key of teamKeys) if (key) ids.add(`wpx-${shardOf(`r:${key}|${gender}`)}`);
  if (venue?.name) ids.add(`wpx-${shardOf(`v:${venueName(venue.name)}`)}`);
  if (teamKeys.length === 2 && teamKeys[0] && teamKeys[1]) {
    ids.add(`wpx-${shardOf(`h:${pairKey(teamKeys[0], teamKeys[1], gender, format)}`)}`);
  }
  return [...ids];
}

/** Merge the shard files a match touched into one `tables` object. */
export function mergeTables(shards) {
  const tables = { rosters: {}, venues: {}, h2h: {}, surnames: {} };
  for (const shard of shards) {
    if (!shard) continue;
    Object.assign(tables.rosters, shard.rosters ?? {});
    Object.assign(tables.venues, shard.venues ?? {});
    Object.assign(tables.h2h, shard.h2h ?? {});
  }
  return tables;
}

/** The surname pools worth fetching: one per first letter of a surname no roster resolved. */
export function surnameLettersFor(squads, tables, teams, gender) {
  const letters = new Set();
  for (const [side, team] of [[squads?.team1, teams[0]], [squads?.team2, teams[1]]]) {
    const roster = rosterEntries(tables.rosters?.[`${team?.key}|${gender}`]);
    for (const p of side?.playingXi ?? []) {
      if (resolvePlayer(roster, p.name)) continue;
      const letter = squash(p.name).split(" ").pop()?.[0];
      if (letter && /[a-z]/.test(letter)) letters.add(letter);
    }
  }
  return [...letters];
}

export { rosterEntries };
