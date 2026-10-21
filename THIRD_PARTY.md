# Third-party material

Liftoff's code, scripts, docs, gear icon and banner are MIT. The Now Playing screenshot blurs the album art (see [LICENSE](LICENSE)).
The following are bundled and keep their own licenses:

| What | Where | Source | License |
|---|---|---|---|
| Michroma typeface | `app/assets/michroma.ttf` | The Michroma Project Authors, [googlefonts/Michroma-font](https://github.com/googlefonts/Michroma-font) | SIL Open Font License 1.1 — full text in [`app/assets/Michroma-OFL.txt`](app/assets/Michroma-OFL.txt) |
| "Cosmic Cliffs" (README screenshot only) | `docs/screenshots/home.jpg` | NASA, ESA, CSA, STScI, via [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:NASA%E2%80%99s_Webb_Reveals_Cosmic_Cliffs,_Glittering_Landscape_of_Star_Birth.jpg) | Public domain (NASA) |
| "Starman" background photo | `app/res/drawable-nodpi/home_bg.png`, `docs/screenshots/now-playing.jpg` | SpaceX, Falcon Heavy Demo Mission, via [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Elon_Musk%27s_Tesla_Roadster_(40143096241).jpg) ([Flickr](https://www.flickr.com/photos/spacex/40143096241/)) | CC0 1.0 public domain dedication. Upscaled with [Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN) (BSD-3-Clause) and resized to 1920×1080 |

Not bundled, fetched at runtime by the app on the TV:

- **Weather:** [Open-Meteo](https://open-meteo.com/) forecast API (free, no key; data CC BY 4.0, attribution: "Weather data by Open-Meteo.com").
- **NASA Astronomy Picture of the Day:** read from [science.nasa.gov/apod](https://science.nasa.gov/apod/).
  Many APOD images are copyrighted by their photographers; Liftoff only displays them on your own TV
  and never redistributes them.

Downloaded by `scripts/setup-toolchain.sh` into `.toolchain/` (not part of this repo):
Eclipse Temurin JDK 17 (GPLv2 with Classpath Exception) and the Android SDK command-line tools,
platform-tools, build-tools and platform (Android Software Development Kit License, which the script asks you to accept).

Trademarks (Google TV, Android TV, TCL, Netflix, Spotify, Govee, NASA, SpaceX, etc.) belong to their
owners. Liftoff is not affiliated with or endorsed by any of them.
