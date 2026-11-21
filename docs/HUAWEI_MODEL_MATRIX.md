# Huawei audio support and artwork accuracy matrix

**Scope:** experimental LibreBuds+ `alpha/librebuds-plus`. Audited against the repo's 40 Huawei audio model profiles on 2026-11-21. A model being detected does **not** prove that its battery, ANC, EQ, or gesture commands work.

## Evidence levels

- **Owner-tested:** FreeBuds 6i was exercised on real hardware; not every advertised feature/firmware combination is individually confirmed.
- **Earlier hardware evidence:** the inherited profile/test history records some successful physical-device work (e.g. FreeBuds 5 and Pro 5). This does not revalidate all functions in the current alpha.
- **Unverified / needs testing:** profile, identifier or standard protocol implementation exists, but hardware operation is not established. Never show fictional percentages or nonfunctional controls.
- **Battery-only:** the profile permits a Huawei battery read but no guessed ANC/EQ/gesture writes. Some new and legacy profiles use this conservative level.
- **Art-only / identification-only:** an illustration or recognized model name is not a vendor control link.

## Model-by-model family audit

| Family | Profile IDs (all individual models) | Current hardware evidence | Picture/case accuracy |
| --- | --- | --- | --- |
| FreeBuds mainline | `freebuds-3`, `freebuds-4`, `freebuds-5`, `freebuds-6`, `freebuds-7` | Prior tests on selected models; rest needs testing | Dedicated for 3–6; generic for 7 |
| FreeBuds i-series | `freebuds-3i`, `freebuds-4i`, `freebuds-5i`, `freebuds-6i`, `freebuds-7i` | 6i owner-tested; others need testing | Approximate stem silhouette except 6i (Pro 5 proxy) |
| FreeBuds 4E | `freebuds-4e`, `freebuds-4e-v2` | Needs testing | FreeBuds 4 family art, approximate |
| FreeBuds Pro | `freebuds-pro`, `freebuds-pro-2`, `freebuds-pro-2-plus`, `freebuds-pro-3`, `freebuds-pro-4`, `freebuds-pro-5` | Earlier tests on selected Pro models; rest needs testing | Dedicated for Pro 2–5; Pro 2+ uses Pro 2 art; original Pro stem fallback |
| FreeBuds SE | `freebuds-se`, `freebuds-se-2`, `freebuds-se-3`, `freebuds-se-4-anc`, `freebuds-se-5-max` | Unverified | Generic stem silhouette; generic case |
| FreeBuds other | `freebuds-lite`, `freebuds-neo`, `freebuds-lipstick`, `freebuds-lipstick-2`, `freebuds-studio` | Unverified | Generic fallback; stem silhouette for most TWS; Studio headset form is not represented faithfully |
| FreeClip | `freeclip`, `freeclip-2`, `freeclip-2-v2` | Unverified | Generic fallback — not an accurate clip/case image |
| FreeArc | `freearc` | Unverified | Generic fallback — not an accurate open-ear/case image |
| FreeLace | `freelace`, `freelace-lite`, `freelace-pro`, `freelace-pro-2` | Unverified | Generic fallback — not an accurate neckband image; case graphic is not a real FreeLace case |
| Huawei Eyewear | `eyewear`, `eyewear-2` | Unverified | Generic fallback — not an accurate glasses illustration |
| Huawei W Buds | `w-buds`, `w-buds-2` | Unverified | Generic fallback — not an accurate watch-earbud enclosure |

**40 audio profiles in total.** An additional Huawei Smart PowerBank profile exists in the upstream catalog but is **not audio gear** and is excluded from earbud discovery.

## How the app handles them

1. **Bluetooth discovery:** strict known model or SKU/ModelId identification; tolerates casing, spacing, Huawei prefix and optional factory-code suffix. Clearly named paired Huawei audio products can be listed when Android has not yet cached the SPP UUID. Renamed/unidentified products may remain generic.
2. **Transport:** the original Huawei RFCOMM/SPP path, not the unrelated-brand PairFree engine. Earbuds may share packet framing but still have different firmware commands and behavior.
3. **Battery:** the UI displays left/right/case values only where live packets actually report them. A case must not be fabricated for a neckband, clip, eyewear or any model that doesn't report one.
4. **Settings:** a model's profile decides which reads and writes are attempted; unverified settings cannot be treated as confirmed just because the related model has them.
5. **Artwork:** dedicated illustrations are supplied for FreeBuds 3/4/5/6 and Pro 2/3/4/5. FreeBuds 6i retains the existing **Pro 5 approximation** to avoid a breaking visual change. FreeBuds 4E and Pro 2+ use explicitly approximate family artwork, and in-ear budget models now use a more suitable stem silhouette instead of bean-shaped buds.
6. **Non-TWS hardware:** no existing model-specific licensed art is available for FreeClip, FreeArc, FreeLace, Eyewear, W Buds or FreeBuds Studio. For these non-TWS devices, the battery panel now shows a text label with real reported battery percentages **instead of misleading generic bud/case images**, until accurate artwork is available. Accurate art must be sourced or illustrated separately before claiming otherwise.

## Requirements before calling an individual model fully supported

A real-device test should verify pairing through Android, successful Huawei control handshake, left/right/case battery where applicable, noise modes, available EQ and gesture controls, reconnect after power-cycle, and error behavior during phone calls. Record the exact model and firmware. Never claim all 40 tested based on automated unit tests.

See [DEVICE_COMPATIBILITY.md](DEVICE_COMPATIBILITY.md) and [NOTICE](../NOTICE). Unsupported devices should remain functional as standard Android Bluetooth audio without fake vendor controls.
