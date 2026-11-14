# Measuring the mood rules against a real collection

What `docs/rebuild/MOOD_SCAN_REVIEW.md` was measured with. Nothing here is part of the app.

- `export.ps1`: reads the installed app's `moods.json` (read-only) and writes `songs.tsv` beside itself. That file
  holds the names of the owner's songs: it is ignored by git and must never be committed.
- `Eval2.java`: the mood rules ported so each change can be tried, checked first against the app's own rules (from
  the installed app's `shared` jar), then scored against the songs whose moods were set by hand and the songs people
  gave a mood word on Last.fm.

To run (a JDK 21, and Radon Player installed):

    powershell -File export.ps1
    java -cp "<app>\shared-*.jar;<app>\kotlin-stdlib-2*.jar" Eval2.java songs.tsv "scale=0" "scale=1,lyr=1.3,fits=0.40,near=0.70,most=4"

`<app>` is `C:\Program Files\Radon Player\app`. Each quoted argument is one set of rules to try: `scale=0` is the
rules as they were in 3.2.28, the second is what was adopted in November 2026. The port follows the 3.2.28 rules; if
`MoodRules.kt` changes shape, bring the port up to date before trusting its numbers.
