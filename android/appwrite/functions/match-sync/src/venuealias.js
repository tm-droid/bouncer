/**
 * Other names the archive may file a ground under, when Cricbuzz's name does not join.
 *
 * Cricbuzz and Cricsheet name the same ground differently ("W.A.C.A. Ground" and "WACA",
 * "Holkar Stadium" and "Holkar Cricket Stadium"). The trainer keys grounds by its own name, so a
 * serving name that misses has to be tried under the names the archive uses. Every alternate is
 * only accepted together with a matching city (see `pickGroundByCity`), so a shared name cannot
 * join two different grounds; the curated renames are the exception and follow `pickGround`.
 */

import { squash } from "./contextmath.js";

// Squashed Cricbuzz name -> squashed archive name, for renames no rule derives.
const RENAMED = {
  "bca stadium": "kotambi stadium",
  "holkar stadium": "holkar cricket stadium",
  "pca international cricket stadium": "maharaja yadavindra singh international cricket stadium",
  "al amerat cricket ground ministry turf 1": "al amerat cricket ground oman cricket ministry turf 1",
};

/** The archive's name for a ground Cricbuzz renamed, or undefined. Curated, so trusted without a city. */
export const renamedTo = (name) => RENAMED[squash(String(name ?? "").split(",")[0])];

/** Alternate squashed names for [name], not including the plain one `venueName` gives. */
export function venueAliases(name) {
  const first = String(name ?? "").split(",")[0];
  // "W.A.C.A. Ground" -> "WACA Ground": a full stop after a single letter is an initial's.
  const undotted = squash(first.replace(/(?<![A-Za-z])([A-Za-z])\./g, "$1"));
  const out = new Set([undotted, undotted.replace(/ (ground|stadium)$/, ""), RENAMED[squash(first)]]);
  out.delete(squash(first));
  out.delete(undefined);
  out.delete("");
  return [...out];
}
