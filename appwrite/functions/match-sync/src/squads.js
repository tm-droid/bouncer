/**
 * Team sheets from `/cricket-match-squads/{id}`.
 *
 * The live page names only the two batters at the crease and the two bowlers in the attack; its
 * `playerDetails` arrays are empty. This page is server-rendered and its flight stream carries
 * `{team1: {team, players}, team2: {team, players}}`, where `players` is keyed by group —
 * `"playing XI"` and `"bench"` after the toss, `"Squad"` before it.
 *
 * Port of `MatchSquadsRsc` in `MatchSquadsRsc.kt`; both are pinned to the same captured pages.
 */

import { CRICBUZZ_BASE } from "./cricbuzz-http.js";
import { forEachNextFlightPayload } from "./nextflight.js";

export const squadsUrl = (matchId) => `${CRICBUZZ_BASE}/cricket-match-squads/${matchId}`;

const GROUP_XI = "playing XI";
const GROUP_BENCH = "bench";
const GROUP_SQUAD = "Squad";

const isObject = (v) => v !== null && typeof v === "object" && !Array.isArray(v);
const isSide = (o, key) => isObject(o[key]) && "team" in o[key] && "players" in o[key];

/** A flight payload string that is a back-reference (`"$12"`) is not a value. */
function str(v) {
  if (v === null || v === undefined || typeof v === "object") return "";
  const text = String(v);
  return text.trim() === "" || text.startsWith("$") ? "" : text;
}

function findHolder(node) {
  if (Array.isArray(node)) {
    for (const child of node) {
      const hit = findHolder(child);
      if (hit) return hit;
    }
    return null;
  }
  if (!isObject(node)) return null;
  if (isSide(node, "team1") && isSide(node, "team2")) return node;
  for (const key of Object.keys(node)) {
    const hit = findHolder(node[key]);
    if (hit) return hit;
  }
  return null;
}

function group(players, key) {
  const list = players[key];
  if (!Array.isArray(list)) return [];
  const out = [];
  for (const p of list) {
    if (!isObject(p)) continue;
    const name = str(p.name);
    if (!name) continue;
    out.push({ id: str(p.id), name, role: str(p.role), captain: p.captain === true, keeper: p.keeper === true });
  }
  return out;
}

function side(obj) {
  if (!isObject(obj.team) || !isObject(obj.players)) return null;
  return {
    teamId: str(obj.team.teamId),
    name: str(obj.team.teamName),
    shortName: str(obj.team.teamSName),
    playingXi: group(obj.players, GROUP_XI),
    bench: group(obj.players, GROUP_BENCH),
    squad: group(obj.players, GROUP_SQUAD),
  };
}

/** Both sides' team sheets, or null when the page carries none in the expected shape. */
export function parseSquads(html) {
  let holder = null;
  forEachNextFlightPayload(html, (payload) => {
    if (!holder) holder = findHolder(payload);
  });
  if (!holder) return null;
  const team1 = side(holder.team1);
  const team2 = side(holder.team2);
  if (!team1 || !team2) return null;
  return { team1, team2, hasPlayingXi: team1.playingXi.length > 0 && team2.playingXi.length > 0 };
}
