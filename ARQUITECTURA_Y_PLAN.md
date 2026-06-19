# BetSim — Documento de Arquitectura y Plan de Implementación

> Documento técnico derivado de la especificación `betsim_especificaciones_pwa.pdf` (v2.0, Junio 2026).
> Su objetivo es convertir la *visión* del PDF en un *plan ejecutable*: modelo de datos, contrato de API,
> alcance de MVP y decisiones de arquitectura concretas.

---

## 1. Decisiones de alcance (lo primero)

Antes de cualquier diseño, fijamos el alcance para no construir de más. La especificación es ambiciosa;
el caso de uso real es **"jugar con amigos"**. Eso cambia varias prioridades.

| Tema | Especificación PDF | Decisión para el MVP | Motivo |
|------|--------------------|-----------------------|--------|
| Tipos de apuesta | Simples (1X2) + Combinadas | **Ambas**, mercado único **1X2** | Es el núcleo del producto |
| Apuestas en vivo (live) | Ambiguo | **Solo pre-partido** | Live multiplica la complejidad (latencia, settlement parcial) |
| Mercados | "Mercados" en plural | **Solo 1X2** (1=local, X=empate, 2=visitante) | Un mercado bien hecho > cinco a medias |
| Ligas | "Ligas internacionales" | **1–2 ligas** configurables | Controlar el coste de la API externa |
| API externa | API-Football *o* The Odds API | **The Odds API** (una sola) | Las cuotas son el centro; su free tier es más generoso para odds |
| Leaderboard entre amigos | No aparece | **Sí, incluido en MVP** | Es *la* función social que hace divertido jugar con amigos |
| Recarga / reclamo diario | Mencionado sin reglas | **Bono diario fijo + reset manual** | Reglas simples y claras |

**Out of scope del MVP** (candidatos a v2): apuestas en vivo, mercados adicionales (over/under, hándicap,
ambos marcan), cash-out, notificaciones push, chat entre amigos, multi-divisa.

---

## 2. Presupuesto de la API externa (el riesgo nº1)

Este es el factor que más condiciona la viabilidad y el PDF lo subestima. Lo dimensionamos explícitamente.

**The Odds API** (plan gratuito ≈ 500 créditos/mes; 1 crédito ≈ 1 región × 1 mercado por petición de odds).

Estimación con **2 ligas**, refresco de cuotas cada **15 min** solo en ventana activa:

- Listado de eventos + odds de una liga (1 región, 1 mercado) ≈ **1 crédito/llamada**.
- Si refrescamos solo en una ventana de ~12 h/día (no de madrugada): `12 h × 4 refrescos/h × 2 ligas ≈ 96 créditos/día`.
- Eso son **~2.880 créditos/mes** → **excede el free tier**.

**Conclusiones / mitigaciones:**

1. El refresco "cada 15 min sobre todos los partidos" del PDF **no entra en el plan gratuito**. Opciones:
   - Refrescar cuotas **cada 30–60 min** y solo de partidos que empiezan en las próximas 24–48 h.
   - Cachear agresivamente; no llamar si no hay partidos en ventana.
   - Plan de pago (~20.000 créditos/mes) si el grupo de amigos crece.
2. **Diseñar una capa de abstracción `OddsProvider`** (interfaz) para poder cambiar de proveedor o mockear
   datos sin tocar la lógica de negocio. Imprescindible para tests y para desarrollar sin gastar créditos.
3. Guardar siempre la **última respuesta** en BD (no depender de la API en cada request de usuario; el
   usuario lee de *nuestra* BD, no de la API externa).

> Regla de oro: **el usuario nunca pega contra la API externa**. Solo nuestros jobs `@Scheduled` lo hacen.

---

## 3. Arquitectura general

```
┌──────────────────────────┐         HTTPS / JSON          ┌─────────────────────────────┐
│        PWA (cliente)      │  ───────────────────────────▶ │     Spring Boot API REST     │
│  HTML5 + Tailwind + JS    │  ◀─────────────────────────── │                              │
│  manifest.json + sw.js    │      JWT (Bearer token)       │  ┌────────────────────────┐  │
│  Bottom Sheet / Bottom Nav│                               │  │ Controllers (REST)     │  │
└──────────────────────────┘                               │  ├────────────────────────┤  │
                                                            │  │ Services (lógica/ACID) │  │
                                                            │  ├────────────────────────┤  │
                                                            │  │ Scheduled Jobs (3)     │──┼──▶ The Odds API
                                                            │  ├────────────────────────┤  │
                                                            │  │ Repositories (JPA)     │  │
                                                            │  └────────────────────────┘  │
                                                            └──────────────┬───────────────┘
                                                                           │
                                                                  ┌────────▼────────┐
                                                                  │   PostgreSQL    │
                                                                  └─────────────────┘
```

