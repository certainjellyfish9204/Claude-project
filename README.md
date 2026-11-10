# VideoEditor v4

Same features as v3, with three build changes:
- FFmpeg comes from the maintained FFmpegKit fork (dev.ffmpegkit-maintained:ffmpeg:7.1.6)
- Export uses MPEG-4 video, because the free FFmpeg build has no H.264 encoder
- A GitHub Actions workflow builds the APK in the cloud (.github/workflows/build.yml)

## Get the APK without a computer
1. Install Termux from F-Droid (the Play Store version is outdated).
2. In Termux:
   termux-setup-storage
   pkg install git unzip
   unzip ~/storage/downloads/VideoEditor_v4.zip -d ~
   cd ~/VideoEditor_v4
   git init -b main && git add . && git commit -m "VideoEditor v4"
   git remote add origin https://github.com/YOUR_USER/YOUR_REPO.git
   git push -u origin main
   (Create an empty repo on github.com first. Use a personal access token with repo scope as the password.)
3. On github.com, open the repo's Actions tab, then the "Build APK" run, and download VideoEditor-debug-apk.
4. Unzip it and install the APK. Allow "Install unknown apps" for your file manager when Android asks.
