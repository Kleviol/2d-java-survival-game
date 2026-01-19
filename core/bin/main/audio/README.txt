Place your audio files here.

Expected default filenames (configurable in assets/config/game.properties):

- audio/bgm.ogg  (looping background music)
- audio/hit.wav  (enemy hit sound effect)
- audio/attack.wav  (hero attack sound effect)
- audio/success.wav  (victory sound effect)

If these files are missing, the game will auto-generate simple fallback WAV audio on first run
into local storage under: audio_generated/

Formats supported by libGDX depend on platform; on desktop, OGG for music and WAV/OGG for SFX are typical.