### Stack confirmado

- **Backend**: Java 21, Spring Boot 3.x (Web, Data JPA, Security, Validation), Flyway (migraciones).
- **BD**: PostgreSQL 16.
- **Frontend**: HTML5 + Tailwind CSS + JavaScript vanilla (sin framework pesado; el PDF lo pide así).
  PWA con `manifest.json` + Service Worker (estrategia *App Shell*).
- **Auth**: JWT (access token; ver §7 para refresh).
- **Build/Dev**: Docker Compose (Postgres + backend) para levantar en local con un comando.

---

## 4. Modelo de datos

Esto es lo que más le faltaba al PDF. Diseño relacional normalizado pensado para garantizar ACID en la
billetera y soportar tickets combinados (uno-a-muchos).

### 4.1. Diagrama de entidades

```
 usuario 1───* apuesta 1───* seleccion *───1 partido *───1 liga
    │                                              │
    └──1───* transaccion                           └── (estado, marcador, cuotas congeladas en seleccion)
```

### 4.2. Tablas

**`usuario`**
| Columna | Tipo | Notas |
|---------|------|-------|
| id | BIGINT PK | |
| username | VARCHAR(30) UNIQUE NOT NULL | login |
| email | VARCHAR(120) UNIQUE NOT NULL | |
| password_hash | VARCHAR(100) NOT NULL | BCrypt |
| saldo | NUMERIC(14,2) NOT NULL | billetera; nunca < 0 (CHECK) |
| rol | VARCHAR(20) NOT NULL | USER / ADMIN |
| ultimo_bono | DATE | control del bono diario |
| creado_en | TIMESTAMPTZ NOT NULL | |

> `saldo` con `CHECK (saldo >= 0)` a nivel de BD: defensa final contra el descubierto.

**`liga`**
| id | nombre | sport_key (clave externa de The Odds API) | pais | activa (bool) |

**`partido`**
| Columna | Tipo | Notas |
|---------|------|-------|
| id | BIGINT PK | |
| liga_id | FK → liga | |
| external_id | VARCHAR UNIQUE | id del evento en la API externa (idempotencia) |
| equipo_local | VARCHAR | |
| equipo_visitante | VARCHAR | |
| inicio_utc | TIMESTAMPTZ | |
| estado | VARCHAR | `PROGRAMADO` / `EN_JUEGO` / `FINALIZADO` / `LIQUIDADO` |
| goles_local | INT NULL | se rellena al finalizar |
| goles_visitante | INT NULL | |
| cuota_1 | NUMERIC(6,2) | cuota local vigente |
| cuota_x | NUMERIC(6,2) | cuota empate vigente |
| cuota_2 | NUMERIC(6,2) | cuota visitante vigente |
| cuotas_actualizado_en | TIMESTAMPTZ | |

**`apuesta`** (el ticket)
| Columna | Tipo | Notas |
|---------|------|-------|
| id | BIGINT PK | |
| usuario_id | FK → usuario | |
| tipo | VARCHAR | `SIMPLE` / `COMBINADA` |
| importe | NUMERIC(14,2) | lo apostado (bloqueado del saldo) |
| cuota_total | NUMERIC(12,2) | producto de cuotas congeladas |
| retorno_potencial | NUMERIC(14,2) | importe × cuota_total |
| estado | VARCHAR | `PENDIENTE` / `GANADA` / `PERDIDA` / `ANULADA` |
| creado_en | TIMESTAMPTZ | |
| resuelto_en | TIMESTAMPTZ NULL | |

**`seleccion`** (cada línea de un ticket)
| Columna | Tipo | Notas |
|---------|------|-------|
| id | BIGINT PK | |
| apuesta_id | FK → apuesta | |
| partido_id | FK → partido | |
| pronostico | VARCHAR | `LOCAL` / `EMPATE` / `VISITANTE` |
| cuota_congelada | NUMERIC(6,2) | **inmutable** desde la confirmación (§4.3 PDF) |
| resultado | VARCHAR | `PENDIENTE` / `ACERTADA` / `FALLADA` |

