Short 440 Hz tones for `DesktopCardAudioTest`, made with ffmpeg:

    ffmpeg -f lavfi -i "sine=frequency=440:duration=0.3" -ar 22050 -ac 1 -c:a libmp3lame -b:a 32k tone.mp3
    ffmpeg -f lavfi -i "sine=frequency=440:duration=1" -ar 44100 -ac 2 -strict -2 -c:a vorbis tone.ogg
    ffmpeg -f lavfi -i "sine=frequency=440:duration=0.3" -ar 22050 -ac 1 -c:a aac tone.m4a

`tone.m4a` (AAC) has no decoder on the desktop: the test checks that it is reported, not played.

ffmpeg's experimental Vorbis encoder makes streams that JOrbis decodes to nothing when they are shorter than about a second or use 22 kHz, so `tone.ogg` is one second of 44.1 kHz stereo. Files from libvorbis do not have the problem.
