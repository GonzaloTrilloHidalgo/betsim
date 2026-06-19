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
| Mercados | "Mercados" en plural | **1X2** + **Goleador (marca en cualquier momento)** | Dos mercados que cubren lo más popular; el modelo queda generalizado para añadir más |
| Competición | "Ligas internacionales" | **Mundial 2026** (un solo torneo, `sport_key = soccer_fifa_world_cup`) | El torneo está en juego ahora (11 jun – 19 jul 2026); acotado y barato en API |
| API externa | API-Football *o* The Odds API | **The Odds API** (cuotas) + **fuente de resultados de goleadores** (ver §2) | The Odds API no da quién marcó; los props de jugador necesitan un proveedor de resultados |
| Liquidación 1X2 en eliminatorias | No contemplado | **Resultado de los 90 min** (la X sigue válida aunque haya prórroga/penaltis) | Es el estándar de las casas reales para el mercado 1X2 |
| Liquidación Goleador | No contemplado | **Goles en los 90 min** (no cuenta prórroga/penaltis); autogoles no cuentan al jugador | Estándar de "marca en cualquier momento" |
| Leaderboard entre amigos | No aparece | **Sí, incluido en MVP**, métrica = **beneficio neto histórico** | Es *la* función social que hace divertido jugar con amigos; premia acierto sostenido, no suerte |
| Recarga / reclamo diario | Mencionado sin reglas | **Bono diario fijo + reset manual** | Reglas simples y claras |

**Out of scope del MVP** (candidatos a v2): apuestas en vivo, mercados adicionales (over/under, hándicap,
ambos marcan, **primer/último goleador**), cash-out, notificaciones push, chat entre amigos, multi-divisa.

> **Nota sobre el mercado de goleadores:** se incluye, pero tiene dos costes reales que conviene conocer
> (detallados en §2): (1) las cuotas de *player props* en The Odds API suelen requerir **plan de pago** y
> consumen más créditos; y (2) **liquidar** un acierto de goleador exige saber **quién marcó**, dato que
> The Odds API **no** proporciona → hace falta una **segunda fuente de resultados** (p. ej. API-Football).
> Por eso lo planificamos como un **fast-follow (v1.1)**: el modelo de datos lo soporta desde el día 1,
> pero se implementa justo después de tener el 1X2 funcionando de punta a punta.

---

## 2. Presupuesto de la API externa (el riesgo nº1)

Este es el factor que más condiciona la viabilidad y el PDF lo subestima. Lo dimensionamos explícitamente.

**The Odds API** (plan gratuito ≈ 500 créditos/mes; 1 crédito ≈ 1 región × 1 mercado por petición de odds).
Competición única: **Mundial 2026** → `sport_key = soccer_fifa_world_cup` (1 sola clave deportiva).

Estimación con **un solo torneo**, refresco de cuotas cada **30 min** en ventana activa:

- Listado de eventos + odds del torneo (1 región, 1 mercado 1X2) ≈ **1 crédito/llamada**.
- Ventana realista de partidos ~10 h/día: `10 h × 2 refrescos/h ≈ 20 créditos/día`.
- A lo largo del torneo (~38 días): `20 × 38 ≈ 760 créditos`.
- Está **muy cerca/dentro** del free tier mensual y, si se afina (refrescar solo cuando hay partidos en
  las próximas 24 h), baja a la mitad. **El Mundial cabe holgadamente** frente a las 2 ligas año-redondo.

> Ventaja clave del Mundial: es un evento **acotado en el tiempo**. No hay coste recurrente indefinido; el
> grupo juega durante las ~5 semanas del torneo y listo.

**Coste añadido por el mercado de goleadores (player props):**

- En The Odds API los mercados de jugador (`player_goal_scorer_anytime`) van por un **endpoint por evento**
  (`/events/{id}/odds`) y cuestan **más créditos por llamada** que el 1X2 del listado, además de requerir
  normalmente un **plan de pago**. Estimación: refrescar props solo de partidos < 24 h, 1 vez/hora, son
  unos pocos partidos/día → manejable, pero **ya no cabe en el free tier**.
- **Resultados de goleadores**: The Odds API da el marcador (`/scores`) pero **no la lista de goleadores**.
  Para liquidar este mercado necesitamos una **segunda integración de resultados**, p. ej. **API-Football**
  (endpoint de eventos/goles del partido). Su free tier (~100 req/día) sobra para consultar los goleadores
  de unos pocos partidos finalizados al día.

