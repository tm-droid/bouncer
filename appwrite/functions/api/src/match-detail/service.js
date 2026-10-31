// Only this response cache is written here. Canonical rows remain owned by match-sync.
const SECTIONS = ['summary', 'detail', 'scorecard', 'meta', 'commentary'];
export const validMatchId = id => typeof id === 'string' && /^\d{1,12}$/.test(id);
export const ttlFor = (state, finalized = false) => /complete|result|terminal/i.test(state ?? '')
  ? (finalized ? 86_400_000 : 600_000) : /preview|upcoming|scheduled|pre.match/i.test(state ?? '') ? 600_000 : 60_000;
const usable = (key, value) => key === 'commentary' ? Array.isArray(value) && value.length > 0
  : key === 'scorecard' ? value?.innings?.length > 0
  : key === 'detail' ? !!(value?.status || value?.score || value?.team1Score || value?.batsmen?.length)
  : key === 'meta' ? !!(value?.venue || value?.teams?.length || value?.startMs || value?.toss) : !!value;

export function envelope(id, cached, now) {
  const ttl = ttlFor(cached.state, cached.finalized);
  const availability = Object.fromEntries(SECTIONS.map(key => {
    const available = usable(key, cached[key]);
    const fetchedAt = available ? cached.times?.[key] ?? null : null;
    return [key, { available, fetchedAt, stale: available && (fetchedAt == null || now - fetchedAt >= ttl) }];
  }));
  return { schemaVersion: 1, matchId: id, state: cached.state ?? null,
    ...Object.fromEntries(SECTIONS.map(k => [k, usable(k, cached[k]) ? cached[k] : null])),
    fetchedAt: cached.fetchedAt ?? null,
    stale: Object.values(availability).some(v => v.stale),
    retryAfterSeconds: Math.max(0, Math.ceil(((cached.retryAt ?? 0) - now) / 1000)), availability };
}

export function createDetailService(deps, { now = Date.now, capacity = 100, attemptLimit = 40 } = {}) {
  const memo = new Map(), flights = new Map();
  let attempts = [], lists = null;
  function remember(id, entry) {
    memo.delete(id); memo.set(id, entry);
    while (memo.size > capacity) memo.delete(memo.keys().next().value);
  }
  function reply(id, entry) {
    const body = envelope(id, entry, now());
    return { status: SECTIONS.some(k => body.availability[k].available) ? 200 : 503, body };
  }
  async function load(id) {
    const deadlineAt = now() + 10_500;
    let cached = memo.get(id) ?? await deps.readCache(id, deadlineAt).catch(() => null);
    cached ??= { times: {} };
    if (cached.expiresAt > now() || cached.retryAt > now()) { remember(id, cached); return reply(id, cached); }
    // Download all current lists; failed lookup is never proof that an ID is unknown.
    try {
      if (!lists || now() - lists.at >= 30_000) lists = { entries: await deps.readLists(deadlineAt), at: now() };
    } catch {
      return { ...reply(id, { ...cached, retryAt: now() + 90_000 }), status: cached.summary || cached.detail || cached.scorecard ? 200 : 503 };
    }
    const member = lists.entries.find(m => String(m.summary.id) === id);
    if (!member) {
      if (SECTIONS.some(k => usable(k, cached[k]))) return reply(id, cached);
      return { status: 404, body: { error: 'unknown_match', matchId: id } };
    }
    cached = { ...cached, times: { ...cached.times }, summary: member.summary, state: member.state === 'Complete' ? 'Complete' : member.summary.state || member.state };
    cached.times.summary = member.fetchedAt;
    const seeds = await deps.readSeeds(id, deadlineAt).catch(() => ({}));
    for (const key of ['detail', 'scorecard']) if (usable(key, seeds[key]?.value) && (!usable(key, cached[key]) || seeds[key].at > (cached.times[key] ?? 0))) {
      cached[key] = seeds[key].value; cached.times[key] = seeds[key].at;
    }
    attempts = attempts.filter(at => now() - at < 600_000);
    if (attempts.length >= attemptLimit) return reply(id, { ...cached, retryAt: attempts[0] + 600_000 });
    // Claim synchronously before awaiting any upstream work. Warm container bound only.
    attempts.push(now());
    let sections = {};
    try { sections = await deps.fetchSections(id, deadlineAt); } catch { /* preserve last good sections */ }
    let refreshed = false;
    for (const key of ['detail', 'scorecard', 'meta', 'commentary']) if (usable(key, sections[key])) {
      cached[key] = ['detail', 'scorecard'].includes(key) ? { ...cached[key], ...sections[key] } : sections[key]; cached.times[key] = now(); refreshed = true;
    }
    // Finalized archive evidence is needed for the 24-hour TTL; failure keeps ten minutes.
    if (/complete|result|terminal/i.test(cached.state ?? '')) cached.finalized = await deps.isFinalized(id, deadlineAt).catch(() => false);
    const complete = ['detail', 'scorecard'].every(k => usable(k, sections[k]));
    cached.fetchedAt = refreshed ? now() : cached.fetchedAt ?? null;
    cached.expiresAt = now() + (complete ? ttlFor(cached.state, cached.finalized) : 90_000);
    cached.retryAt = complete ? 0 : now() + 90_000;
    remember(id, cached);
    await deps.writeCache(id, cached, deadlineAt).catch(() => {});
    return reply(id, cached);
  }
  return async id => {
    if (!validMatchId(id)) return { status: 400, body: { error: 'invalid_match_id' } };
    if (flights.has(id)) return flights.get(id);
    if (flights.size >= attemptLimit) return { status: 503, body: { error: 'busy', retryAfterSeconds: 90 } };
    const work = load(id).finally(() => flights.delete(id));
    flights.set(id, work);
    return work;
  };
}
