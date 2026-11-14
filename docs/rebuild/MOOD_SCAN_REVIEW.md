# The mood scan: a review (decision D14)

The owner asked (14 November 2026) whether the way each song's mood is worked out can be made more accurate, or is
best left alone. This is what was measured, what was proposed, and (further down) what was then changed.

## How it was measured

The rules as they are (`MoodRules.judge`, from the installed 3.2.28) were run over the owner's own scan results
(about 3,300 songs, every one heard, nearly all with their words read), read-only, outside the app. Three things were
looked at: how the moods spread over the library; how well the sound's numbers alone tell moods apart, checked against
two things that don't come from the sound (people's Last.fm tags on the song, and the mood the words were read as); and
how the result compares with the 17 songs whose moods the owner set by hand. The harness has no file genre and no
learnt nudges, so it gives the same main mood as the app for about 70% of songs: close, not exact.

## What it shows

1. **Almost every song gets five moods, the most allowed.** 96% of songs. A song in five of twelve moods says little
   about any of them.
2. **Three moods are on nearly everything.** Energetic is on 96% of songs, Intense and Hype on 87% each. Calm is on
   2%, Chill on 6%, Focus on under 1%. This is why "chill" and "calm" sets have so little to choose from, and why
   high-energy moods turn up on songs that aren't.
3. **The cause is the scale the sound is measured on.** The rules treat -10 dB as "as loud as it gets". 79% of this
   library is louder than that (modern records are), so the loudness cue is at its top for four songs in five and
   tells them apart no more. The "busy" cue (notes a second) is the same: its scale ends at 5, and the library's
   middle is 4.5. Energetic, Hype and Intense all lean on those two cues.
4. **The sound alone is a weak judge of mood.** On a scale where 0.5 is a coin toss and 1 is perfect, the sound scores
   0.5 to 0.6 for most moods against either check. It is fair for Intense (0.8), Party and Hype (0.65). For Happy, Sad
   and Dark, the major-or-minor-key cue is no better than chance. The words and people's tags carry the real signal.
5. **Against the owner's 17 hand-set songs the rules do well**: 84% of the owner's moods are found, and 78% of the
   moods given are ones the owner chose. But 17 songs is too few to tune anything by.

## What was tried

Putting the loudness, brightness, busyness and beat cues on the scale this library really has (its 5th to 95th
percentile, in place of the fixed scale):

| | As it is | Rescaled | Rescaled, three moods at most |
|---|---|---|---|
| Songs with five moods | 96% | 92% | none (96% have three) |
| Energetic / Hype / Intense on | 98 / 91 / 90% | 73 / 54 / 70% | 48 / 32 / 55% |
| Chill / Calm / Focus on | 5 / 2 / 0% | 24 / 15 / 17% | 11 / 7 / 3% |
| Sound alone against the words (Energetic, Hype, Chill, Calm) | 0.55, 0.62, 0.52, 0.58 | 0.60, 0.69, 0.59, 0.65 | the same |
| Owner's moods found (17 songs) | 84% | 79% | 60% |
| Moods given that are the owner's | 78% | 72% | 86% |

Rescaling spreads the moods far better and makes the sound a somewhat better judge. On the owner's 17 songs it is
slightly worse, and capping at three moods trades finding fewer of the owner's moods for giving fewer wrong ones. With
17 songs neither difference means much.

## What was changed (14 November 2026, on the owner's word)

The owner said to make the changes, and that the moods already in the files may be lost (Reset moods writes the new
ones). Each change was tried by itself first, against the owner's 17 hand-set songs and against the 75 songs that
people gave a mood word on Last.fm ("chill", "sad", "happy"...), those tags kept out of what the rules were shown.

| Change | Kept? | Why |
|---|---|---|
| The sound read on the scale records really have (loud -14 to -5 dB, bright 1,300 to 2,650, busy 3.4 to 6 notes a second, beat 0.2 to 0.68) | **Yes** | Energetic on 98% of songs became 55%; Calm 2% became 9%, Chill 5% became 13%. The song's main mood matched what people called it for 36% of songs, from 28% |
| The words counted for more (1.7, from 1.3) | **Yes** | The main mood matched the owner's own for 17 of 17 songs (16 before), and more of the owner's moods were found |
| Fewer moods: four at most, each at least 0.40 and 70% of the top one | **Yes** | 3.5 moods a song, from 4.9. Of the moods given, 85% are ones the owner chose (78% before); the price is that 68% of the owner's moods are found (84% before) |
| The major-or-minor key counted for less in Happy, Sad and Dark | **No** | Tried at 0.4 of its weight: no better against people's tags, slightly worse against the owner's songs. Left as it was |
| Scales taken from each library's own songs | **No** | The phone and the PC would then give the same song different moods, and a song's mood would shift as the collection grew. One fixed scale, set from this collection, instead |

The checks are small (17 and 75 songs), so these numbers say "better", not "how much better". Fixing a song's moods
in the Tagger still teaches the app, as before.

**To get the new moods into the files:** Reset moods, in the app, once this is in a build.

## What was proposed before the changes

1. **Don't change the rules yet.** A change rewrites the mood tag in every one of the owner's files at the next scan;
   that needs better proof than this.
2. **Get proof: about 100 songs with moods set by the owner** (the Tagger already records them, and they also teach
   the learnt nudges). With those, every change below can be scored honestly, and kept only if it scores better.
3. **Then, the changes worth testing against them:**
   - the cue scales taken from the library itself (percentiles), so they fit any collection, quiet or loud;
   - fewer moods per song: a higher bar for a heard song's extra moods (0.26 today), or three at most;
   - the major-or-minor cue counted for less in Happy, Sad and Dark, where it is no better than chance;
   - the words' reading counted for more where the sound says little.
4. **Whatever is changed keeps an undo**: the moods as they were are kept (`MoodBackup` does this today for a sync).

## What needs the owner

Setting moods by hand on about 100 songs, spread over quiet and loud ones, in the Tagger. Nothing else.
