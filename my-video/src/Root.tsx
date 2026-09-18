import "./index.css";
import "./theme/fonts";
import React from "react";
import { AbsoluteFill, Composition, Folder, Still } from "remotion";
import { AD_DURATION, BouncerAd, CTA_DURATION, HOOK_DURATION } from "./BouncerAd";
import { Hook } from "./scenes/Hook";
import { AppTour, TOUR_DURATION } from "./scenes/AppTour";
import { Cta } from "./scenes/Cta";
import { HomeScreen } from "./app/HomeScreen";
import { CommentaryTab, MatchDetailScreen, OverviewTab, ScorecardTab } from "./app/MatchDetailScreen";
import { AndroidHome, LiveUpdateCard } from "./app/AndroidHome";
import { NewsScreen, SeriesScreen } from "./app/SeriesNewsScreens";
import { Thumbnail } from "./ad/Thumbnail";
import { Gold } from "./ad/Headline";
import { Screenshot, SHOT_H, SHOT_W } from "./ad/Screenshot";
import { states } from "./data/match";
import { dp } from "./theme/tokens";

const FPS = 30;
const W = 1920;
const H = 1080;

/** Full-screen stills at the phone's logical size: fidelity checks against emulator screencaps and Play Store screenshots. */
const StillHome: React.FC = () => (
  <AbsoluteFill>
    <HomeScreen state={states.s0} />
  </AbsoluteFill>
);
const StillOverview: React.FC = () => (
  <AbsoluteFill>
    <MatchDetailScreen state={states.s2} tab={0}>
      <OverviewTab state={states.s2} homeProb={states.s2.winProb} swing={17} scrollY={150} />
    </MatchDetailScreen>
  </AbsoluteFill>
);
const StillCommentary: React.FC = () => (
  <AbsoluteFill>
    <MatchDetailScreen state={states.s4} tab={2}>
      <CommentaryTab />
    </MatchDetailScreen>
  </AbsoluteFill>
);
const StillScorecard: React.FC = () => (
  <AbsoluteFill>
    <MatchDetailScreen state={states.s4} tab={1}>
      <ScorecardTab />
    </MatchDetailScreen>
  </AbsoluteFill>
);
const StillWidgets: React.FC = () => (
  <AbsoluteFill>
    <AndroidHome>
      <div style={{ position: "absolute", left: dp(8), right: dp(8), top: dp(40) }}>
        <LiveUpdateCard state={states.s4} />
      </div>
    </AndroidHome>
  </AbsoluteFill>
);

const StillSeries: React.FC = () => (
  <AbsoluteFill>
    <SeriesScreen />
  </AbsoluteFill>
);
const StillNews: React.FC = () => (
  <AbsoluteFill>
    <NewsScreen />
  </AbsoluteFill>
);

/**
 * Play Store phone screenshots, 1080×1920, in upload order. Each wraps the matching full-screen
 * still in the listing frame; the copy is the ad's `AppTour` headline for that beat, with the
 * shared Series/News beat split in two. Render all seven with `npm run screenshots`.
 */
const ShotHome: React.FC = () => (
  <Screenshot eyebrow="LIVE SCORES" title={<><Gold>Live</Gold> scores, one screen.</>} sub="Live, upcoming and recent — refreshed as the game moves.">
    <StillHome />
  </Screenshot>
);
const ShotOverview: React.FC = () => (
  <Screenshot eyebrow="WIN PROBABILITY" title={<>Watch the match <Gold>turn.</Gold></>} sub="Live win probability that updates every ball.">
    <StillOverview />
  </Screenshot>
);
const ShotCommentary: React.FC = () => (
  <Screenshot eyebrow="BALL-BY-BALL COMMENTARY" title={<>Every <Gold>delivery</Gold>, in order.</>} sub="Grouped by over, wickets and boundaries called out.">
    <StillCommentary />
  </Screenshot>
);
const ShotScorecard: React.FC = () => (
  <Screenshot eyebrow="FULL SCORECARDS" title={<>The <Gold>numbers</Gold> that explain it.</>} sub="Batting, bowling, extras, fall of wickets, partnerships.">
    <StillScorecard />
  </Screenshot>
);
const ShotSeries: React.FC = () => (
  <Screenshot eyebrow="SERIES" title={<>Every <Gold>series</Gold>, one place.</>} sub="Fixtures and points tables, active and archived.">
    <StillSeries />
  </Screenshot>
);
const ShotNews: React.FC = () => (
  <Screenshot eyebrow="NEWS" title={<>Every <Gold>story</Gold>, as it breaks.</>} sub="Cricket news, read inside the app.">
    <StillNews />
  </Screenshot>
);
const ShotWidgets: React.FC = () => (
  <Screenshot eyebrow="ON YOUR HOME SCREEN" title={<>Never <Gold>miss</Gold> a ball.</>} sub="A pinned live score in your notifications.">
    <StillWidgets />
  </Screenshot>
);

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition id="BouncerAd" component={BouncerAd} durationInFrames={AD_DURATION} fps={FPS} width={W} height={H} />
      <Folder name="Scenes">
        <Composition id="Hook" component={Hook} durationInFrames={HOOK_DURATION} fps={FPS} width={W} height={H} />
        <Composition id="AppTour" component={AppTour} durationInFrames={TOUR_DURATION} fps={FPS} width={W} height={H} />
        <Composition id="Cta" component={Cta} durationInFrames={CTA_DURATION} fps={FPS} width={W} height={H} />
      </Folder>
      <Still id="Thumbnail" component={Thumbnail} width={1280} height={720} />
      <Folder name="Stills">
        <Still id="Still-Home" component={StillHome} width={1080} height={2400} />
        <Still id="Still-Overview" component={StillOverview} width={1080} height={2400} />
        <Still id="Still-Commentary" component={StillCommentary} width={1080} height={2400} />
        <Still id="Still-Scorecard" component={StillScorecard} width={1080} height={2400} />
        <Still id="Still-Series" component={StillSeries} width={1080} height={2400} />
        <Still id="Still-News" component={StillNews} width={1080} height={2400} />
        <Still id="Still-Widgets" component={StillWidgets} width={1080} height={2400} />
      </Folder>
      <Folder name="Screenshots">
        <Still id="Shot-01-Home" component={ShotHome} width={SHOT_W} height={SHOT_H} />
        <Still id="Shot-02-Overview" component={ShotOverview} width={SHOT_W} height={SHOT_H} />
        <Still id="Shot-03-Commentary" component={ShotCommentary} width={SHOT_W} height={SHOT_H} />
        <Still id="Shot-04-Scorecard" component={ShotScorecard} width={SHOT_W} height={SHOT_H} />
        <Still id="Shot-05-Series" component={ShotSeries} width={SHOT_W} height={SHOT_H} />
        <Still id="Shot-06-News" component={ShotNews} width={SHOT_W} height={SHOT_H} />
        <Still id="Shot-07-Widgets" component={ShotWidgets} width={SHOT_W} height={SHOT_H} />
      </Folder>
    </>
  );
};