**`transaccion`** (auditoría de movimientos de saldo)
| id | usuario_id FK | apuesta_id FK NULL | tipo (`BONO_INICIAL`/`APUESTA`/`PREMIO`/`BONO_DIARIO`/`RESET`) | importe (+/-) | saldo_resultante | creado_en |

> La tabla `transaccion` da trazabilidad completa de la billetera y permite reconstruir el saldo y
> dibujar el "gráfico de rendimiento de saldo" que pide el PDF en la pestaña Billetera.

### 4.3. Máquina de estados del partido

```
PROGRAMADO ──(job cuotas: kickoff alcanzado)──▶ EN_JUEGO ──(job liquidación: FT)──▶ FINALIZADO ──(settlement OK)──▶ LIQUIDADO
```

Toda la liquidación de apuestas cuelga de la transición `FINALIZADO → LIQUIDADO`.

---

## 5. Contrato de la API REST

Prefijo: `/api/v1`. Autenticación JWT salvo donde se indique *(público)*.

### 5.1. Autenticación
| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| POST | `/auth/register` | Crea usuario + asigna saldo inicial (1.000) | público |
| POST | `/auth/login` | Devuelve `accessToken` (+ `refreshToken`) | público |
| POST | `/auth/refresh` | Renueva el access token | refresh token |

### 5.2. Cartelera / Partidos
| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| GET | `/matches` | Partidos `PROGRAMADO` con cuotas, filtrable por `?liga=&desde=&hasta=` | sí |
| GET | `/matches/{id}` | Detalle de un partido | sí |
| GET | `/leagues` | Ligas activas (para filtros) | sí |

### 5.3. Apuestas
| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| POST | `/bets` | Crea ticket (simple o combinada). Valida saldo, congela cuotas, bloquea importe | sí |
| GET | `/bets?estado=PENDIENTE\|RESUELTO` | Historial del usuario (pestaña "Mis Apuestas") | sí |
| GET | `/bets/{id}` | Detalle del ticket con sus selecciones | sí |

**Cuerpo de `POST /bets`:**
```json
{
  "importe": 50.00,
  "selecciones": [
    { "partidoId": 12, "pronostico": "LOCAL" },
    { "partidoId": 19, "pronostico": "EMPATE" }
  ]
}
```
**Validaciones del servidor (críticas):**
1. El usuario tiene `saldo >= importe`.
2. Todos los partidos están en estado `PROGRAMADO` (no empezados).
3. Se **re-leen las cuotas actuales de BD** y se congelan en `seleccion.cuota_congelada`
   (el cliente *no* dicta la cuota; evita manipulación).
4. `tipo` = `SIMPLE` si 1 selección, `COMBINADA` si ≥2.
5. Todo dentro de **una transacción** `@Transactional` con bloqueo del saldo.

### 5.4. Billetera
| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| GET | `/wallet` | Saldo actual + resumen | sí |
| GET | `/wallet/transactions` | Movimientos (para el gráfico) | sí |
| POST | `/wallet/daily-bonus` | Reclama bono diario (1×/día) | sí |
| POST | `/wallet/reset` | Reinicia saldo al valor inicial | sí |

### 5.5. Social (lo que añade valor para "jugar con amigos")
| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| GET | `/leaderboard` | Ranking de usuarios por saldo / beneficio neto | sí |

### 5.6. Admin (opcional MVP)
| POST | `/admin/sync` | Forzar sincronización manual de calendario/cuotas/liquidación | ADMIN |

---

## 6. Tareas programadas (`@Scheduled`)

Conforme al PDF, pero ajustadas al presupuesto de API (§2).

| Job | Frecuencia PDF | Frecuencia recomendada | Función |
|-----|----------------|------------------------|---------|
| Sincronizador de calendario | Diaria 01:00 | **Diaria 01:00** | Trae partidos de próximas 72 h, upsert por `external_id` |
| Actualizador de cuotas | Cada 15 min | **Cada 30–60 min**, solo partidos < 48 h y `PROGRAMADO` | Refresca `cuota_1/x/2`; respeta presupuesto |
| Motor de liquidación | Cada 5 min | **Cada 5 min** | Busca FT, fija marcador, liquida tickets `PENDIENTE` en transacción |

**Idempotencia**: todos los jobs deben poder re-ejecutarse sin duplicar datos ni doble-pago
(upsert por `external_id`; liquidar solo apuestas en estado `PENDIENTE`; marcar partido `LIQUIDADO`).

