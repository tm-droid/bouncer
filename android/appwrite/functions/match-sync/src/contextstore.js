/**
 * Keeps one context per match: fetches what it needs, at most a little each run, and remembers it.
 *
 * `context.js` is the arithmetic and is pure; this is the part that talks to Cricbuzz, Open-Meteo
 * and the bucket. Every dependency is passed in, so the bounds below — the only thing standing
 * between sixty upcoming fixtures and sixty extra Cricbuzz fetches in one minute — can be tested
 * without a network.
 */

import {
  CONTEXT_VERSION,
  FORECAST_TTL_MS,
  SQUAD_RETRY_MS,
  archiveTeamName,
  buildContext,
  contextFileId,
  hasElevens,
  mergeTables,
  rosterEntries,
  shardIdsFor,
  surnameLettersFor,
} from "./context.js";

/** How long a table shard is trusted before it is fetched again. The trainer rewrites them weekly. */
const SHARD_TTL_MS = 6 * 60 * 60_000;

/** New contexts built, and forecasts refreshed, per run. Each build is up to two Cricbuzz pages. */
export const BUILDS_PER_RUN = 2;
export const FORECASTS_PER_RUN = 4;
/** Previews and summaries written per run. Each is one writer call and one checker call. */
export const SURFACES_PER_RUN = 1;

export function newBudget() {
  return { builds: BUILDS_PER_RUN, forecasts: FORECASTS_PER_RUN, surfaces: SURFACES_PER_RUN };
}

/**
 * @param deps `{ readJson(fileId), writeJson(fileId, value), fetchPage(url), parseMatchMeta, parseSquads,
 *               squadsUrl(matchId), matchUrl(matchId), fetchForecast, log, error, now }`
 */
