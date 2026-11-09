# Hardware protocol

## Transport

The current app implements a TCP client. Configure the robot host and port in Connections or save a TCP robot profile. Messages are UTF-8 JSON objects, one object per line. The app reads one line at a time. A future firmware adapter can use the same framing over another byte stream.

## App-to-robot commands

Motor speed is an integer from -255 to 255. Motor IDs must be positive. The current directional UI sends two motor commands, with IDs 1 and 2.

```json
{"type":"motor","id":1,"speed":120}
{"type":"motor","id":2,"speed":120}
{"type":"stop"}
```

Servo commands are supported by the core codec and custom-button editor, with positive IDs and angles from 0 to 180:

```json
{"type":"servo","id":1,"angle":90}
```

The Controller screen can save custom on-screen buttons for validated motor, servo, or stop commands. Buttons send the same JSON objects above; arbitrary command types are rejected. A stop button remains available when manual motion is emergency-stop-locked, while motion/servo buttons are gated.

## Robot-to-app messages

Telemetry requires a non-empty sensor ID and finite numeric value:

```json
{"type":"telemetry","sensor":"range","value":12.5}
```

An acknowledgement may include an optional message:

```json
{"type":"ack","message":"motor command accepted"}
```

Malformed JSON, unsupported message types, and invalid command values are rejected by the codec. The current UI records received lines in the terminal; robot telemetry is not yet mapped into named charts or persisted as profile-specific rules.

## Safety and security

The TCP channel is unencrypted and has no authentication. Use it only on a trusted network. Add firmware-side authentication before exposing a robot to an untrusted network. The app stop command is best-effort and cannot replace an independent hardware emergency-stop.
