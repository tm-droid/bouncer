import React from "react";
import { AbsoluteFill, Audio, Easing, interpolate, staticFile, useCurrentFrame } from "remotion";
import { Phone } from "../app/Phone";
import { HomeScreen } from "../app/HomeScreen";
import { CommentaryTab, MatchDetailScreen, OverviewTab, ScorecardTab } from "../app/MatchDetailScreen";
import { AndroidHome, LiveUpdateCard } from "../app/AndroidHome";
import { AD_BG, Gold, Headline } from "../ad/Headline";
import { colors, dp, withAlpha } from "../theme/tokens";
import { states, type ChaseState } from "../data/match";

const ease = Easing.bezier(0.16, 1, 0.3, 1);
const spring = Easing.spring({ damping: 200 });
const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;

/** Frame markers inside the tour (30 fps). Scenes 2–6 of the storyboard share this one phone. */
export const T = {
  homeStart: 0,
  homeFour: 90, // the FOUR lands on Home
  detailStart: 195,
  detailScroll: 240,
  detailSix: 290, // the SIX in match detail
  commentaryStart: 375,
  commentaryWicket: 450,
  scorecardStart: 555,
  homescreenStart: 705,
  notification: 740,
  end: 825,
};

export const TOUR_DURATION = T.end;

const PHONE_SCALE = 0.44;
const PHONE_LEFT = 200;
const PHONE_TOP = 36;

const lerp = (a: number, b: number, t: number) => a + (b - a) * t;

/** Score/prob state interpolated over a "ball lands" moment. */
const blend = (a: ChaseState, b: ChaseState, t: number): ChaseState => ({
  ...(t < 0.5 ? a : b),
  winProb: lerp(a.winProb, b.winProb, t),
});

/** The newest ball chip pops in over `landAt`…`landAt+12`. */
const chipLanding = (frame: number, landAt: number) => (i: number, n: number): React.CSSProperties =>
  i === n - 1
    ? {
        scale: String(interpolate(frame, [landAt, landAt + 12], [0.3, 1], { ...clamp, easing: spring, output: "perceptual-scale" })),
        opacity: interpolate(frame, [landAt, landAt + 6], [0, 1], clamp),
      }
    : {};

const stagger = (frame: number, start: number, step = 5, dur = 18) => (i: number): React.CSSProperties => ({
  opacity: interpolate(frame, [start + i * step, start + i * step + dur], [0, 1], { ...clamp, easing: ease }),
  translate: `0 ${interpolate(frame, [start + i * step, start + i * step + dur], [dp(10), 0], { ...clamp, easing: ease })}px`,
});

