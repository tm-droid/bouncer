// Shared pure delivery outcome parser; intentionally has no provider dependencies.
const BALL_OUTCOME = /^[^,]+,\s*([^,]+)/;
const TRAILING_PUNCTUATION = /[.!]+$/;
export function outcomeClause(text) {
  const m = String(text || "").match(BALL_OUTCOME);
  return m ? m[1].trim().replace(TRAILING_PUNCTUATION, "") : "";
}
