# Play Console declarations

Material for **Play Console → Monitor and improve → App content**, not for the store listing.
Store listing copy, screenshots and the promo video live in `docs/PLAYSTORELISTING.md`.

## `fgs-datasync.mp4`

The video Play requires alongside the `FOREGROUND_SERVICE_DATA_SYNC` declaration. Policy asks for
"a link to a video demonstrating each foreground service feature ... the steps the user needs to
take in your app in order to trigger the feature", so the clip is a real screen recording of the
debug build, not a rendered reconstruction.

41 s, 1080×2400, recorded on the `bouncer_test` emulator (API 36). It shows, in order:

1. Home with the live match list.
2. Tapping the bell on a live match, which is the only way to start the service.
3. Leaving the app, then the ongoing Live Update notification the service posts.
4. Tapping **Stop** on that notification, after which the card and the service are gone.

The declaration text that goes in the console with it is in `docs/PLAYSTORELISTING.md`, under
"Foreground service declaration".

### Re-recording it

The card only appears for a match that is **live at record time** and present in the cached live
list, so the clip cannot be re-made on demand — wait for play. With an emulator or device attached:

```bash
./gradlew installDebug
scripts/record-fgs-declaration.sh preflight   # measure tap targets, clear any follow
scripts/record-fgs-declaration.sh take        # record, pull to this directory
```

Then upload it somewhere Play can reach (unlisted YouTube, same channel as the promo video) and
paste the link into the declaration. Play takes a link, not a file, so the checked-in copy is the
source of truth for what was submitted.
