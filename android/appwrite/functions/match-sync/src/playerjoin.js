/**
 * Joining a Cricbuzz player to a Cricsheet person.
 *
 * The player ratings are built from Cricsheet, which knows a player as "JJ Bumrah" under a
 * registry id; Cricbuzz knows him as "Jasprit Bumrah" under its own id. Cricsheet's register does
 * carry a Cricbuzz id column, but it is filled for 50 of 18,554 people, so the join is by name —
 * and only ever inside one team's recent roster, where a surname and an initial are nearly always
 * enough to be unique.
 *
 * An ambiguous or absent match returns null. A player joined to the wrong person carries someone
 * else's record into the model as a fact; a player not joined is simply an unknown, which the
 * model has been trained to expect.
 */

import { squash as squashName } from "./contextmath.js";

export { squashName };

const SURNAME_PARTICLES = new Set(["de", "du", "van", "der", "den", "le", "la", "ul", "al", "bin", "da"]);

/**
 * A name as surname plus either initials or given names.
 *
 * Read from the name as written, before case is folded: a token of one to four capitals ("JJ",
 * "KL", "V") is a run of initials, and that is the only thing that tells "MS Dhoni" from a player
 * whose given name happens to be two letters long.
 */
function shape(raw) {
  const tokens = String(raw ?? "").trim().split(/\s+/).filter(Boolean);
  // "de Zorzi", "van der Dussen", "du Plooy": the particles belong to the surname, or "T de Zorzi"
  // reads as a player whose second given name is "de".
  let cut = tokens.length - 1;
  while (cut > 0 && SURNAME_PARTICLES.has(tokens[cut - 1].toLowerCase())) cut--;
  const surname = squashName(tokens.slice(cut).join(" "));
  const rest = tokens.slice(0, cut);
  if (rest.length === 0) return { surname, initials: null, given: [] };
  const initialTokens = rest.filter((t) => /^[A-Z]{1,4}$/.test(t.replace(/\./g, "")));
  if (initialTokens.length === rest.length) {
    return { surname, initials: rest.join("").replace(/\./g, "").toLowerCase(), given: [] };
  }
  return { surname, initials: null, given: rest.map(squashName).filter(Boolean) };
}

/** Whether two shapes could be one person. The surname must agree; the rest must not disagree. */
function compatible(a, b) {
  if (!a.surname || a.surname !== b.surname) return false;
  if (a.initials && b.initials) return a.initials.startsWith(b.initials) || b.initials.startsWith(a.initials);
  if (a.initials || b.initials) {
    const initials = a.initials ?? b.initials;
    const given = a.initials ? b.given : a.given;
    if (given.length === 0) return false;
    if (initials[0] !== given[0][0]) return false;
    return initials.length < 2 || given.length < 2 || initials[1] === given[1][0];
  }
  return a.given.length > 0 && a.given[0] === b.given[0];
}

/**
 * The roster entry for [name], or null.
 *
 * @param roster  `[{ id, names: [names as Cricsheet writes them] }]` — one team's recent players
 */
export function resolvePlayer(roster, name) {
  const wanted = squashName(name);
  if (!wanted || !Array.isArray(roster)) return null;

  const exact = roster.filter((p) => p.names.some((n) => squashName(n) === wanted));
  if (exact.length === 1) return exact[0];
  if (exact.length > 1) return null;

  const query = shape(name);
  // Cricbuzz lists some players by surname alone ("Rayudu", "van der Dussen"). Inside one team's
  // roster a surname is usually one person; where it is two, neither is chosen.
  const loose =
    query.given.length === 0 && !query.initials
      ? roster.filter((p) => p.names.some((n) => shape(n).surname === query.surname))
      : roster.filter((p) => p.names.some((n) => compatible(query, shape(n))));
  return loose.length === 1 ? loose[0] : null;
}
