#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
ffmpeg -hide_banner -loglevel error \
  -f lavfi -i 'color=red:s=320x240:r=30:d=2' \
  -f lavfi -i 'sine=frequency=440:sample_rate=44100:duration=2' \
  -vf 'drawbox=x=0:y=120:w=320:h=120:color=blue:t=fill' \
  -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest -movflags +faststart \
  -y app/src/androidTest/assets/source.mp4
