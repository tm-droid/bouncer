import React from "react";
import { linearTiming, TransitionSeries } from "@remotion/transitions";
import { fade } from "@remotion/transitions/fade";
import { Hook } from "./scenes/Hook";
import { AppTour, TOUR_DURATION } from "./scenes/AppTour";
import { Cta } from "./scenes/Cta";

export const HOOK_DURATION = 105;
export const CTA_DURATION = 100;
export const FADE = 14;

/** Total frames of the assembled ad (each transition overlaps two scenes). */
export const AD_DURATION = HOOK_DURATION + TOUR_DURATION + CTA_DURATION - FADE * 2;

export const BouncerAd: React.FC = () => (
  <TransitionSeries>
    <TransitionSeries.Sequence durationInFrames={HOOK_DURATION} name="Hook">
      <Hook />
    </TransitionSeries.Sequence>
    <TransitionSeries.Transition presentation={fade()} timing={linearTiming({ durationInFrames: FADE })} />
    <TransitionSeries.Sequence durationInFrames={TOUR_DURATION} name="AppTour">
      <AppTour />
    </TransitionSeries.Sequence>
    <TransitionSeries.Transition presentation={fade()} timing={linearTiming({ durationInFrames: FADE })} />
    <TransitionSeries.Sequence durationInFrames={CTA_DURATION} name="CTA">
      <Cta />
    </TransitionSeries.Sequence>
  </TransitionSeries>
);
