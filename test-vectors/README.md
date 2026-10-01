# Test vectors

Shared by the Android `:protocol` tests and the Windows fork. One JSON object per line:

```json
{"id":"battery-response-pro2","model":"freebuds-pro-2","synthetic":true,"source":"...","hex":"5A 00 10 ...","expect":{"cmd":"01/08","tlv":{"1":"4A","2":"64 54 4A"}}}
```

| Field | Meaning |
|---|---|
| `id` | unique, kebab-case |
| `model` | profile id from `profiles/`, or `any` |
| `synthetic` | `true` when rebuilt from reported values instead of a raw capture |
| `source` | where the values come from |
| `hex` | one complete link frame, upper-case pairs separated by spaces |
| `expect.cmd` | `SS/CC` service and command in hex |
| `expect.tlv` | TLV type (decimal, as string) to value hex; `""` means empty value |

Every vector must decode to exactly one payload. Raw captures from later test rounds go in
`roundN/` with `"synthetic": false`.

## Beacons (`beacon/`)

BLE service data under UUID 0xFDEE, checked by `BeaconVectorsTest` (not by the link-frame test).
Only the payload is kept: no Bluetooth addresses, device names or capture notes. `hex` is the service
data; `expect` is `null` when the parser must reject it, otherwise the decoded fields (`format`,
`modelId`, `subModelId`, `caseOpen`, and for the compact format `flags`, `lid`, `state` and the
`case`/`left`/`right` batteries as `{"percent","charging"}` or `null`).
