# Appendix A: every spoken DJ line in `android/core/common/org/omarchy/music/Dj.kt` (3.2.28)

Generated mechanically from the source: every string literal of three or more words on a non-comment line between lines 124 and 1690, plus `DjVoices.SAMPLE`. `${...}` are Kotlin templates. Flags: SELF = names itself ("your DJ"), BANNED = a forbidden phrase, DATA = sounds like reading out data, CHEESE = matched the cheesy-phrase list, EXCL = exclamation mark only. Disposition: **R** rewrite (SELF, BANNED, DATA or CHEESE), **E** light edit (EXCL only: "!" becomes "." or "..."), **K** keep.

| Dj.kt line | Bag key / context | Line | Flags | Disp. |
|---|---|---|---|---|
| 125 | greeting | Hey, night owl |  | K |
| 136 | hello-friday | Happy Friday night! It's your DJ, and the weekend starts right now. | SELF | R |
| 137 | hello-friday | It's Friday night, and your DJ is here. Let's make it a good one. | SELF | R |
| 138 | hello-friday | Friday night, finally. Your DJ here, with the music to match. | SELF | R |
| 140 | hello-weekend-morning | Happy ${if (d == java.time.DayOfWeek.SATURDAY)  |  | K |
| 140 | hello-weekend-morning |  else  |  | K |
| 140 | hello-weekend-morning | }! It's your DJ. No alarms, just music. | SELF CHEESE | R |
| 141 | hello-weekend-morning | Weekend morning, no rush. Your DJ here, taking it easy with you. | SELF | R |
| 142 | hello-weekend-morning | Good morning, and happy weekend. It's your DJ. | SELF | R |
| 144 | hello-saturday-night | Saturday night! Your DJ is here and ready. | SELF | R |
| 145 | hello-saturday-night | It's Saturday night. Your DJ here. Let's turn it up. | SELF | R |
| 147 | hello-sunday-night | Sunday evening, the calm before the week. Your DJ here. | SELF | R |
| 148 | hello-sunday-night | Hey, it's Sunday night. Your DJ, here to stretch the weekend out a little longer. | SELF | R |
| 150 | hello-monday | Good morning, and happy Monday. It's your DJ. We've got this. | SELF CHEESE | R |
| 151 | hello-monday | Monday morning. Your DJ here, to make it a little easier. | SELF | R |
| 158 | hello-morning | Good morning! It's your DJ, and I've got your soundtrack ready. | SELF | R |
| 159 | hello-morning | Morning! Your DJ here. Let's ease into the day. | SELF | R |
| 160 | hello-morning | Good morning, it's your DJ. Coffee is optional, music is not. | SELF CHEESE | R |
| 161 | hello-morning | Rise and shine, it's your DJ. I picked these just for you. | SELF CHEESE | R |
| 162 | hello-morning | Good morning! Your DJ is in the building, and so are your songs. | SELF CHEESE | R |
| 164 | hello-afternoon | Good afternoon! It's your DJ, here to get you through the rest of the day. | SELF | R |
| 165 | hello-afternoon | Hey, good afternoon. Your DJ here, with your afternoon pick me up. | SELF CHEESE | R |
| 166 | hello-afternoon | Afternoon! It's your DJ. Let's keep the day moving. | SELF | R |
| 167 | hello-afternoon | Good afternoon, it's your DJ. I've been saving these for you. | SELF | R |
| 168 | hello-afternoon | Hey there, your DJ here. An afternoon slump? Not on my watch. | SELF CHEESE | R |
| 170 | hello-evening | Good evening! It's your DJ. Let's wind the day down right. | SELF | R |
| 171 | hello-evening | Evening! Your DJ here, and the night is just getting started. | SELF | R |
| 172 | hello-evening | Good evening, it's your DJ. Kick back, I've got the music. | SELF | R |
| 173 | hello-evening | Hey, good evening. Your DJ here, ready when you are. | SELF | R |
| 174 | hello-evening | Evening, friend. It's your DJ, with a little something for tonight. | SELF | R |
| 176 | hello-night | Hey, night owl. It's your DJ, keeping you company. | SELF | R |
| 177 | hello-night | Still up? Me too. It's your DJ. | SELF | R |
| 178 | hello-night | A late night session? Love that. Your DJ here. | SELF | R |
| 179 | hello-night | Hey, night owl, your DJ here. Let's keep it going. | SELF | R |
| 180 | hello-night | It's late, and the music is still on. Your DJ here. | SELF | R |
| 508 | repeat | Here's what you've had on repeat lately, starting with ${q(s)} by ${by(s)}. |  | K |
| 509 | repeat | You know these ones by heart. ${names(songs, 3)}, all on heavy rotation. |  | K |
| 510 | repeat | Your heavy rotation, coming right up. First, ${q(s)}. |  | K |
| 511 | repeat | These are the songs you can't stop playing. Honestly? I get it. | CHEESE | R |
| 512 | repeat | On repeat, and for good reason. ${names(songs, 3)}. Sing along, nobody's listening. | CHEESE | R |
| 513 | repeat | If it's been stuck in your head lately, it's probably in this set. ${q(s)} kicks it off. |  | K |
| 514 | repeat | Your current obsessions, all in one place. ${q(s)} leads the way. |  | K |
| 515 | repeat | You've worn these out lately, and I'm not about to stop you. ${q(s)} first. |  | K |
| 516 | repeat | The songs living rent free in your head right now. Starting with ${q(s)} by ${by(s)}. | CHEESE | R |
| 530 | throwback-plays | Throwback to $y! You played ${q(s)} $n times that year. | EXCL | E |
| 531 | throwback-plays | Let's take it back to $y, when ${q(s)} was basically your whole personality. $n plays, to be exact. | CHEESE | R |
| 532 | throwback-plays | Remember $y? You had ${q(s)} on replay, $n times. |  | K |
| 533 | throwback-plays | The time machine is warmed up. Destination, $y, and ${q(s)}, which you played $n times. | CHEESE | R |
| 534 | throwback-plays | A quick trip back to $y. ${q(s)} was on constant rotation back then, with $n plays. |  | K |
| 535 | throwback-plays | Back to $y we go. ${q(s)} got $n plays from you that year. Let's make it one more. |  | K |
| 536 | throwback-plays | If $y had a theme song for you, it was ${q(s)}. $n plays! | CHEESE | R |
| 538 | throwback | Throwback to $y, starting with ${q(s)} by ${by(s)}. |  | K |
| 539 | throwback | Going back to $y now, with the songs you were into back then. |  | K |
| 540 | throwback | Let's dust off $y. Starting with ${q(s)}. |  | K |
| 541 | throwback | Remember $y? These were your songs. |  | K |
| 542 | throwback | Flashback time: $y. ${names(songs, 3)}. |  | K |
| 543 | throwback | A little nostalgia never hurt. Here's your $y, starting with ${q(s)}. |  | K |
| 557 | throwback | a lot of |  | K |
| 559 | rediscover | Remember this one? You've played ${q(s)} $times times, but not $since. |  | K |
| 560 | rediscover | Some old favourites you haven't heard $since. Starting with ${q(s)}. |  | K |
| 561 | rediscover | Let's dig up a few you used to love. You haven't played ${q(s)} $since. |  | K |
| 562 | rediscover | Here's one that's been gathering dust. ${q(s)}, $times plays, and nothing $since. |  | K |
| 563 | rediscover | Rediscovery time! These used to be big for you, starting with ${q(s)} by ${by(s)}. | EXCL | E |
| 564 | rediscover | Long time no hear. ${q(s)} by ${by(s)}, not played $since. |  | K |
| 565 | rediscover | From the back of the shelf: ${q(s)}. You loved it once, $times plays, but not $since. |  | K |
| 566 | rediscover | Let's bring back a few you forgot about. ${q(s)} first. |  | K |
| 575 | rediscover | at this hour |  | K |
| 578 | hour | Here's what you usually play $whenText, starting with ${q(s)}. |  | K |
| 579 | hour | What you usually play $whenText: ${names(songs, 3)}. |  | K |
| 580 | hour | I've noticed what you like $whenText. Starting with ${q(s)}. |  | K |
| 581 | hour | Right on schedule. These are your go to songs $whenText. |  | K |
| 582 | hour | Your $part regulars are here, led by ${q(s)} by ${by(s)}. |  | K |
| 583 | hour | Same time, same vibe. ${names(songs, 3)}, just how you like it $whenText. |  | K |
| 584 | hour | Clockwork. Around now you usually reach for ${q(s)}, so here it is. | CHEESE | R |
| 585 | hour | Your $part soundtrack, as usual. ${names(songs, 3)}. |  | K |
| 586 | hour | I know what time it is. ${q(s)} time. |  | K |
| 631 | switchUp | Hey, let's switch it up. We're going to play some $m songs, starting with ${q(s)}. |  | K |
| 632 | switchUp | Let's switch it up: some $m songs now, and ${q(s)} goes first. |  | K |
| 633 | switchUp | Time for a change. A few $m songs, starting with ${q(s)} by ${by(s)}. |  | K |
| 641 | moodset-<group> | Hey, let's switch it up. Energy, party and hype, starting with ${q(s)}. |  | K |
| 642 | moodset-<group> | Let's turn it up. Some high-energy ones now, and ${q(s)} gets us going. |  | K |
| 643 | moodset-<group> | Okay, let's get moving. Party and hype, with ${q(s)} first. |  | K |
| 645 | moodset-<group> | Hey, let's switch it up. Something happy and easy now, starting with ${q(s)}. |  | K |
| 646 | moodset-<group> | Let's keep it light. Happy, chill and calm, with ${q(s)} first. |  | K |
| 647 | moodset-<group> | Good vibes only for a bit. ${q(s)} kicks it off. | CHEESE | R |
| 649 | moodset-<group> | Hey, let's switch it up. Something sad and dark now, starting with ${q(s)}. |  | K |
| 650 | moodset-<group> | Lights down. A few of the heavier ones, ${q(s)} first. |  | K |
| 651 | moodset-<group> | Let's sit with it for a while. Sad and dark, starting with ${q(s)}. |  | K |
| 652 | moodset-<group> |  &  |  | K |
| 652 | moodset-<group> |  and  |  | K |
| 657 | moodmix-Happy | Let's lift things up a little. A happy run, starting with ${q(s)}. |  | K |
| 658 | moodmix-Happy | Sun's out, at least in here. Some happy ones, kicking off with ${q(s)}. | CHEESE | R |
| 658 | moodmix-Happy | Time for a mood boost. Happy songs only, and ${q(s)} goes first. |  | K |
| 659 | moodmix-Energetic | Let's pick up the pace. Energy up, starting with ${q(s)}. |  | K |
| 660 | moodmix-Energetic | Okay, let's get moving. ${q(s)} gets us going. |  | K |
| 660 | moodmix-Energetic | Shake off the slump. This one's all energy, and ${q(s)} leads the way. |  | K |
| 661 | moodmix-Party | Let's turn this into a party. ${q(s)} first, so turn it up. |  | K |
| 662 | moodmix-Party | Party mode, on. We're starting with ${q(s)}. | CHEESE | R |
| 662 | moodmix-Party | If you're not dancing yet, give it a minute. ${q(s)} is up. |  | K |
| 663 | moodmix-Hype | Let's get hyped. ${q(s)}, and it only goes up from here. |  | K |
| 664 | moodmix-Hype | Hype mode. Hold on tight, ${q(s)} is first. | CHEESE | R |
| 664 | moodmix-Hype | Energy check. We're going full hype, with ${q(s)}. | CHEESE | R |
| 665 | moodmix-Chill | Let's go somewhere chill. ${q(s)}, then a few more to sink into. |  | K |
| 666 | moodmix-Chill | Time to slow it down and get comfy. ${q(s)} first. |  | K |
| 666 | moodmix-Chill | Deep breath. Something chill now, starting with ${q(s)}. |  | K |
| 667 | moodmix-Calm | Let's bring it way down. Calm and easy, starting with ${q(s)}. |  | K |
| 668 | moodmix-Calm | A quiet one now. ${q(s)}, nice and calm. |  | K |
| 668 | moodmix-Calm | Let's take a breather. ${q(s)}, and a few more like it. |  | K |
| 669 | moodmix-Dreamy | Let's drift for a while. Something dreamy, starting with ${q(s)}. |  | K |
| 670 | moodmix-Dreamy | Head in the clouds time. ${q(s)} first. |  | K |
| 670 | moodmix-Dreamy | A little dreamy detour now, starting with ${q(s)}. |  | K |
| 671 | moodmix-Focus | Heads down, let's focus. ${q(s)}, and a few more to get you in the zone. |  | K |
| 672 | moodmix-Focus | Getting things done? I've got you. Focus mode, starting with ${q(s)}. |  | K |
| 672 | moodmix-Focus | No distractions, just flow. ${q(s)} first. |  | K |
| 673 | moodmix-Romantic | Let's set the mood. Something romantic, starting with ${q(s)}. |  | K |
| 674 | moodmix-Romantic | A little romantic stretch now. ${q(s)} first. |  | K |
| 674 | moodmix-Romantic | Candles optional. Romantic songs, starting with ${q(s)}. | CHEESE | R |
| 675 | moodmix-Sad | Let's sit with our feelings for a bit. Something sad, starting with ${q(s)}. |  | K |
| 676 | moodmix-Sad | Sometimes you just need a sad song. Here's ${q(s)}. |  | K |
| 676 | moodmix-Sad | Okay, tissues ready? A few sad ones, ${q(s)} first. | CHEESE | R |
| 677 | moodmix-Dark | Let's go somewhere darker. ${q(s)} opens the way. |  | K |
| 678 | moodmix-Dark | Lights down. Something dark now, starting with ${q(s)}. |  | K |
| 678 | moodmix-Dark | Into the shadows for a bit. ${q(s)} first. |  | K |
| 679 | moodmix-Intense | Let's turn it up. Something intense, starting with ${q(s)}. |  | K |
| 680 | moodmix-Intense | Brace yourself, it's getting intense. ${q(s)} first. | CHEESE | R |
| 680 | moodmix-Intense | No holding back now. ${q(s)} kicks it off. |  | K |
| 681 | moodmix-other | Let's change the mood. Something ${Moods.adjective(mood)}, starting with ${q(s)}. |  | K |
| 682 | moodmix-other | New mood, coming up. A ${Moods.adjective(mood)} run, starting with ${q(s)}. |  | K |
| 743 | moodmix-new | I've mixed in a couple of new ones I think you'll like. |  | K |
| 744 | moodmix-new | There's something new in there too. Tell me what you think. |  | K |
| 745 | moodmix-new | And a few you haven't heard yet, same vibe. |  | K |
| 746 | moodmix-new | Plus a new find or two, picked just for you. |  | K |
| 760 | mostplayed | Some of your most played, starting with ${q(s)} by ${by(s)}. |  | K |
| 761 | mostplayed | Your most played, and not just the very top. ${q(s)} first. |  | K |
| 762 | mostplayed | The ones you keep coming back to. Let's dig into the list, starting with ${q(s)}. |  | K |
| 763 | mostplayed | From your most played: ${names(songs, 3)}. You know these ones. |  | K |
| 764 | mostplayed | A run through your most played songs, with ${q(s)} by ${by(s)} up first. |  | K |
| 774 | favorites | Some of your favourites, like ${q(s)} by ${by(s)}. |  | K |
| 775 | favorites | Straight from your favourites: ${names(songs, 3)}. You starred these for a reason. |  | K |
| 776 | favorites | Only the best now, your starred songs. Starting with ${q(s)}. |  | K |
| 777 | favorites | You gave these a star, so I'm giving them the spotlight. ${q(s)} first. |  | K |
| 778 | favorites | Favourites time. No skips needed, ${q(s)} leads the way. |  | K |
| 779 | favorites | The ones you saved for a reason. ${q(s)} by ${by(s)} first. |  | K |
| 780 | favorites | All killer, no filler. Your favourites, starting with ${q(s)}. | CHEESE | R |
| 797 | fresh | Something new for you, picked from $from. Let's see what you think. |  | K |
| 798 | fresh | Here's some music you haven't heard yet, because you've been playing $from. |  | K |
| 799 | fresh | Fresh finds, inspired by $from. Skip if they're not for you, I won't take it personally. | CHEESE | R |
| 800 | fresh | New music alert. If you like $from, these should feel right at home. | CHEESE | R |
| 801 | fresh | Let's try something new. I went looking for songs like $from, and I think I found some gems. | CHEESE | R |
| 802 | fresh | New songs incoming. You've been playing $from, so these are for you. |  | K |
| 803 | fresh | I went digging for new stuff in the world of $from. Here's what I found. |  | K |
| 804 | fresh | Ready for something you've never heard? Inspired by $from. |  | K |
| 814 | deep | Deep cuts from $who: songs of theirs you haven't played yet. |  | K |
| 815 | deep | You play a lot of $who, but have you heard these? Let's go deeper. |  | K |
| 816 | deep | You know the hits from $who. Now for the ones only real fans know. |  | K |
| 817 | deep | Beyond the big ones. Here's the $who you haven't heard yet. |  | K |
| 818 | deep | Time to dig into the $who catalogue. Trust me, there's treasure in here. | CHEESE | R |
| 819 | deep | Let's go past the singles. More $who, all new to you. |  | K |
| 820 | deep | There's more to $who than the songs you know. Here's proof. |  | K |
| 839 | genre | Some $tag, since you've been playing $a so much. |  | K |
| 840 | genre | You've had $a on a lot lately, so let's lean into some $tag. |  | K |
| 841 | genre | A little $tag for you. If you like $a, you'll feel right at home. |  | K |
| 842 | genre | Genre of the moment: $tag. Blame $a for this one. |  | K |
| 843 | genre | Since $a is on your mind, let's explore some more $tag. |  | K |
| 844 | genre | A detour into $tag, courtesy of $a. |  | K |
| 853 | like | More like ${q(seed)} by ${by(seed)}. |  | K |
| 854 | like | If you liked ${q(seed)}, you'll like where this is going. |  | K |
| 855 | like | Let's stay in the world of ${q(seed)}, with a few songs that sound just like it. |  | K |
| 856 | like | Riffing on ${q(seed)} now. Same energy, new songs. |  | K |
| 857 | like | Inspired by ${q(seed)} by ${by(seed)}. Let's ride this wave. |  | K |
| 858 | like | ${q(seed)} started something. Here's more in that vein. |  | K |
| 859 | like | Same lane as ${q(seed)}, new songs. Let's go. |  | K |
| 875 | genre-mine | Some $name from your music, since you've been playing a lot of it lately. |  | K |
| 876 | genre-mine | You've been on a real $name kick lately. Here's more from your own collection. |  | K |
| 877 | genre-mine | Let's dig through your $name, starting with ${q(songs[0])}. |  | K |
| 878 | genre-mine | Your music, your genre. A little $name, coming up. |  | K |
| 879 | genre-mine | A $name run, straight from your library. |  | K |
| 920 | genre-mine | ${if (h % 12 == 0) 12 else h % 12} ${if (h < 12)  |  | K |
| 920 | genre-mine |  else  |  | K |
| 945 | fact-milestone | Milestone alert! Coming up is your ${num(n)}th play of ${q(s)}. Let's celebrate. | CHEESE | R |
| 946 | fact-milestone | Big one in this set: ${q(s)} hits ${num(n)} plays. Congratulations to you both. | CHEESE | R |
| 947 | fact-milestone | Get ready, ${q(s)} is about to reach ${num(n)} plays. That's a milestone. |  | K |
| 959 | fact:streak | You've played ${q(s)} on $d of the last seven days. That's a streak, and I'm keeping it alive. |  | K |
| 960 | fact:year-ago | One year ago this week, you were playing ${q(s)} too. Some things never change. |  | K |
| 962 | fact:mood-next | ${q(s)} is a ${Moods.adjective(topMood)} one, which fits: it's the mood you've played most this month. |  | K |
| 966 | fact:artist-since | You've been listening to $a since ${month(t)}. That's loyalty. | CHEESE | R |
| 967 | fact:artist-new | You only found $a this month, and you've already played them ${num(m)} times. |  | K |
| 973 | fact:hours | Did you know? You've spent about ${hoursText(h)} hours listening to ${q(s)}. That's ${num(n)} plays. | CHEESE | R |
| 974 | fact:minutes | You've played ${q(s)} ${num(n)} times. That's about ${(n * dur / 60).toInt()} minutes of it. |  | K |
| 978 | fact:first | Fun fact: you first played ${q(s)} way back in ${month(t)}. | CHEESE | R |
| 979 | fact:crush | You only found ${q(s)} $days days ago, and you've already played it ${num(n)} times. Someone has a new favourite. | CHEESE | R |
| 982 | fact:never-skipped | You've heard ${q(s)} all the way through ${num(full)} times, and you've never skipped it. Not once. |  | K |
| 983 | fact:skipped | Real talk: you've skipped ${q(s)} ${num(sk)} times. Let's see if it wins you over today. | CHEESE | R |
| 986 | fact:top-ten | Heads up: ${q(s)} is your most played song of all time. Number one! | CHEESE | R |
| 987 | fact:top-ten | ${q(s)} is one of your top ten songs of all time. Number ${ordinal(i + 1)}, in fact. |  | K |
| 991 | fact:artist-rank | $lead is your number one artist of all time, with ${num(plays)} plays. Of course they made the set. |  | K |
| 992 | fact:artist-rank | $lead is number ${ordinal(r)} on your all time artist list, with ${num(plays)} plays. |  | K |
| 998 | fact:mood-month | Lately you've mostly played ${Moods.adjective(m)} songs. That's ${n * 100 / moodPlays} percent of what you played from your own music this month. |  | K |
| 1001 | fact:mood-hour | Around this time of day, you usually go for something ${Moods.adjective(m)}. I took notes. | DATA | R |
| 1006 | fact:top-artist-month | Your most played artist this month? $a, with ${num(n)} plays. No surprise there. | CHEESE | R |
| 1008 | fact:second-artist-month | Your number two artist this month is $b, with ${num(n)} plays. ${byMonth[0].key} had better watch out. | CHEESE | R |
| 1010 | fact:top-artist-all | All time, you've played $a ${num(n)} times. They're your number one. |  | K |
| 1011 | fact:artists-count | You've listened to ${num(f.artistAll.size)} different artists. Variety is the spice of life. | CHEESE | R |
| 1012 | fact:songs-count | You've played ${num(f.count.size)} different songs. That's a seriously deep library. |  | K |
| 1014 | fact:top-song-month | Your most played song this month? ${q(s)} by ${by(s)}, with ${num(s.plays)} plays. |  | K |
| 1020 | fact:top-genre-month | Your top genre lately is $g. You've played more of it than anything else this month. |  | K |
| 1023 | fact:peak-hour | You listen to the most music around ${hourName(it.index)}. I see you. | DATA | R |
| 1027 | fact:night-share | About $night percent of your listening happens after midnight. Night owl, confirmed. | CHEESE | R |
| 1028 | fact:morning-share | About $early percent of your music happens before 10 AM. Early bird energy. | CHEESE | R |
| 1031 | fact:month-plays | You've played ${num(f.monthPlays)} songs so far this month. Busy ears. | CHEESE | R |
| 1032 | fact:month-plays | ${num(f.monthPlays)} plays this month and counting. I'm keeping track. | DATA | R |
| 1034 | fact:new-artist-month | Your big discovery this month? $a. ${num(f.artistMonth[a] ?: 0)} plays since you found them. |  | K |
| 1036 | fact:total-plays | I've counted ${num(f.total)} plays of yours. That's a lot of music. | DATA | R |
| 1038 | fact:since | Your listening history goes back to ${month(f.since)}. That's over ${num(years.toInt())} years of music, and I've been reading all of it. | DATA | R |
| 1042 | fact:year-top | Your number one song of $y was “${top.first.title}” by ${top.first.artist.split( |  | K |
| 1042 | fact:year-top | )[0].trim()}, with ${num(top.second)} plays. |  | K |
| 1044 | fact:busiest-year | This is your biggest year for music yet, with ${num(n)} plays so far. Keep it up. |  | K |
| 1045 | fact:busiest-year | Your biggest year for music was $y, with ${num(n)} plays. Think you can beat it? | CHEESE | R |
| 1049 | fact:most-skipped | The song you skip the most is ${q(t)}, with ${num(sk)} skips. No hard feelings though, it stays in the mix for when the mood is right. |  | K |
| 1052 | fact:never-skipped | You've never skipped ${q(t)}. ${num(full)} times, start to finish. |  | K |
| 1055 | fact:skips-all | You've skipped ${num(skips)} songs in all. Don't worry, a skip never keeps a song out of my sets, it just means the mood wasn't right. |  | K |
| 1058 | fact:skips-week | You've skipped ${num(n)} songs this week. Picky mood lately? That's fine, I'll keep the hits coming. |  | K |
| 1060 | fact:skipped-week | This week you skipped ${q(t)} ${num(sk)} times. Not feeling it right now? It'll be back when you are. |  | K |
| 1063 | fact:heard-out | You hear ${outs * 100 / (skips + outs)} percent of songs all the way through. That's commitment. | CHEESE | R |
| 1083 | fact:mood-week | This week you've mostly played ${adj(m)} songs. ${num(n)} plays of them, to be exact. |  | K |
| 1084 | fact:mood-week | The mood of the week in your plays? ${m}. ${MoodTally.percent(week, m)} percent of what you played from your music. |  | K |
| 1085 | fact:mood-week | If this week had a mood, it'd be $m. You played ${num(n)} ${adj(m)} songs. |  | K |
| 1093 | fact:mood-any | Here's one: ${MoodTally.percent(all, m)} percent of what you play from your music is ${adj(m)}. That's ${num(n)} plays. |  | K |
| 1094 | fact:mood-any | ${m.replaceFirstChar { it.uppercase() }} songs: ${num(n)} plays all told |  | K |
| 1094 | fact:mood-any | , and ${num(mo)} this month. |  | K |
| 1094 | fact:mood-any | , but none this month. |  | K |
| 1095 | fact:mood-any | Mood check: you've played ${num(n)} ${adj(m)} songs, ${MoodTally.percent(all, m)} percent of your listening. | CHEESE | R |
| 1096 | fact:mood-any | This month you've played ${num(mo)} ${adj(m)} songs |  | K |
| 1096 | fact:mood-any | , ${MoodTally.percent(month, m)} percent of the month. |  | K |
| 1099 | fact:mood-all | All time, the mood you play most is $m. That's ${num(n)} plays, and ${MoodTally.percent(all, m)} percent of your music. |  | K |
| 1103 | fact:mood-shift | You've been going for more $m than usual lately. Up from $was percent last month to $pct percent now. |  | K |
| 1104 | fact:mood-shift | Something's changed. Your $m songs went from $was percent of your plays to $pct percent this month. |  | K |
| 1105 | fact:mood-shift | I'm noticing more ${adj(m)} songs from you lately. About $pct percent this month, from $was percent before. |  | K |
| 1111 | fact:mood-song | Your go to ${adj(topMood)} song? ${q(t)} by ${by(t)}. You've played it ${num(n)} times. |  | K |
| 1120 | fact:mood-time | Late at night, you get ${adj(m)}. Your $m plays spike after ten. |  | K |
| 1121 | fact:mood-time | Mornings are for $m, apparently. That's when you play the most ${adj(m)} songs. |  | K |
| 1122 | fact:mood-time | Your afternoons lean $m. That's when the ${adj(m)} songs come out. |  | K |
| 1123 | fact:mood-time | Evenings bring out your $m side. That's when you play the most ${adj(m)} music. |  | K |
| 1128 | fact:mood-hype | About $hype percent of your listening is Hype. You don't do quiet, do you? | CHEESE | R |
| 1129 | fact:mood-hype | About $hype percent of your music is Hype. Just enough to keep things moving. |  | K |
| 1132 | fact:mood-neglected | You used to play a lot of ${adj(m)} songs, but hardly any lately. Maybe I'll sneak one in. |  | K |
| 1209 | fact:mood-neglected |  and  |  | K |
| 1210 | fact:mood-neglected | $a and ${picked[1].first} |  | K |
| 1212 | discover | Here's someone new I think you'll like: $a. If you're into $like, this is right up your alley. |  | K |
| 1213 | discover | Time to find you a new favourite. Meet $both. You play a lot of $like, so trust me on this one. | CHEESE | R |
| 1214 | discover | Fans of $like tend to love $a. Let's see if you do too. |  | K |
| 1215 | discover | I've got a good feeling about this one. $a, for the $like fan in you. |  | K |
| 1216 | discover | New artist alert! Say hello to $both. You can thank $like for this one. | CHEESE | R |
| 1217 | discover | Someone you should know: $a. Big $like energy. |  | K |
| 1218 | discover | Let me introduce you to $both. If $like is your thing, you're in for a treat. | CHEESE | R |
| 1272 | spotlight | Artist spotlight: $a. You've played them ${num(n)} times, so here's a mix of the big ones and a few deeper cuts, starting with ${q(s)}. |  | K |
| 1273 | spotlight | Let's give $a the stage for a few songs. Some favourites, some you don't play as much, and ${q(s)} goes first. |  | K |
| 1274 | spotlight | Spotlight on $a now. ${num(n)} plays tell me you're a fan. Here's ${q(s)}. | DATA | R |
| 1274 | spotlight | Spotlight on $a now, from your library. Here's ${q(s)}. |  | K |
| 1275 | spotlight | It's a $a moment. The hits, the hidden ones, the lot. Starting with ${q(s)}. |  | K |
| 1276 | spotlight | Five songs, one artist: $a. Let's go, with ${q(s)}. |  | K |
| 1297 | album | An album moment: three in a row from $album by ${by(s)}, in order, the way it was meant to be heard. |  | K |
| 1298 | album | Let's sit with $album for a bit. Three straight from the record, starting with ${q(s)}. |  | K |
| 1299 | album | Album time. From $album, back to back: ${q(s)} and the two that follow it. |  | K |
| 1300 | album | No shuffling for this one. $album by ${by(s)}, three songs in a row. |  | K |
| 1312 | album | One year ago |  | K |
| 1312 | album | ${ordinal(ya).replaceFirstChar { it.uppercase() }} years ago |  | K |
| 1314 | yearago | $ago this week, this is what you were listening to. Starting with ${q(s)} by ${by(s)}. |  | K |
| 1315 | yearago | Flashback to this week, ${ago.lowercase()}. You had ${q(s)} on. |  | K |
| 1316 | yearago | $ago today, give or take a few days, these were your songs. ${q(s)} first. |  | K |
| 1317 | yearago | On this week ${ago.lowercase()}: ${names(songs, 3)}. Bring back any memories? |  | K |
| 1343 | yearago | the ${decade % 100}s |  | K |
| 1346 | decade | Let's take a trip to $d. Everything in this set came out back then, starting with ${q(s)}. |  | K |
| 1347 | decade | Decade trip! We're heading to $d with ${q(s)} by ${by(s)}. | EXCL | E |
| 1348 | decade | Set the dial to $d. Your music from that decade, starting with ${q(s)}. |  | K |
| 1349 | decade | A little time travel: $d, straight from your collection. |  | K |
| 1360 | onehit | Your one hit wonders: artists you've only got a song or two from. Starting with ${q(s)} by ${by(s)}. |  | K |
| 1361 | onehit | Some artists only made it into your music once. Here's a few of them, like ${by(s)}. |  | K |
| 1362 | onehit | The lone stars of your library. One song each, starting with ${q(s)}. | CHEESE | R |
| 1363 | onehit | Single appearances only. ${names(songs, 3)}, each the only one of theirs you have. |  | K |
| 1386 | neverskip | Songs you've played this month and haven't skipped once. Starting with ${q(s)}. |  | K |
| 1387 | neverskip | Every time these came on this month, you heard them to the end. ${q(s)} first. |  | K |
| 1388 | neverskip | Zero skips this month between them. These are the keepers, starting with ${q(s)} by ${by(s)}. |  | K |
| 1389 | neverskip | Not a single skip this month. Here's ${names(songs, 3)}. |  | K |
| 1400 | second | Second chances. These are ones you usually skip, but maybe today's the day. ${q(s)} first. |  | K |
| 1401 | second | Hear me out. You skip these a lot, but I think the mood might be right this time. Starting with ${q(s)}. | CHEESE | R |
| 1402 | second | A few songs that never get a fair shot. Let's give ${q(s)} one more try. |  | K |
| 1403 | second | Second chance round. If one still isn't for you, skip away, no hard feelings. |  | K |
| 1414 | unreleased | Welcome to the unreleased corner. Songs you won't find anywhere else, starting with ${q(s)}. |  | K |
| 1415 | unreleased | Off the record now. Some unreleased ones from your vault, like ${q(s)} by ${by(s)}. |  | K |
| 1416 | unreleased | From the vault: unreleased tracks only. ${q(s)} first. |  | K |
| 1417 | unreleased | These never came out officially, but they're in your music. Here's ${q(s)}. |  | K |
| 1428 | streak | You've played ${q(s)} on $n of the last seven days. That's a streak. Here it is, with a few more you can't put down. |  | K |
| 1429 | streak | Streak alert! ${q(s)} has been on almost every day this week. Let's keep it going. | CHEESE | R |
| 1430 | streak | These are the songs you've played day after day this week, starting with ${q(s)}. |  | K |
| 1431 | streak | Every day this week, practically. ${names(songs, 3)}. On a streak, and I'm not stopping it. |  | K |
| 1476 | t-fri | It's Friday night. Let's go. |  | K |
| 1476 | t-fri | Friday night, and the weekend starts now. |  | K |
| 1477 | t-sat | Saturday, no rush. |  | K |
| 1477 | t-sat | It's the weekend, so let's enjoy it. |  | K |
| 1477 | t-sat | Saturday vibes, coming right up. |  | K |
| 1478 | t-sun | Easy Sunday morning. |  | K |
| 1478 | t-sun | Lazy Sunday? I've got you. |  | K |
| 1478 | t-sun | Sunday mornings are for good music. |  | K |
| 1479 | t-mon | Monday morning. Let's ease into the week. |  | K |
| 1479 | t-mon | New week, fresh start. |  | K |
| 1479 | t-mon | Mondays are better with music. |  | K |
| 1480 | t-late | It's late, but we're still going. |  | K |
| 1480 | t-late | Past midnight, and the music's still on. |  | K |
| 1480 | t-late | Late night, keeping you company. |  | K |
| 1481 | t-early | Early start today. |  | K |
| 1481 | t-early | Up with the sun, I see. |  | K |
| 1496 | songComment | milestone:${songKey(t)}:${n + 1} |  | K |
| 1499 | songComment | Big moment: this is your ${num(n + 1)}th play of ${q(t)}. Here's to ${num(n + 1)} more. | CHEESE | R |
| 1505 | fact:c-top | Your number one song of all time, coming up. |  | K |
| 1505 | fact:c-top | This one's in your all time top ten. Number ${ordinal(i + 1)}. |  | K |
| 1508 | fact:c-never | You've never skipped this one. ${num(full)} times, start to finish. |  | K |
| 1512 | fact:c-crush | You found this one $days days ago and you've played it ${num(n)} times already. |  | K |
| 1513 | fact:c-old | An old friend: you first played this back in ${month(first)}. |  | K |
| 1548 | from-loved | From $name, the ones you keep coming back to, like ${q(songs[0])} by ${by(songs[0])}. |  | K |
| 1549 | from-loved | The best of $name, by your count. ${q(songs[0])} is up first. | DATA | R |
| 1550 | from-loved | Your top picks from $name, starting with ${q(songs[0])}. |  | K |
| 1551 | from-loved | These are the songs from $name you play the most. No surprises, just the good stuff. |  | K |
| 1557 | from-unplayed | Some from $name you haven't played yet, starting with ${q(songs[0])} by ${by(songs[0])}. |  | K |
| 1558 | from-unplayed | Hiding in $name: songs you've never played. Let's fix that, starting with ${q(songs[0])}. |  | K |
| 1559 | from-unplayed | First listen time. These are from $name, and they're all new to you. |  | K |
| 1560 | from-unplayed | You've got songs in $name you haven't even heard yet. Here's ${q(songs[0])}. |  | K |
| 1566 | from-throwback | From $name, a few you haven't played in a while, like ${q(songs[0])}. |  | K |
| 1567 | from-throwback | Let's revisit some of $name you haven't heard lately. ${q(songs[0])} first. |  | K |
| 1568 | from-throwback | It's been a while for these ones from $name. Welcome back, ${q(songs[0])}. |  | K |
| 1569 | from-throwback | Dusting off a few from $name, starting with ${q(songs[0])} by ${by(songs[0])}. |  | K |
| 1575 | from-hour | From $name, what you usually play around now, like ${q(songs[0])}. |  | K |
| 1576 | from-hour | Right on time. From $name, your usual for this hour, starting with ${q(songs[0])}. |  | K |
| 1577 | from-hour | From $name, the ones that always come out around this time. ${q(songs[0])} leads. |  | K |
| 1578 | from-hour | The songs from $name you reach for at this hour, starting with ${q(songs[0])}. |  | K |
| 1588 | from-artist | A few from $who, from $name. |  | K |
| 1589 | from-artist | Spotlight on $who now, straight from $name. |  | K |
| 1590 | from-artist | Let's give $who some time. A few of theirs from $name. |  | K |
| 1591 | from-artist | From $name, a little run of $who, starting with ${q(songs[0])}. |  | K |
| 1597 | from-mix | More from $name: ${names(songs, 3)}. |  | K |
| 1598 | from-mix | A bit of everything from $name. ${q(songs[0])} kicks it off. |  | K |
| 1599 | from-mix | Shuffling through $name. Up next, ${q(songs[0])} by ${by(songs[0])}. | BANNED | R |
| 1600 | from-mix | Back to $name with a mix. ${names(songs, 3)}. |  | K |
| 1617 | askFrom | From $name, something ${Moods.adjective(mood)}: “${songs[0].title}” by ${names(songs.take(1))} first. |  | K |
| 1619 | askFrom | ${t.title} ${t.artist} ${t.album} ${genreOf(t)} |  | K |
| 1622 | askFrom | From $name, you asked for ${text.trim()}: “${songs[0].title}” by ${names(songs.take(1))} first. |  | K |
| 1633 | askAny | There's nothing like that in ${sc.first}, so from everywhere: ${it.line} |  | K |
| 1641 | askMood | You asked for $asked: here's something ${Moods.adjective(mood)} from your music, starting with “${songs[0].title}” by ${names(songs.take(1))}. |  | K |
| 1667 | askAll | ${info?.name?.ifEmpty { null } ?: who}, coming up: some you know, and some you might not. |  | K |
| 1674 | askAll | You asked for $asked: here's ${g.lowercase()} from your music, starting with “${songs[0].title}” by ${names(songs.take(1))}. |  | K |
| 1678 | askAll | You asked for $asked, so here's some $tag, starting with “${songs[0].title}” by ${names(songs.take(1))}. |  | K |
| 1684 | askAll | You asked for $asked. Here's a mix of it, starting with “${songs[0].title}” by ${names(songs.take(1))}. |  | K |

Totals: keep 264, light edit 3, rewrite 85.
