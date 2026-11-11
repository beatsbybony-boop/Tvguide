# TV Guide for Fire Stick

A 1990s cable-style channel guide for Fire TV / Android TV. Channels loop
open-licensed and public-domain video from the Internet Archive, laid out in a
classic time grid with a live preview window, a clock and the local temperature.

## Channels

Channels 2-13 loop single films, cartoons and shows from the Internet Archive.

Channels 20-25 are music video channels (80s Hip Hop, 90s Hip Hop, 80s Synth,
Grunge+Metal, 80s Pop Hits, 90s Pop Hits). They play YouTube playlists through
YouTube's own embedded player, shuffled, with a "NOW PLAYING" banner. Videos the
uploader doesn't allow to be embedded are skipped. If a whole playlist can't be
played, the channel switches automatically to half-hour mixes of music videos from
the Internet Archive.

Channel 30 (Free Movies) plays free full-length movies on YouTube: FilmRise,
public-domain classics and Popcornflix, in 3-hour blocks. It falls back to the
Archive's feature films the same way.

Channel 14 (CN Official) plays the official Cartoon Network YouTube channel, and
channel 31 (80s-90s Films) plays a free 1980s movie playlist plus Full Moon
Features' official full-movie playlist.

Channels 32-37 are genre movie channels: Comedy, Horror, Action, Sci-Fi Movies,
Halloween and Christmas, built from free-movie playlists (Popcornflix, FilmRise,
Full Moon Features, public-domain collections) with Internet Archive fallbacks.

Channel 38 (Racing) combines and shuffles NASCAR's official full race replay
playlists. A channel with `"combine": true` merges all its playlists into one
shuffled stream; to add more races, add playlist IDs to its `youtube` list.

Channels 40-51: Nick (combined shuffle of several Nick shows), Bob Ross, Unsolved
Mysteries, Macy's Parade, Pee-wee, Game Shows, Cleetus and Dirty Mo. A `youtube`
entry can be a playlist ID, a channel's uploads (a playlist ID starting `UU`, i.e.
the channel ID with `UC` changed to `UU`), or a single video written `v:VIDEOID`.

To add your own YouTube playlist as a channel, add an entry to `channels.json` with
the playlist ID (the part after `list=`). The playlist must be Public or Unlisted.

YouTube requires embedded players to identify the app, so the player page is loaded
with the app ID (`https://<applicationId>`) as its base URL. If you change the
`applicationId` in `app/build.gradle.kts`, that updates automatically.

In `channels.json`, a channel's `youtube` list holds playlist IDs (the part after
`list=` in a YouTube playlist link). Fast-forward / rewind skip to the next /
previous video on those channels.

## Remote controls

**In the guide**

| Button | Action |
| --- | --- |
| Up / Down | Move between channels |
| Left / Right | Move between programs in time |
| Select | Tune the highlighted channel (full screen) |
| Menu | Return to full screen |
| Back | Exit the app |
| Play/Pause, Fast-forward, Rewind | Pause, +30s, -30s |

**Full screen**

| Button | Action |
| --- | --- |
| Up / Down | Channel surf |
| Select / Menu / Back | Open the guide |

Pressing Select on the channel that's already playing takes you straight back to full screen.

## Get the APK

Every push to `main` builds the app on GitHub. Open the **Releases** page of
this repo and download `TVGuide.apk`, or use the **Actions** tab and grab the
`TVGuide-apk` artifact.

## Install on the Fire Stick

1. On the Fire Stick: Settings > My Fire TV > Developer Options > Install unknown apps, and allow **Downloader**.
2. Install **Downloader** from the Amazon Appstore.
3. In Downloader, enter the release download link, for example
   `https://github.com/<you>/<repo>/releases/latest/download/TVGuide.apk`
4. Install, then open **TV Guide** from Your Apps & Channels.

## Customise

* **Temperature location**: `app/src/main/java/com/beatsbybony/tvguide/Config.kt`
* **Channels**: `app/src/main/assets/channels.json`. Each channel is an Internet
  Archive search query. Numbers and names are up to you.