> Recomendación de coste: implementar **1X2 en el free tier** primero; activar goleadores cuando decidáis
> asumir el plan de pago de odds + la segunda fuente de resultados. El `OddsProvider`/`ResultsProvider` con
> modo *mock* permite desarrollar y probar toda la lógica de goleadores **sin gastar un crédito**.

**Conclusiones / mitigaciones (siguen vigentes):**

1. Refrescar cuotas **cada 30 min** y solo de partidos que empiezan en las próximas 24 h.
2. **Diseñar una capa de abstracción `OddsProvider`** (interfaz) para poder cambiar de proveedor o mockear
   datos sin tocar la lógica de negocio. Imprescindible para tests y para desarrollar sin gastar créditos.
3. Guardar siempre la **última respuesta** en BD (no depender de la API en cada request de usuario; el
   usuario lee de *nuestra* BD, no de la API externa).
4. **Plan B sin coste**: el modo *mock* del `OddsProvider` permite desarrollar y jugar con datos simulados
   del Mundial si se agotan los créditos.

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
                                                            │  │ Scheduled Jobs (3)     │──┼──▶ OddsProvider   → The Odds API (cuotas 1X2 + props)
                                                            │  │                        │──┼──▶ ResultsProvider→ API-Football (marcador + goleadores)
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
- **Datos externos**: `OddsProvider` (The Odds API, cuotas) y `ResultsProvider` (API-Football, marcador y
  goleadores), ambos detrás de una interfaz con implementación *mock* para desarrollo sin coste.
- **Build/Dev**: Docker Compose (Postgres + backend) para levantar en local con un comando.

---

## 4. Modelo de datos

Esto es lo que más le faltaba al PDF. Diseño relacional normalizado pensado para garantizar ACID en la
billetera y soportar tickets combinados (uno-a-muchos).

### 4.1. Diagrama de entidades

```
 usuario 1───* apuesta 1───* seleccion *───1 opcion_cuota *───1 mercado *───1 partido *───1 liga
    │                                                                            │
    └──1───* transaccion                                          (marcador + goleadores al finalizar)
```

> Generalizamos a **mercado → opción**: un `partido` tiene varios `mercado` (1X2, GOLEADOR), y cada mercado
> tiene varias `opcion_cuota` (las 3 del 1X2, o un jugador por opción en goleador). Una `seleccion` apunta
> a una `opcion_cuota` y congela su cuota. Así añadir mercados nuevos = añadir filas, no columnas.

### 4.2. Tablas

**`usuario`**
| Columna | Tipo | Notas |
|---------|------|-------|
| id | BIGINT PK | |
| username | VARCHAR(30) UNIQUE NOT NULL | login |
| email | VARCHAR(120) UNIQUE NOT NULL | |
| password_hash | VARCHAR(100) NOT NULL | BCrypt |
| saldo | NUMERIC(14,2) NOT NULL | billetera; inicial 50; nunca < 0 (CHECK) |
| rol | VARCHAR(20) NOT NULL | USER / ADMIN |
| ultimo_bono | DATE | control del bono diario |
| creado_en | TIMESTAMPTZ NOT NULL | |

> `saldo` con `CHECK (saldo >= 0)` a nivel de BD: defensa final contra el descubierto.

**`liga`** *(competición — para el MVP contiene una sola fila: el Mundial 2026)*
| id | nombre | sport_key (clave externa de The Odds API, ej. `soccer_fifa_world_cup`) | pais | activa (bool) |

> Mantenemos el nombre genérico `liga`/competición para no atarnos: si tras el Mundial queréis seguir
> jugando con LaLiga o Champions, basta con añadir filas, sin cambiar el esquema.

**`partido`**
| Columna | Tipo | Notas |
|---------|------|-------|
| id | BIGINT PK | |
| liga_id | FK → liga | |
| external_id | VARCHAR UNIQUE | id del evento en la API externa (idempotencia) |
| equipo_local | VARCHAR | |
| equipo_visitante | VARCHAR | |
| inicio_utc | TIMESTAMPTZ | |
| fase | VARCHAR NULL | `GRUPOS` / `OCTAVOS` / `CUARTOS` / `SEMIS` / `FINAL` (útil para agrupar en la cartelera del Mundial) |
| estado | VARCHAR | `PROGRAMADO` / `EN_JUEGO` / `FINALIZADO` / `LIQUIDADO` |
| goles_local | INT NULL | marcador a 90 min (tiempo reglamentario); se rellena al finalizar |
| goles_visitante | INT NULL | marcador a 90 min (no contar prórroga/penaltis) |