**Algoritmo de liquidación (por partido finalizado):**
```
1. Determinar resultado real: LOCAL / EMPATE / VISITANTE según marcador.
2. Por cada seleccion de ese partido en apuestas PENDIENTE:
     resultado = ACERTADA si pronostico == resultadoReal, si no FALLADA.
3. Por cada apuesta afectada, recalcular estado:
     - si alguna seleccion FALLADA  -> apuesta PERDIDA.
     - si TODAS las selecciones (de todos sus partidos) ACERTADAS -> GANADA:
         acreditar retorno_potencial al saldo + registrar transaccion PREMIO.
     - si quedan selecciones PENDIENTE (otros partidos sin terminar) -> sigue PENDIENTE.
4. Marcar partido como LIQUIDADO.
   Todo en una @Transactional.
```

---

## 7. Seguridad

- **Passwords**: BCrypt.
- **JWT**: access token corto (15 min) + **refresh token** (no estaba en el PDF; lo añado).
  El PDF propone `localStorage`; es vulnerable a XSS. Para "jugar con amigos" es aceptable, pero
  recomiendo al menos: tokens de vida corta, refresh token en cookie `HttpOnly` si se quiere endurecer.
- **Autorización**: Spring Security con filtro JWT; rutas `/auth/**` públicas, resto autenticadas; `/admin/**` rol ADMIN.
- **Anti-trampa**: la cuota y el resultado **siempre los decide el servidor** desde BD, nunca el cliente.
- **CORS**: configurado para el origen de la PWA.
- **Rate limiting** básico en `/auth/login` (fuerza bruta).

---

## 8. Frontend PWA

- **3 pestañas** (Bottom Navigation, zonas táctiles ≥ 44×44 px):
  1. **Cartelera** — tarjetas de partido con 3 botones de cuota (1/X/2), filtros horizontales por liga.
  2. **Mis Apuestas** — segmentado Pendientes / Resueltos.
  3. **Billetera** — saldo, gráfico de evolución, bono diario, reset.
- **Bet Slip flotante** (Bottom Sheet): se actualiza al pulsar cuotas, permite importe y confirmación
  sin salir de la cartelera. Calcula cuota total y retorno potencial en cliente (visual), pero el
  servidor recalcula al confirmar.
- **PWA**: `manifest.json` (`display: standalone`, orientación `portrait`, iconos 192/512),
  `sw.js` con estrategia App Shell (cache-first para estáticos, network-first para datos).
- **Leaderboard** accesible desde la Billetera o como vista secundaria.

---

## 9. Plan de implementación por fases

### Fase 0 — Cimientos (esqueleto)
- Repo, Docker Compose (Postgres), proyecto Spring Boot, Flyway, configuración base.
- Esqueleto de la PWA (HTML, Tailwind, manifest, service worker mínimo).

### Fase 1 — Auth y billetera
- Registro/login/refresh con JWT.
- Saldo inicial, tabla `transaccion`, endpoints `/wallet/*`, bono diario, reset.

### Fase 2 — Datos deportivos
- Interfaz `OddsProvider` + implementación The Odds API + **mock** para desarrollo.
- Job de calendario + job de cuotas. Endpoints `/matches`, `/leagues`.

### Fase 3 — Apuestas (núcleo)
- `POST /bets` con validación, congelación de cuotas y bloqueo de saldo (ACID).
- Historial `/bets`. Bet Slip en frontend.

### Fase 4 — Liquidación
- Job de liquidación + algoritmo de settlement + acreditación de premios.

### Fase 5 — Social y pulido
- Leaderboard, gráfico de billetera, refinamiento UX móvil, tests de integración.

> Cada fase es desplegable y probable de forma independiente. Recomiendo cerrar Fase 1–4 como MVP jugable.

---

## 10. Cabos sueltos / decisiones pendientes de confirmar

1. **¿1 o 2 ligas para empezar?** (impacta coste API). Sugerencia: 1 (LaLiga o Premier).
2. **Reglas del bono diario**: ¿cuánto y cada cuánto? Sugerencia: +500 cada 24 h si saldo < 200.
3. **Empates/anulaciones**: si un partido se cancela en el mundo real, ¿se anula la selección y se
   recalcula la combinada con cuota 1.0? (estándar en casas reales). Recomiendo soportarlo.
4. **¿Hosting?** Para jugar con amigos: backend + Postgres en un VPS pequeño o Railway/Render; PWA en
   el mismo backend o en Netlify/Vercel.
5. **Métrica del leaderboard**: ¿saldo actual o beneficio neto histórico? Sugerencia: beneficio neto.
```
