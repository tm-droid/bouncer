import test from "node:test";
import assert from "node:assert/strict";

import { resolvePlayer, squashName } from "../src/playerjoin.js";

// Names as Cricsheet's registry writes them, against names as Cricbuzz's team sheets write them —
// both taken from real data (IND v WI, 151543; SA v AUS, 147909).
const roster = [
  { id: "ba607b88", names: ["V Kohli", "Virat Kohli"] },
  { id: "740742ef", names: ["RG Sharma"] },
  { id: "b17e2f24", names: ["KL Rahul"] },
  { id: "fe93fd9d", names: ["RA Jadeja"] },
  { id: "5574750c", names: ["M Prasidh Krishna"] },
  { id: "a1", names: ["AS Joseph"] },
  { id: "a2", names: ["S Joseph"] },
  { id: "z1", names: ["T de Zorzi"] },
  { id: "d1", names: ["HE van der Dussen"] },
  { id: "k1", names: ["Kuldeep Yadav"] },
];
const idOf = (name) => resolvePlayer(roster, name)?.id ?? null;

test("an exact name or alias joins", () => {
  assert.equal(idOf("Virat Kohli"), "ba607b88");
  assert.equal(idOf("KL Rahul"), "b17e2f24");
  assert.equal(idOf("Kuldeep Yadav"), "k1");
});

test("a full given name joins to initials by surname and first initial", () => {
  assert.equal(idOf("Rohit Sharma"), "740742ef");
  assert.equal(idOf("Ravindra Jadeja"), "fe93fd9d");
});

test("two players sharing a surname are told apart by their initials", () => {
  assert.equal(idOf("Alzarri Joseph"), "a1");
  assert.equal(idOf("Shamar Joseph"), "a2", "S Joseph, not AS Joseph");
});

test("a surname that could be two players joins to neither", () => {
  const twins = [{ id: "x", names: ["S Joseph"] }, { id: "y", names: ["SA Joseph"] }];
  assert.equal(resolvePlayer(twins, "Shamar Joseph"), null);
  assert.equal(resolvePlayer(twins, "Joseph"), null);
});

test("surname particles stay with the surname", () => {
  assert.equal(idOf("Tony de Zorzi"), "z1");
  assert.equal(idOf("van der Dussen"), "d1", "Cricbuzz lists some players by surname alone");
});

test("a wrong initial is a different person, not a near miss", () => {
  assert.equal(idOf("Mohit Sharma"), null);
  assert.equal(idOf("Nobody Atall"), null);
  assert.equal(resolvePlayer(null, "Virat Kohli"), null);
});

test("names are compared with accents and punctuation folded", () => {
  assert.equal(squashName("  Séan  O'Brien-Smith "), "sean o brien smith");
});
