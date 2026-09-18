import { loadFont } from "@remotion/fonts";
import { staticFile } from "remotion";

export const FONT_FAMILY = "IBM Plex Sans";

/** The app bundles four IBM Plex Sans weights (res/font); ExtraBold resolves to Bold 700 there too. */
export const fontsReady = Promise.all([
  loadFont({ family: FONT_FAMILY, url: staticFile("fonts/ibm_plex_sans_regular.ttf"), weight: "400" }),
  loadFont({ family: FONT_FAMILY, url: staticFile("fonts/ibm_plex_sans_medium.ttf"), weight: "500" }),
  loadFont({ family: FONT_FAMILY, url: staticFile("fonts/ibm_plex_sans_semibold.ttf"), weight: "600" }),
  loadFont({ family: FONT_FAMILY, url: staticFile("fonts/ibm_plex_sans_bold.ttf"), weight: "700" }),
]);
