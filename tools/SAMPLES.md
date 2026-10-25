# Instrument samples

MouthBand plays every preset without any audio files: each sample preset has a synth patch and
every drum kit has synthesized one-shots (spec §2.6). Bundling real samples makes the sample
presets and kits sound like the instruments they are named after. This file says what to export,
how to name it and how to check that the app picked it up.

## Source

**FluidR3_GM.sf2** (MIT licence) — the General MIDI SoundFont by Frank Wen. Edit it with the free
SoundFont editor **Polyphone** (https://www.polyphone.io).

When samples are bundled, add the FluidR3_GM MIT notice to `THIRD_PARTY_NOTICES.md` and as
`app/src/main/assets/licenses/fluidr3_gm_mit.txt` (the Licenses screen lists every file there
once its entry is added in `ui/licenses/LicenseEntry.kt`).

## What the app loads

```
app/src/main/assets/samples/
  electric_bass/<midi>.wav     Electric Bass (finger), GM program 34
  soft_keys/<midi>.wav         Electric Piano 1, GM program 5
  piano/<midi>.wav             Acoustic Grand Piano, GM program 1
  flute/<midi>.wav             Flute, GM program 74
  kits/boom_bap/{kick,snare,hat_closed,hat_open,clap}.wav   Standard kit
  kits/trap/{kick,snare,hat_closed,hat_open,clap}.wav       TR-808 kit
  kits/lofi/{kick,snare,hat_closed,hat_open,clap}.wav       Jazz or Brush kit
  kits/rock/{kick,snare,hat_closed,hat_open,clap}.wav       Power kit
  kits/pop/   (optional — Pop reuses: kick from boom_bap, snare + clap from trap, hats from rock)
```

`<midi>` is the sample's root note as a MIDI number (60 = middle C). The sampler picks the nearest
root and pitch-shifts by resampling, so roots **every 3 semitones** keep each shift within
±1.5 semitones:

| Folder | Roots to export (MIDI) |
|---|---|
| electric_bass | 28, 31, 34, 37, 40, 43, 46 |
| soft_keys | 48, 51, 54, 57, 60, 63, 66, 69, 72 |
| piano | 54, 57, 60, 63, 66, 69, 72, 75, 78, 81, 84 |
| flute | 54, 57, 60, 63, 66, 69, 72, 75, 78, 81, 84 |

GM drum notes for the kits: kick 36, snare 38, clap 39, closed hi-hat 42, open hi-hat 46.

## File format

- WAV, **44.1 kHz, mono**, 16-bit PCM (24-bit and 32-bit float are read too).
- Trimmed: the attack starts within the first millisecond, no leading silence.
- Tails ≤ 2 s, faded out to silence at the end.
- Peak around −1 dBFS; keep the instruments of one folder at matching loudness.

## Export with Polyphone

1. Open `FluidR3_GM.sf2` in Polyphone.
2. In the tree, open **Presets** and select the preset (bank 0 for instruments, bank 128 for drum
   kits). Its **Instrument** shows the key ranges and the samples it uses.
3. Under **Samples**, select the samples of that instrument. For each needed root, use the sample
   whose root key is closest; if the sample's root differs from the table, name the file after the
   sample's **actual** root key (the app reads the root from the file name).
4. Right-click → **Export** → WAV. Repeat for every root (or every drum note).
5. Convert to the format above (Polyphone's sample editor can trim and fade; any audio editor can
   resample to 44.1 kHz mono and set the peak). Name the files `<midi>.wav` / `<piece>.wav`.
6. Copy them into the folders listed above.

## Check

Build and run a debug build. Without a file the log shows `D MouthBand: no asset samples/...`
and that preset uses its synth patch. With the files in place those lines disappear for the
bundled presets. Play each preset in a Review screen across its range and listen for clicks at
loop boundaries and uneven loudness between roots.
