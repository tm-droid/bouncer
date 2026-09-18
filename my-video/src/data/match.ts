/**
 * One fixture, read by every scene, so the figures agree across Home, match detail, the widget and
 * the Live Update card. India chase 187 against Australia, 3rd T20I.
 *
 * The chase moves through five states as the ad plays; each scene picks the state for its moment.
 */

export type Ball = string; // Cricbuzz notation: "0" "1" "4" "6" "W" "Wd" "Nb"

export type ChaseState = {
  /** India's score line, e.g. "146/4" */
  score: string;
  overs: string;
  wickets: number;
  runs: number;
  need: number;
  ballsLeft: number;
  crr: string;
  rrr: string;
  /** India's win probability, whole percent */
  winProb: number;
  /** last 12 balls, oldest first */
  recentBalls: Ball[];
  striker: { name: string; runs: number; balls: number; fours: number; sixes: number };
  nonStriker: { name: string; runs: number; balls: number; fours: number; sixes: number };
  bowler: { name: string; overs: string; maidens: number; runs: number; wickets: number };
  status: string;
};

const base = {
  striker: { name: "Suryakumar Yadav", runs: 48, balls: 29, fours: 5, sixes: 2 },
  nonStriker: { name: "Hardik Pandya", runs: 21, balls: 14, fours: 2, sixes: 1 },
  bowler: { name: "Mitchell Starc", overs: "3.2", maidens: 0, runs: 27, wickets: 1 },
};

export const states = {
  /** 16.2 ov — the ad opens here */
  s0: {
    score: "142/4",
    overs: "16.2",
    wickets: 4,
    runs: 142,
    need: 45,
    ballsLeft: 22,
    crr: "8.69",
    rrr: "12.27",
    winProb: 46,
    recentBalls: ["W", "1", "2", "1", "1", "0", "4", "Wd", "1", "0", "1", "0"],
    ...base,
    status: "India need 45 runs in 22 balls",
  },
  /** 16.3 — FOUR */
  s1: {
    score: "146/4",
    overs: "16.3",
    wickets: 4,
    runs: 146,
    need: 41,
    ballsLeft: 21,
    crr: "8.85",
    rrr: "11.71",
    winProb: 52,
    recentBalls: ["1", "2", "1", "1", "0", "4", "Wd", "1", "0", "1", "0", "4"],
    ...base,
    striker: { ...base.striker, runs: 52, balls: 30, fours: 6 },
    bowler: { ...base.bowler, overs: "3.3", runs: 31 },
    status: "India need 41 runs in 21 balls",
  },
  /** 16.4 — SIX */
  s2: {
    score: "152/4",
    overs: "16.4",
    wickets: 4,
    runs: 152,
    need: 35,
    ballsLeft: 20,
    crr: "9.12",
    rrr: "10.50",
    winProb: 63,
    recentBalls: ["2", "1", "1", "0", "4", "Wd", "1", "0", "1", "0", "4", "6"],
    ...base,
    striker: { ...base.striker, runs: 58, balls: 31, fours: 6, sixes: 3 },
    bowler: { ...base.bowler, overs: "3.4", runs: 37 },
    status: "India need 35 runs in 20 balls",
  },
  /** 17.0 — end of Starc's over (1, 0) */
  s3: {
    score: "153/4",
    overs: "17",
    wickets: 4,
    runs: 153,
    need: 34,
    ballsLeft: 18,
    crr: "9.00",
    rrr: "11.33",
    winProb: 60,
    recentBalls: ["1", "0", "4", "Wd", "1", "0", "1", "0", "4", "6", "1", "0"],
    ...base,
    striker: { ...base.striker, runs: 59, balls: 32, fours: 6, sixes: 3 },
    nonStriker: { ...base.nonStriker, balls: 15 },
    bowler: { name: "Mitchell Starc", overs: "4", maidens: 0, runs: 38, wickets: 1 },
    status: "India need 34 runs in 18 balls",
  },
  /** 17.1 — WICKET, Hardik c Maxwell b Hazlewood */
  s4: {
    score: "153/5",
    overs: "17.1",
    wickets: 5,
    runs: 153,
    need: 34,
    ballsLeft: 17,
    crr: "8.92",
    rrr: "12.00",
    winProb: 49,
    recentBalls: ["0", "4", "Wd", "1", "0", "1", "0", "4", "6", "1", "0", "W"],
    striker: { name: "Axar Patel", runs: 0, balls: 0, fours: 0, sixes: 0 },
    nonStriker: { name: "Suryakumar Yadav", runs: 59, balls: 32, fours: 6, sixes: 3 },
    bowler: { name: "Josh Hazlewood", overs: "3.1", maidens: 0, runs: 24, wickets: 2 },
    status: "India need 34 runs in 17 balls",
  },
} satisfies Record<string, ChaseState>;

