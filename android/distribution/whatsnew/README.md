# Play Store release notes

`whatsnew/whatsnew-en-US` is what Google Play shows as "What's new" for the release being
uploaded. The CI `publish` job passes this directory to `r0adkll/upload-google-play` as
`whatsNewDirectory`, so whatever is in the file at the moment of the push is what ships.

Play truncates at **500 characters** per locale. Write for someone who uses the app, not for
someone who reads the diff: name the thing that is different on screen, not the commit that
changed it.

## When you bump `versionName`

1. Move the current `whatsnew-en-US` to `history/<the version you are leaving>.txt`.
2. Write the new release's notes into `whatsnew-en-US`.
3. Bump `versionName` in `app/build.gradle.kts` in the same commit.

`versionCode` is **not** hand-edited — CI derives it from the workflow run number
(`BOUNCER_VERSION_CODE`), so it is monotonic without anyone remembering to bump it.

## history/

One file per shipped `versionName`. The files for 1.0.1–1.0.7 were reconstructed after the
fact from the commit ranges between the `build: bump versionCode …` commits, because the repo
carried no changelog and no tags at the time. They are a record, not something Play reads.