export const AppTour: React.FC = () => {
  const frame = useCurrentFrame();

  /* ---- phone entrance ---- */
  const phoneX = interpolate(frame, [0, 32], [PHONE_LEFT + 260, PHONE_LEFT], { ...clamp, easing: ease });
  const phoneOpacity = interpolate(frame, [0, 14], [0, 1], clamp);

  /* ---- which screen is showing ---- */
  const inHome = frame < T.detailStart + 24;
  const inDetail = frame >= T.detailStart && frame < T.homescreenStart + 20;
  const inLauncher = frame >= T.homescreenStart;

  /* ---- Home state ---- */
  const fourT = interpolate(frame, [T.homeFour, T.homeFour + 30], [0, 1], { ...clamp, easing: ease });
  const homeState = blend(states.s0, states.s1, fourT);

  /* ---- Detail state ---- */
  const sixT = interpolate(frame, [T.detailSix, T.detailSix + 40], [0, 1], { ...clamp, easing: ease });
  const wicketT = interpolate(frame, [T.commentaryWicket, T.commentaryWicket + 30], [0, 1], { ...clamp, easing: ease });
  const detailState: ChaseState =
    frame < T.commentaryStart ? blend(states.s1, states.s2, sixT) : frame < T.commentaryWicket ? states.s3 : blend(states.s3, states.s4, wicketT);
  const detailProb = frame < T.commentaryStart ? lerp(states.s1.winProb, states.s2.winProb, sixT) : detailState.winProb;
  const swing = frame < T.commentaryStart ? lerp(0, states.s2.winProb - states.s1.winProb, sixT) : undefined;

  const flashAlpha = (at: number) => interpolate(frame, [at, at + 9, at + 45], [0, 0.15, 0], clamp);
  const flash =
    frame >= T.detailSix && frame < T.detailSix + 45
      ? { color: colors.cricket.six, alpha: flashAlpha(T.detailSix) }
      : frame >= T.commentaryWicket && frame < T.commentaryWicket + 45
        ? { color: colors.cricket.wicket, alpha: flashAlpha(T.commentaryWicket) }
        : undefined;

  /* ---- push transition Home → Detail ---- */
  const pushT = interpolate(frame, [T.detailStart, T.detailStart + 22], [0, 1], { ...clamp, easing: ease });
  const tapT = interpolate(frame, [T.detailStart - 14, T.detailStart + 4], [0, 1], clamp);

  /* ---- tabs ---- */
  const indicator =
    frame < T.commentaryStart
      ? 0
      : frame < T.scorecardStart
        ? interpolate(frame, [T.commentaryStart, T.commentaryStart + 18], [0, 2], { ...clamp, easing: ease })
        : interpolate(frame, [T.scorecardStart, T.scorecardStart + 18], [2, 1], { ...clamp, easing: ease });
  const tab = frame < T.commentaryStart ? 0 : frame < T.scorecardStart ? 2 : 1;
  const bodyFade = (start: number) => interpolate(frame, [start + 6, start + 22], [0, 1], { ...clamp, easing: ease });

  /* ---- overview scroll ---- */
  const scrollY = interpolate(frame, [T.detailScroll, T.detailScroll + 28], [0, 150], { ...clamp, easing: ease });

  /* ---- commentary wicket insert ---- */
  const wicketIn = interpolate(frame, [T.commentaryWicket, T.commentaryWicket + 20], [0, 1], { ...clamp, easing: ease });
  const hideNewest = frame < T.commentaryWicket ? 2 : 0;

  /* ---- launcher ---- */
  const launcherT = interpolate(frame, [T.homescreenStart, T.homescreenStart + 20], [0, 1], { ...clamp, easing: ease });
  const notifY = interpolate(frame, [T.notification, T.notification + 26], [-dp(520), 0], { ...clamp, easing: ease });

  return (
    <AbsoluteFill style={{ background: AD_BG }}>
      {/* faint gold vignette behind the phone */}
      <div style={{ position: "absolute", left: -200, top: -200, width: 1200, height: 1400, background: `radial-gradient(closest-side, ${withAlpha(colors.primaryText, 0.06)}, transparent)` }} />

      <Audio src={staticFile("sfx/whip.wav")} volume={0.3} startFrom={0} />
      {frame >= T.homeFour ? <Audio src={staticFile("sfx/switch.wav")} volume={0.35} /> : null}

      <div style={{ position: "absolute", left: phoneX, top: PHONE_TOP, opacity: phoneOpacity }}>
        <Phone scale={PHONE_SCALE}>
          {/* Home */}
          {inHome ? (
            <div style={{ position: "absolute", inset: 0, opacity: 1 - pushT * 0.3, scale: String(1 - pushT * 0.04) }}>
              <HomeScreen
                state={homeState}
                winProb={homeState.winProb}
                ballStyle={chipLanding(frame, T.homeFour)}
                rowStyle={stagger(frame, 14, 8, 20)}
                heroStyle={{ opacity: interpolate(frame, [6, 24], [0, 1], { ...clamp, easing: ease }) }}
              />
              {/* tap ripple on the hero */}
              {tapT > 0 && tapT < 1 ? (
                <div
                  style={{
                    position: "absolute",
                    left: 540 - 120,
                    top: 330,
                    width: 240,
                    height: 240,
                    borderRadius: "50%",
                    background: withAlpha(colors.onSurface, 0.12),
                    scale: String(0.3 + tapT * 0.9),
                    opacity: 1 - tapT,
                  }}
                />
              ) : null}
            </div>
          ) : null}

          {/* Match detail */}
          {inDetail ? (
            <div style={{ position: "absolute", inset: 0, translate: `${(1 - pushT) * 1080}px 0`, opacity: inLauncher ? 1 - launcherT : 1 }}>
              <MatchDetailScreen state={detailState} tab={tab} indicator={indicator} flash={flash} ballStyle={chipLanding(frame, frame < T.commentaryStart ? T.detailSix : T.commentaryWicket)}>
                {tab === 0 ? <OverviewTab state={detailState} homeProb={detailProb} swing={swing !== undefined && Math.abs(swing) >= 1 ? swing : undefined} scrollY={scrollY} /> : null}
                {tab === 2 ? (
                  <div style={{ opacity: bodyFade(T.commentaryStart) }}>
                    <CommentaryTab
                      hideNewest={hideNewest}
                      rowStyle={(i) =>
                        hideNewest === 0 && i < 2
                          ? { opacity: wicketIn, translate: `0 ${(1 - wicketIn) * -dp(24)}px` }
                          : {}
                      }
                    />
                  </div>
                ) : null}
                {tab === 1 ? (
                  <div style={{ opacity: bodyFade(T.scorecardStart) }}>
                    <ScorecardTab rowStyle={stagger(frame, T.scorecardStart + 12, 4, 16)} />
                  </div>
                ) : null}
              </MatchDetailScreen>
            </div>
          ) : null}

          {/* Android launcher with the widget + Live Update card */}
          {inLauncher ? (
            <div style={{ position: "absolute", inset: 0, opacity: launcherT }}>
              <AndroidHome
                state={states.s4}
                widgetStyle={{
                  scale: String(interpolate(frame, [T.homescreenStart + 8, T.homescreenStart + 30], [0.92, 1], { ...clamp, easing: spring, output: "perceptual-scale" })),
                  opacity: interpolate(frame, [T.homescreenStart + 8, T.homescreenStart + 24], [0, 1], clamp),
                  transformOrigin: "top left",
                }}
              >
                <div style={{ position: "absolute", left: dp(8), right: dp(8), top: dp(40), translate: `0 ${notifY}px` }}>
                  <LiveUpdateCard state={states.s4} />
                </div>
              </AndroidHome>
            </div>
          ) : null}
        </Phone>
      </div>

      {/* ---- right-column copy, one beat each ---- */}
      <Headline eyebrow="LIVE SCORES" title={<><Gold>Live</Gold> scores, one screen.</>} sub="Live, upcoming and recent — refreshed as the game moves. Tests, ODIs, T20s and domestic cricket." from={T.homeStart + 20} until={T.detailStart} />
      <Headline eyebrow="WIN PROBABILITY" title={<>Watch the match <Gold>turn.</Gold></>} sub="Live win probability that updates every ball, plus the swing that explains it." from={T.detailStart + 8} until={T.commentaryStart} />
      <Headline eyebrow="BALL-BY-BALL COMMENTARY" title={<>Every <Gold>delivery</Gold>, in order.</>} sub="Grouped by over, with wickets and boundaries called out as they happen." from={T.commentaryStart + 8} until={T.scorecardStart} />
      <Headline eyebrow="FULL SCORECARDS" title={<>The <Gold>numbers</Gold> that explain it.</>} sub="Batting and bowling cards, extras, fall of wickets, partnerships." from={T.scorecardStart + 8} until={T.homescreenStart} />
      <Headline eyebrow="ON YOUR HOME SCREEN" title={<>Never <Gold>miss</Gold> a ball.</>} sub="Home-screen widgets and a pinned live-score notification. Android Auto for the drive." from={T.homescreenStart + 8} until={T.end} />
    </AbsoluteFill>
  );
};