export const match = {
  seriesName: "Australia tour of India, 2026",
  matchDesc: "3rd T20I",
  matchType: "T20I",
  /** matchHeaderText(): matchDesc already contains matchType, so it is "3rd T20I · series" */
  headerText: "3rd T20I · Australia tour of India, 2026",
  title: "India vs Australia",
  venue: "Wankhede Stadium, Mumbai",
  home: { acronym: "IND", name: "India", crest: "crests/ind.png" },
  away: { acronym: "AUS", name: "Australia", crest: "crests/aus.png" },
  awayScore: "186/7",
  awayOvers: "20",
  target: 187,
  lastWicket: "Tilak Varma c Head b Zampa 25 (19) — 118/4 (14.3 ov)",
  /** win-probability history for India, one point per over-ish, for the swing series */
  winProbHistory: [50, 47, 44, 46, 41, 45, 47, 43, 40, 44, 46, 48, 45, 42, 38, 42, 46],
};

/** Other rows on Home beneath the hero: one live, one upcoming, one result. */
export const otherMatches = [
  {
    headerText: "2nd Test · England tour of Pakistan, 2026",
    live: true,
    teams: [
      { acronym: "PAK", crest: "crests/pak.png", score: "312 & 86/2", overs: "24.5", batting: true },
      { acronym: "ENG", crest: "crests/eng.png", score: "294", overs: "" , batting: false },
    ],
    status: "Day 3: 2nd Session - Pakistan lead by 104 runs",
  },
  {
    headerText: "1st ODI · New Zealand tour of South Africa, 2026",
    live: false,
    teams: [
      { acronym: "RSA", crest: "crests/sa.png", score: "", overs: "", batting: false },
      { acronym: "NZ", crest: "crests/nz.png", score: "", overs: "", batting: false },
    ],
    status: "Starts tomorrow, 1:30 pm",
  },
  {
    headerText: "Final · ICC Women's T20 World Cup 2026",
    live: false,
    teams: [
      { acronym: "AUSW", crest: "crests/aus.png", score: "164/6", overs: "20", batting: false },
      { acronym: "ENGW", crest: "crests/eng.png", score: "151/9", overs: "20", batting: false },
    ],
    status: "Australia Women won by 13 runs",
  },
];

export type CommentaryRow =
  | { kind: "over"; over: number; runs: number; balls: Ball[] }
  | { kind: "ball"; label: string; result: Ball; text: string };

