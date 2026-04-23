# Connectivity Service poc (Spring Boot 4.0.1)

poc di microservizio connectivity con API:
- `POST /api/v1/connectivity/connect`
- `POST /api/v1/connectivity/disconnect`
- `POST /api/v1/connectivity/reconnect`

## architettura

- `controller`: esposizione API REST e gestione errori
- `service`: business logic connect/disconnect/reconnect
- `model`: request/response e modelli di dominio
- `repository`: astrazione dello stato delle sessioni socket

## esempio payload

```json
{
  "ip": "192.168.1.10",
  "port": 5001,
  "subsystem": "PAYLOAD",
  "protocol": "MQTT"
}
```