> Las cuotas ya **no** son columnas del partido: viven en `mercado`/`opcion_cuota` (abajo).

**`mercado`** (un mercado abierto para un partido)
| Columna | Tipo | Notas |
|---------|------|-------|
| id | BIGINT PK | |
| partido_id | FK → partido | |
| tipo | VARCHAR | `1X2` / `GOLEADOR` |
| estado | VARCHAR | `ABIERTO` / `CERRADO` / `LIQUIDADO` |

> UNIQUE (`partido_id`, `tipo`): un mercado de cada tipo por partido.

**`opcion_cuota`** (cada opción apostable dentro de un mercado, con su cuota vigente)
| Columna | Tipo | Notas |
|---------|------|-------|
| id | BIGINT PK | |
| mercado_id | FK → mercado | |
| codigo | VARCHAR | `LOCAL`/`EMPATE`/`VISITANTE` (1X2) o `PLAYER:<external_id>` (goleador) |
| descripcion | VARCHAR | "Real Madrid" / "Empate" / "Lamine Yamal" (para mostrar) |
| cuota | NUMERIC(6,2) | cuota vigente (la que muestra la cartelera) |
| disponible | BOOLEAN | false si el proveedor deja de ofrecerla |
| actualizado_en | TIMESTAMPTZ | |

> UNIQUE (`mercado_id`, `codigo`): permite *upsert* de cuotas por el job sin duplicar opciones.

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
| opcion_cuota_id | FK → opcion_cuota | la opción concreta elegida (de ahí se derivan mercado y partido) |
| descripcion_snapshot | VARCHAR | copia de la descripción al apostar (ej. "Lamine Yamal — Marca") |
| cuota_congelada | NUMERIC(6,2) | **inmutable** desde la confirmación (§4.3 PDF) |
| resultado | VARCHAR | `PENDIENTE` / `ACERTADA` / `FALLADA` / `ANULADA` |

> `ANULADA` cubre el caso de que la opción/partido se cancele: esa línea pasa a cuota efectiva 1.0 y la
> combinada se recalcula (ver §10). Guardamos `descripcion_snapshot` porque las cuotas/descripciones del
> proveedor cambian; el ticket debe enseñar siempre lo que el usuario apostó.

**`partido_goleador`** (quién marcó en los 90 min — alimenta la liquidación del mercado GOLEADOR)
| Columna | Tipo | Notas |
|---------|------|-------|
| id | BIGINT PK | |
| partido_id | FK → partido | |
| player_external_id | VARCHAR | id del jugador en la fuente de resultados (API-Football) |
| nombre | VARCHAR | nombre del goleador |
| minuto | INT NULL | informativo |
| en_propia | BOOLEAN | autogol → **no** cuenta para "marca en cualquier momento" |

> UNIQUE (`partido_id`, `player_external_id`, `minuto`) para idempotencia del job de resultados.
> Un jugador presente aquí (con `en_propia = false`) hace que la opción `PLAYER:<id>` resulte `ACERTADA`.

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
| POST | `/auth/register` | Crea usuario + asigna saldo inicial (**50 monedas**) | público |
| POST | `/auth/login` | Devuelve `accessToken` (+ `refreshToken`) | público |
| POST | `/auth/refresh` | Renueva el access token | refresh token |

### 5.2. Cartelera / Partidos
| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| GET | `/matches` | Partidos `PROGRAMADO` con sus mercados/opciones, filtrable por `?liga=&desde=&hasta=` | sí |
| GET | `/matches/{id}` | Detalle de un partido (incluye mercados `1X2` y `GOLEADOR` con sus opciones) | sí |
| GET | `/matches/{id}/markets/{tipo}` | Opciones de un mercado concreto (útil para la lista larga de goleadores) | sí |
| GET | `/leagues` | Ligas activas (para filtros) | sí |

