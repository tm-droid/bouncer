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
    <AndroidHome state={states.s4}>
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

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition id="BouncerAd" component={BouncerAd} durationInFrames={AD_DURATION} fps={FPS} width={W} height={H} />
      <Folder name="Scenes">
        <Composition id="Hook" component={Hook} durationInFrames={HOOK_DURATION} fps={FPS} width={W} height={H} />
        <Composition id="AppTour" component={AppTour} durationInFrames={TOUR_DURATION} fps={FPS} width={W} height={H} />
        <Composition id="Cta" component={Cta} durationInFrames={CTA_DURATION} fps={FPS} width={W} height={H} />
      </Folder>
      <Folder name="Stills">
        <Still id="Still-Home" component={StillHome} width={1080} height={2400} />
        <Still id="Still-Overview" component={StillOverview} width={1080} height={2400} />
        <Still id="Still-Commentary" component={StillCommentary} width={1080} height={2400} />
        <Still id="Still-Scorecard" component={StillScorecard} width={1080} height={2400} />
        <Still id="Still-Series" component={StillSeries} width={1080} height={2400} />
        <Still id="Still-News" component={StillNews} width={1080} height={2400} />
        <Still id="Still-Widgets" component={StillWidgets} width={1080} height={2400} />
      </Folder>
    </>
  );
};