/** Commentary feed, newest first, for Innings 2 — as CommentaryTabV2 renders it. */
export const commentary: CommentaryRow[] = [
  {
    kind: "ball",
    label: "17.1",
    result: "W",
    text:
      "Hazlewood to Hardik Pandya, <b>out Caught by Maxwell!!</b> Slower ball, wide of off, Hardik goes for the big hit over cover and slices it high. Maxwell settles under it at deep cover. Hardik Pandya c Maxwell b Hazlewood 21(16)",
  },
  { kind: "over", over: 17, runs: 12, balls: ["1", "0", "4", "6", "1", "0"] },
  {
    kind: "ball",
    label: "16.6",
    result: "0",
    text: "Starc to Hardik Pandya, <b>no run</b>, full and straight, dug out to mid-on",
  },
  {
    kind: "ball",
    label: "16.5",
    result: "1",
    text: "Starc to Suryakumar Yadav, <b>1 run</b>, low full toss, worked away to deep square leg",
  },
  {
    kind: "ball",
    label: "16.4",
    result: "6",
    text:
      "Starc to Suryakumar Yadav, <b>SIX</b>, that is outrageous! Full on the stumps, gets low and scoops it over fine leg. The crowd is on its feet",
  },
  {
    kind: "ball",
    label: "16.3",
    result: "4",
    text: "Starc to Suryakumar Yadav, <b>FOUR</b>, short and wide, slashed hard over point",
  },
  {
    kind: "ball",
    label: "16.2",
    result: "0",
    text: "Starc to Suryakumar Yadav, <b>no run</b>, yorker length, jammed out to cover",
  },
  {
    kind: "ball",
    label: "16.1",
    result: "1",
    text: "Starc to Hardik Pandya, <b>1 run</b>, pushed down to long-off",
  },
  { kind: "over", over: 16, runs: 8, balls: ["1", "1", "0", "4", "Wd", "1", "0"] },
  {
    kind: "ball",
    label: "15.6",
    result: "0",
    text: "Cummins to Hardik Pandya, <b>no run</b>, back of a length, defended",
  },
  {
    kind: "ball",
    label: "15.5",
    result: "1",
    text: "Cummins to Suryakumar Yadav, <b>1 run</b>, dropped short, pulled to deep midwicket",
  },
];

export const scorecard = {
  /** India 153/5 (17.1) — the state at the end of the ad's commentary beat */
  summary: "India 153/5 (17.1)",
  rates: "CRR: 8.92 • REQ: 12.00",
  batting: [
    { name: "Abhishek Sharma", how: "c Inglis b Starc", r: 12, b: 8, fours: 2, sixes: 0, sr: "150.00", out: true },
    { name: "Sanju Samson", how: "b Hazlewood", r: 24, b: 18, fours: 3, sixes: 1, sr: "133.33", out: true },
    { name: "Ishan Kishan", how: "c Zampa b Maxwell", r: 9, b: 8, fours: 1, sixes: 0, sr: "112.50", out: true },
    { name: "Suryakumar Yadav", how: "", r: 59, b: 32, fours: 6, sixes: 3, sr: "184.38", out: false },
    { name: "Tilak Varma", how: "c Head b Zampa", r: 25, b: 19, fours: 2, sixes: 1, sr: "131.58", out: true },
    { name: "Hardik Pandya", how: "c Maxwell b Hazlewood", r: 21, b: 16, fours: 2, sixes: 1, sr: "131.25", out: true },
    { name: "Axar Patel", how: "", r: 0, b: 0, fours: 0, sixes: 0, sr: "0.00", out: false },
  ],
  extras: "Extras: 3 (wd 2, lb 1)",
  crr: "CRR 8.92",
  fow: [
    { score: "1-24", who: "Abhishek Sharma, 2.4" },
    { score: "2-58", who: "Sanju Samson, 7.1" },
    { score: "3-79", who: "Ishan Kishan, 9.5" },
    { score: "4-118", who: "Tilak Varma, 14.3" },
    { score: "5-153", who: "Hardik Pandya, 17.1" },
  ],
  bowling: [
    { name: "Mitchell Starc", o: "4", m: 0, r: 38, w: 1, econ: "9.50" },
    { name: "Josh Hazlewood", o: "3.1", m: 0, r: 24, w: 2, econ: "7.58" },
    { name: "Pat Cummins", o: "4", m: 0, r: 37, w: 0, econ: "9.25" },
    { name: "Adam Zampa", o: "4", m: 0, r: 32, w: 1, econ: "8.00" },
    { name: "Glenn Maxwell", o: "2", m: 0, r: 21, w: 1, econ: "10.50" },
  ],
};
