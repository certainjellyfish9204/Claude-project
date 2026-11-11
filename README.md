# VideoEditor v5

Changes from v4:
- Exports are saved to Movies/VideoEditor (visible in Gallery and Files)
- App icon (blue background, white play button)
- gradle.properties with AndroidX enabled
- minSdk 29 (Android 10 or newer), required for MediaStore saving

## Install from Termux
   cd ~
   unzip -o ~/storage/downloads/VideoEditor_v5.zip -d ~/v5tmp
   cp -r ~/v5tmp/VideoEditor_v5/. ~/VideoEditor_v4/
   rm -rf ~/v5tmp
   cd ~/VideoEditor_v4
   git add -A && git commit -m "v5" && git push origin main