### 5.3. Apuestas
| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| POST | `/bets` | Crea ticket (simple o combinada). Valida saldo, congela cuotas, bloquea importe | sí |
| GET | `/bets?estado=PENDIENTE\|RESUELTO` | Historial del usuario (pestaña "Mis Apuestas") | sí |
| GET | `/bets/{id}` | Detalle del ticket con sus selecciones | sí |

**Cuerpo de `POST /bets`** (cada selección referencia una `opcion_cuota`, sea 1X2 o goleador):
```json
{
  "importe": 10.00,
  "selecciones": [
    { "opcionCuotaId": 102 },   // p. ej. "Real Madrid" en el mercado 1X2
    { "opcionCuotaId": 540 }    // p. ej. "Lamine Yamal — Marca" en el mercado GOLEADOR
  ]
}
```
**Validaciones del servidor (críticas):**
1. El usuario tiene `saldo >= importe`.
2. Cada opción pertenece a un mercado `ABIERTO` de un partido en estado `PROGRAMADO` (no empezado) y está `disponible`.
3. Se **re-leen las cuotas actuales de BD** (de `opcion_cuota`) y se congelan en `seleccion.cuota_congelada`
   (el cliente *no* dicta la cuota; evita manipulación). Se guarda `descripcion_snapshot`.
4. **No** se permiten dos selecciones del **mismo partido** en una combinada (eventos no independientes;
   regla estándar de casas reales y coherente con la fórmula de cuota acumulada del PDF).
5. `tipo` = `SIMPLE` si 1 selección, `COMBINADA` si ≥2.
6. Todo dentro de **una transacción** `@Transactional` con bloqueo del saldo.

### 5.4. Billetera
| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| GET | `/wallet` | Saldo actual + resumen | sí |
| GET | `/wallet/transactions` | Movimientos (para el gráfico) | sí |
| POST | `/wallet/daily-bonus` | Reclama bono diario (**+10 monedas**, 1×/día) | sí |
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
| Sincronizador de calendario | Diaria 01:00 | **Diaria 01:00** | Trae partidos del Mundial de las próximas 72 h, upsert por `external_id`; crea los `mercado` (1X2, GOLEADOR) |
| Actualizador de cuotas | Cada 15 min | **Cada 30 min** (1X2) / **cada 60 min** (goleador), solo partidos < 24 h y `PROGRAMADO` | Upsert de `opcion_cuota` por mercado; respeta presupuesto (§2) |
| Motor de liquidación | Cada 5 min | **Cada 5 min** | Busca FT, fija marcador **y goleadores**, liquida tickets `PENDIENTE` en transacción |

> El job de cuotas usa el `OddsProvider`. La parte de goleadores llama al endpoint de *player props* por
> evento (más caro, ver §2) y a menor frecuencia. El job de liquidación usa además el `ResultsProvider`
> (API-Football) para poblar `partido_goleador`.

**Idempotencia**: todos los jobs deben poder re-ejecutarse sin duplicar datos ni doble-pago
(upsert por `external_id` y por claves UNIQUE de `opcion_cuota`/`partido_goleador`; liquidar solo apuestas
en estado `PENDIENTE`; marcar partido `LIQUIDADO`).

