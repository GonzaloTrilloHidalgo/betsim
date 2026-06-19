# BetSim ⚽💰

Simulador de apuestas deportivas (Mundial 2026) con **moneda ficticia**, para jugar con amigos.
PWA mobile-first + API REST con Spring Boot. MVP centrado en el mercado **1X2**.

> Diseño completo en [`ARQUITECTURA_Y_PLAN.md`](./ARQUITECTURA_Y_PLAN.md).

## Qué incluye el MVP

- **Auth** con JWT (registro / login / refresh).
- **Billetera**: saldo inicial 50 monedas, bono diario +10, reset, historial de movimientos.
- **Cartelera** del Mundial con cuotas 1X2 (1 / X / 2).
- **Apuestas** simples y combinadas, con congelación de cuota y bloqueo de saldo (transacción ACID).
- **Liquidación automática** (jobs `@Scheduled`) al finalizar los partidos (resultado a 90 min).
- **Leaderboard** por beneficio neto histórico.
- **PWA**: instalable, `manifest.json` + Service Worker (App Shell).
- **Proveedor de datos `mock`** por defecto: funciona sin API key ni coste.

## Arquitectura

```
frontend/  PWA (HTML + Tailwind + JS vanilla)  ->  backend/  Spring Boot + JPA + Flyway  ->  PostgreSQL
                                                        └─ SportsDataProvider (mock | The Odds API)
```

## Arranque rápido

### Opción A — Docker Compose (todo en uno)

```bash
docker compose up --build
```

- Frontend: http://localhost:5500
- API: http://localhost:8080/api/v1/health

### Opción B — Local sin Docker (perfil `dev`, base de datos H2 en memoria)

```bash
# Backend
cd backend
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run     # http://localhost:8080

# Frontend (en otra terminal)
cd frontend
python3 -m http.server 5500                        # http://localhost:5500
```

Abre http://localhost:5500, crea una cuenta y empieza a apostar. El proveedor `mock` genera
un cuadro de partidos del Mundial alrededor del arranque y liquida solo los que ya han terminado.

## Usar datos reales (The Odds API)

```bash
export BETSIM_PROVIDER=theoddsapi
export ODDS_API_KEY=tu_api_key   # https://the-odds-api.com
```

> Ojo al presupuesto del free tier (ver §2 del documento de arquitectura). El mercado de goleadores
> queda planificado como v1.1 (requiere player props de pago + fuente de resultados de goleadores).

## Tests

```bash
cd backend && mvn test
```

Cubren el camino crítico de liquidación: una apuesta ganadora acredita el premio; una perdedora no.

## Endpoints principales (`/api/v1`)

| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/auth/register` · `/auth/login` · `/auth/refresh` | Autenticación |
| GET  | `/matches` · `/matches/{id}` · `/leagues` | Cartelera |
| POST | `/bets` · GET `/bets` · GET `/bets/{id}` | Apuestas |
| GET  | `/wallet` · `/wallet/transactions` · POST `/wallet/daily-bonus` · `/wallet/reset` | Billetera |
| GET  | `/leaderboard` | Ranking |
| POST | `/admin/sync` | Sincronización manual (rol ADMIN) |
