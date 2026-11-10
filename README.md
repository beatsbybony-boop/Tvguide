# TV Guide for Fire Stick

A 1990s cable-style channel guide for Fire TV / Android TV. Channels loop
open-licensed and public-domain video from the Internet Archive, laid out in a
classic time grid with a live preview window, a clock and the local temperature.

## Channels

Channels 2-13 loop single films, cartoons and shows. Channels 20-25 are music
video channels (80s Hip Hop, 90s Hip Hop, 80s Synth, Grunge+Metal, 80s Pop Hits,
90s Pop Hits). Short music videos are grouped into half-hour mix blocks, and a
"NOW PLAYING" banner shows the current video. Music videos on the Internet Archive
are user-uploaded, so how many each channel finds can vary.

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