**Algoritmo de liquidación (por partido finalizado):**
```
1. Obtener datos reales a 90 min: marcador y lista de goleadores (partido_goleador).
   IMPORTANTE (Mundial): todo se liquida con lo ocurrido en los 90 min (tiempo
   reglamentario). La X es válida aunque luego haya prórroga/penaltis, y los goles
   de la prórroga NO cuentan para el mercado de goleador. Autogol no cuenta al jugador.

2. Resolver cada seleccion de ese partido en apuestas PENDIENTE, según el tipo de su mercado:
     - Mercado 1X2:      resultadoReal = LOCAL/EMPATE/VISITANTE -> ACERTADA si la opción coincide.
     - Mercado GOLEADOR: ACERTADA si el player_external_id de la opción está en partido_goleador
                         (con en_propia = false); si no, FALLADA.
     (Si la opción/partido fue cancelado -> ANULADA, cuota efectiva 1.0.)

3. Por cada apuesta afectada, recalcular estado:
     - si alguna seleccion FALLADA  -> apuesta PERDIDA.
     - si TODAS las selecciones están resueltas y ninguna FALLADA -> GANADA:
         retorno = importe × ∏(cuota_congelada de las no anuladas; las ANULADA cuentan como 1.0).
         acreditar retorno al saldo + registrar transaccion PREMIO.
     - si quedan selecciones PENDIENTE (otros partidos sin terminar) -> sigue PENDIENTE.

4. Marcar mercados del partido como LIQUIDADO y el partido como LIQUIDADO.
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
  1. **Cartelera** — tarjetas de partido con los 3 botones de cuota 1/X/2 y un acceso **"Goleadores"**
     que abre la lista de jugadores con su cuota (mercado GOLEADOR); filtros horizontales por liga.
  2. **Mis Apuestas** — segmentado Pendientes / Resueltos (cada línea muestra `descripcion_snapshot`).
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
- Saldo inicial (50 monedas), tabla `transaccion`, endpoints `/wallet/*`, bono diario (+10/día), reset.

### Fase 2 — Datos deportivos
- Interfaz `OddsProvider` + implementación The Odds API + **mock** para desarrollo.
- Modelo `mercado`/`opcion_cuota`. Job de calendario + job de cuotas (mercado 1X2).
- Endpoints `/matches`, `/leagues`.

### Fase 3 — Apuestas (núcleo)
- `POST /bets` con validación, congelación de cuotas y bloqueo de saldo (ACID).
- Historial `/bets`. Bet Slip en frontend.

### Fase 4 — Liquidación 1X2
- Job de liquidación + algoritmo de settlement (mercado 1X2) + acreditación de premios.

### Fase 5 — Social y pulido
- Leaderboard, gráfico de billetera, refinamiento UX móvil, tests de integración.

### Fase 6 — Mercado de Goleadores (fast-follow v1.1)
- Cuotas de *player props* en el `OddsProvider` (plan de pago) + opciones `PLAYER:*` en el mercado GOLEADOR.
- `ResultsProvider` (API-Football) + tabla `partido_goleador` + extensión del settlement a GOLEADOR.
- UI de lista de goleadores en la cartelera.

> Cada fase es desplegable y probable de forma independiente. Recomiendo cerrar **Fase 1–4 como MVP jugable**
> (solo 1X2, todo en free tier) y abordar la **Fase 6 (goleadores)** cuando decidáis asumir el coste de
> API. El modelo de datos ya soporta goleadores desde la Fase 2, así que no hay repintado.

---

## 10. Decisiones tomadas y cabos sueltos

### Decididas ✅
- **Competición**: **Mundial 2026** (un solo torneo, `soccer_fifa_world_cup`). Acotado en el tiempo y
  barato en API. Tras el torneo se puede ampliar a ligas sin tocar el esquema.
- **Métrica del leaderboard**: **beneficio neto histórico** (suma de premios − suma de importes apostados).
- **Liquidación 1X2 en eliminatorias**: por marcador de **90 min** (la X es válida pese a prórroga/penaltis).
- **Economía**: saldo inicial **50 monedas**; **bono diario fijo de +10 monedas** reclamable 1 vez cada 24 h
  (sin condición de saldo mínimo).
- **Mercados**: **1X2** (MVP) + **Goleador "marca en cualquier momento"** (fast-follow v1.1). Modelo de
  datos generalizado a `mercado`/`opcion_cuota` desde el inicio. Goleador se liquida a 90 min, autogol no cuenta.

### Pendientes de confirmar
1. **Coste del mercado de goleadores**: requiere **plan de pago** en The Odds API (player props) + una
   **segunda fuente de resultados** (API-Football) para saber quién marcó. ¿Asumimos ese coste o lo dejamos
   tras validar el MVP con 1X2? (recomendado: validar primero con 1X2 gratis).
2. **Empates/anulaciones**: si un partido se cancela/aplaza en el mundo real, ¿se anula la selección y se
   recalcula la combinada con cuota 1.0? (estándar en casas reales). Recomiendo soportarlo.
3. **¿Hosting?** Para jugar con amigos: backend + Postgres en un VPS pequeño o Railway/Render; PWA en
   el mismo backend o en Netlify/Vercel.
4. **Bonus opcional Mundial**: ¿quieres un mercado extra típico de torneo (ej. "ganador del grupo" o
   "campeón del Mundial") en v2? No es MVP, pero es muy social. Lo dejo anotado.
```
