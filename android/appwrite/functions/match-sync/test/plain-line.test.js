import test from "node:test";
import assert from "node:assert/strict";

import { TEMPLATE_PROVIDER, commentaryOrigin, plainLine } from "../src/main.js";

// Sources as Cricbuzz published them on 2026-10-03 (matches 173107, 174881, 163077, 171070).

test("a source that is the scorer's header alone gets a plain line, in code", () => {
  assert.equal(plainLine("Chinelle Henry to Mupachikwa, no run", 0), "Mupachikwa does not score off Chinelle Henry.");
  assert.equal(plainLine("Chinelle Henry to Mupachikwa, 1 run", 0), "Mupachikwa takes a single off Chinelle Henry.");
  assert.equal(plainLine("Chinelle Henry to Mupachikwa, 2 runs", 0), "Mupachikwa takes 2 off Chinelle Henry.");
  assert.equal(plainLine("Verra Farane to Nurin Imanina, FOUR", 0), "Four to Nurin Imanina off Verra Farane.");
  assert.equal(plainLine("Olive Lefaga to Nurin Imanina, leg byes, 1 run", 0), "A leg bye off Olive Lefaga's delivery to Nurin Imanina.");
  assert.equal(plainLine("Nur Arianna Natsya to Seuga Lipine, no ball", 0), "No-ball from Nur Arianna Natsya to Seuga Lipine.");
  assert.equal(plainLine("Axar Patel to Abdul Samad, 5 wides", 0), "Axar Patel bowls a wide to Abdul Samad, 5 runs in all.");
});

test("a plain line adds no word the source did not give it", () => {
  const allowed = new Set(
    ("does not score off dot ball from to no run for takes a single runs four finds the boundary six clears " +
      "bowls wide leg bye byes delivery in all s").split(" ")
  );
  const sources = [
    "Chinelle Henry to Mupachikwa, no run", "Chinelle Henry to Mupachikwa, 1 run", "Chinelle Henry to Mayers, 3 runs",
    "Verra Farane to Nurin Imanina, FOUR", "Regina Lili'i to Mas Elysa, SIX", "Olive Lefaga to Nurin Imanina, wide",
    "Olive Lefaga to Nurin Imanina, no ball, 2 runs", "Olive Lefaga to Nurin Imanina, byes, 4 runs",
  ];
  for (const source of sources) {
    const names = new Set(source.split(",")[0].toLowerCase().split(/[^a-z]+/));
    for (let seed = 0; seed < 3; seed++) {
      const line = plainLine(source, seed);
      assert.ok(line, source);
      for (const word of line.toLowerCase().split(/[^a-z]+/).filter(Boolean)) {
        assert.ok(allowed.has(word) || names.has(word), `"${word}" in "${line}"`);
      }
    }
  }
});

test("the same delivery always gets the same wording", () => {
  const source = "Chinelle Henry to Mupachikwa, no run";
  assert.equal(plainLine(source, 1791000936266), plainLine(source, 1791000936266));
});

test("a described delivery and a dismissal go to the writer", () => {
  assert.equal(plainLine("Nasir Lone to Smaran Ravichandran, no run, pitched up on off, Smaran toe-ends the drive"), null);
  assert.equal(plainLine("Jasprit Bumrah to Maaz Sadaqat, FOUR, to deep extra cover"), null);
  assert.equal(plainLine("Norah Salima to Nur Aliya Hairun, out Caught by Verra Farane!! Nur Aliya Hairun c Verra Farane b Norah Salima 4(9)"), null);
  assert.equal(plainLine("Drinks are on the field"), null);
  assert.equal(plainLine(""), null);
});

test("origin: a plain line is neither a model's nor raw Cricbuzz text", () => {
  assert.deepEqual(commentaryOrigin({ line: "0.1: x", provider: TEMPLATE_PROVIDER }), { aiGenerated: false, provider: "template" });
});