export function createContextStore(deps) {
  const contexts = new Map();
  const checkedBucket = new Set();
  // When a team sheet was last asked for, and how many times it came back without an eleven. A
  // match Cricbuzz never publishes an eleven for is asked less and less often, up to hourly.
  const lastSquadTry = new Map();
  const squadMisses = new Map();
  const shards = new Map();
  const now = () => (deps.now ? deps.now() : Date.now());

  // Contexts built while a shard read failed (not a 404). They are served from memory but never
  // stored, and are built again on a later run: a missing shard would otherwise stay missing for
  // the life of the match.
  const degraded = new Set();

  /** A shard's contents; null when it does not exist. A read that fails otherwise is pushed to `failed`. */
  async function shard(id, failed) {
    const cached = shards.get(id);
    if (cached && now() - cached.at < SHARD_TTL_MS) return cached.data;
    let data = null;
    try {
      data = await deps.readJson(id);
    } catch (e) {
      if (e?.code !== 404) {
        failed.push(id);
        deps.log(`Context shard ${id}: read failed (${e?.message})`);
        return null;
      }
    }
    // A missing file is remembered, briefly, so it is not asked for every minute.
    shards.set(id, { data, at: data ? now() : now() - SHARD_TTL_MS + 5 * 60_000 });
    return data;
  }

  async function build({ matchId, meta, model, format, gender, previous }) {
    lastSquadTry.set(matchId, now());
    const squads = await deps
      .fetchPage(deps.squadsUrl(matchId))
      .then((html) => deps.parseSquads(html))
      .catch((e) => {
        if (e?.blocked) throw e;
        deps.log(`Context ${matchId}: no team sheet (${e?.message})`);
        return null;
      });

    const teamKeys = (meta?.teams ?? []).map((t) => archiveTeamName(t.name, model?.teamStats, gender, format));
    const failed = [];
    const files = await Promise.all(shardIdsFor({ teamKeys, gender, format, venue: meta?.venue }).map((id) => shard(id, failed)));
    const tables = mergeTables(files);
    if (squads?.hasPlayingXi) {
      const teams = (meta?.teams ?? []).map((t, i) => ({ key: teamKeys[i], id: t.id, name: t.name }));
      for (const letter of surnameLettersFor(squads, tables, teams, gender)) {
        const pool = await shard(`wps-${letter}`, failed);
        if (pool?.people) tables.surnames[letter] = rosterEntries(pool.people);
      }
    }
    return { built: buildContext({ matchId, meta, squads, model, format, gender, tables, previous, now: now() }), failed };
  }

  /**
   * The context for a match, building or refreshing it when that is due and the budget allows.
   * Returns whatever is known — possibly null, possibly a context without elevens — and never
   * throws: a match must be scored whether or not its context could be built this minute.
   */
  async function ensure({ matchId, meta = null, model, format, gender, budget, live = true }) {
    const id = String(matchId);
    let context = contexts.get(id) ?? null;
    try {
      if (!context && !checkedBucket.has(id)) {
        checkedBucket.add(id);
        context = await deps.readJson(contextFileId(id)).catch(() => null);
        if (context) contexts.set(id, context);
      }

      const stale = context && (context.modelAsOf !== (model?.asOf ?? null) || context.v !== CONTEXT_VERSION);
      const wait = SQUAD_RETRY_MS * Math.min(6, 1 + (squadMisses.get(id) ?? 0));
      const wantsEleven = context && !hasElevens(context) && live && now() - (lastSquadTry.get(id) ?? 0) >= wait;
      if ((!context || stale || wantsEleven || degraded.has(id)) && budget.builds > 0 && model) {
        budget.builds -= 1;
        const page = meta ?? deps.parseMatchMeta(await deps.fetchPage(deps.matchUrl(id)));
        if (page?.teams?.length === 2) {
          const { built, failed } = await build({ matchId: id, meta: page, model, format, gender, previous: context });
          squadMisses.set(id, hasElevens(built) ? 0 : (squadMisses.get(id) ?? 0) + 1);
          if (failed.length) degraded.add(id);
          else degraded.delete(id);
          // A build on a failed read replaces nothing already held, and is never stored.
          if (failed.length && context) {
            // keep what is held; the next run tries again
          } else if (!context || comparable(built) !== comparable(context)) {
            context = built;
            contexts.set(id, context);
            if (!failed.length) await deps.writeJson(contextFileId(id), context);
            deps.log(
              `Context ${id}: built rev ${context.rev} — teams ${context.teams.map((t) => (t.known ? "known" : "unknown")).join("/")}, ` +
              `ground ${context.venue?.known ? "known" : "unknown"}, elevens ${context.teams.map((t) => `${t.joined}/${t.listed}`).join(" ")}`
            );
          }
        }
      }

      const venue = context?.venue;
      const forecastDue = venue?.lat != null && now() - (context.forecast?.fetchedAt ?? 0) >= FORECAST_TTL_MS;
      if (forecastDue && budget.forecasts > 0) {
        budget.forecasts -= 1;
        const forecast = await deps.fetchForecast({ lat: venue.lat, lon: venue.lon });
        if (forecast) {
          context = { ...context, forecast, rev: context.rev + 1 };
          contexts.set(id, context);
          if (!degraded.has(id)) await deps.writeJson(contextFileId(id), context);
        }
      }
    } catch (e) {
      if (e?.blocked) throw e;
      deps.error(`Context ${id} failed: ${e?.message}`);
    }
    return context;
  }

  /**
   * Add fields to a match's context — its preview, its summary — and store it. Returns the new
   * context, or null when the match has none to add to.
   */
  async function patch(matchId, fields) {
    const id = String(matchId);
    const current = contexts.get(id);
    if (!current) return null;
    const next = { ...current, ...fields, rev: current.rev + 1 };
    contexts.set(id, next);
    await deps.writeJson(contextFileId(id), next);
    return next;
  }

  return { ensure, patch, get: (matchId) => contexts.get(String(matchId)) ?? null, forget: (matchId) => contexts.delete(String(matchId)) };
}

/** A context with the fields that change on every build removed, for "did anything change". */
function comparable(context) {
  const { rev, builtAt, forecast, ...rest } = context;
  return JSON.stringify(rest);
}
