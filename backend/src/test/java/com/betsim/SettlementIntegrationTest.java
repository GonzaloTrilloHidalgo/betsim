package com.betsim;

import com.betsim.api.BetService;
import com.betsim.domain.*;
import com.betsim.domain.Enums.*;
import com.betsim.provider.ProviderDtos.ProviderResult;
import com.betsim.provider.SportsDataProvider;
import com.betsim.repository.*;
import com.betsim.scheduler.SettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifica el camino crítico: una apuesta ganadora acredita el retorno y queda GANADA;
 * una perdedora descuenta y queda PERDIDA. El proveedor se mockea para controlar el resultado.
 */
@SpringBootTest
@ActiveProfiles("dev")
class SettlementIntegrationTest {

    @MockBean SportsDataProvider provider;

    @Autowired BetService betService;
    @Autowired SettlementService settlement;
    @Autowired UsuarioRepository usuarios;
    @Autowired LigaRepository ligas;
    @Autowired PartidoRepository partidos;
    @Autowired MercadoRepository mercados;
    @Autowired OpcionCuotaRepository opciones;
    @Autowired ApuestaRepository apuestas;

    @Test
    void apuestaGanadoraAcreditaElRetorno() {
        when(provider.fetchUpcomingMatches()).thenReturn(List.of());

        Usuario u = nuevoUsuario("ganador");
        Long localId = crearPartidoConMercado("WIN-1", "España", "Alemania", new BigDecimal("2.00"));

        // Apuesta simple a LOCAL por 10 (cuota 2.0 -> retorno 20).
        Apuesta a = betService.crear(u.getId(), new BigDecimal("10.00"), List.of(localId));
        assertThat(usuarios.findById(u.getId()).orElseThrow().getSaldo())
                .isEqualByComparingTo("40.00"); // 50 - 10

        // El partido termina 2-0 (gana LOCAL). Simulamos paso del tiempo y resultado.
        marcarComoEmpezado("WIN-1");
        when(provider.fetchResult(any())).thenReturn(Optional.of(new ProviderResult("WIN-1", 2, 0)));

        settlement.run();

        Apuesta resuelta = apuestas.findById(a.getId()).orElseThrow();
        assertThat(resuelta.getEstado()).isEqualTo(EstadoApuesta.GANADA);
        assertThat(usuarios.findById(u.getId()).orElseThrow().getSaldo())
                .isEqualByComparingTo("60.00"); // 40 + 20 de premio
    }

    @Test
    void apuestaPerdedoraNoAcredita() {
        when(provider.fetchUpcomingMatches()).thenReturn(List.of());

        Usuario u = nuevoUsuario("perdedor");
        Long localId = crearPartidoConMercado("LOSE-1", "Italia", "Croacia", new BigDecimal("2.00"));

        Apuesta a = betService.crear(u.getId(), new BigDecimal("10.00"), List.of(localId));

        marcarComoEmpezado("LOSE-1");
        when(provider.fetchResult(any())).thenReturn(Optional.of(new ProviderResult("LOSE-1", 0, 1))); // gana visitante

        settlement.run();

        Apuesta resuelta = apuestas.findById(a.getId()).orElseThrow();
        assertThat(resuelta.getEstado()).isEqualTo(EstadoApuesta.PERDIDA);
        assertThat(usuarios.findById(u.getId()).orElseThrow().getSaldo())
                .isEqualByComparingTo("40.00"); // sigue 50 - 10, sin premio
    }

    private Usuario nuevoUsuario(String name) {
        Usuario u = new Usuario();
        u.setUsername(name);
        u.setEmail(name + "@test.com");
        u.setPasswordHash("x");
        u.setSaldo(new BigDecimal("50.00"));
        return usuarios.save(u);
    }

    /** Crea partido (kickoff futuro) + mercado 1X2 con 3 opciones; devuelve el id de la opción LOCAL. */
    private Long crearPartidoConMercado(String ext, String local, String visitante, BigDecimal cuotaLocal) {
        Liga liga = ligas.findBySportKey("soccer_fifa_world_cup").orElseThrow();
        Partido p = new Partido();
        p.setLiga(liga);
        p.setExternalId(ext);
        p.setEquipoLocal(local);
        p.setEquipoVisitante(visitante);
        p.setInicioUtc(Instant.now().plus(2, ChronoUnit.HOURS));
        p.setEstado(EstadoPartido.PROGRAMADO);
        partidos.save(p);

        Mercado m = mercados.save(new Mercado(p, TipoMercado.UNO_X_DOS));
        Long localId = opcion(m, "LOCAL", local, cuotaLocal).getId();
        opcion(m, "EMPATE", "Empate", new BigDecimal("3.00"));
        opcion(m, "VISITANTE", visitante, new BigDecimal("3.50"));
        return localId;
    }

    private OpcionCuota opcion(Mercado m, String codigo, String desc, BigDecimal cuota) {
        OpcionCuota o = new OpcionCuota();
        o.setMercado(m);
        o.setCodigo(codigo);
        o.setDescripcion(desc);
        o.setCuota(cuota);
        return opciones.save(o);
    }

    private void marcarComoEmpezado(String ext) {
        Partido p = partidos.findByExternalId(ext).orElseThrow();
        p.setInicioUtc(Instant.now().minus(2, ChronoUnit.HOURS));
        partidos.save(p);
    }

    @Test
    void mercadosNuevosSeLiquidanSegunSuTipo() {
        when(provider.fetchUpcomingMatches()).thenReturn(List.of());

        Usuario u = nuevoUsuario("mercados");
        Liga liga = ligas.findBySportKey("soccer_fifa_world_cup").orElseThrow();
        Partido p = new Partido();
        p.setLiga(liga);
        p.setExternalId("MKT-1");
        p.setEquipoLocal("Brasil");
        p.setEquipoVisitante("Haití");
        p.setInicioUtc(Instant.now().plus(2, ChronoUnit.HOURS));
        p.setEstado(EstadoPartido.PROGRAMADO);
        partidos.save(p);

        // Opciones que DEBEN ganar con un resultado 2-1 (local gana, 3 goles, ambos marcan).
        Mercado mou = mercados.save(new Mercado(p, TipoMercado.OVER_UNDER));
        Long overId = opcionLinea(mou, "OVER", "Más de 2.5 goles", new BigDecimal("1.90"), new BigDecimal("2.5")).getId();
        opcionLinea(mou, "UNDER", "Menos de 2.5 goles", new BigDecimal("1.90"), new BigDecimal("2.5"));

        Mercado mdc = mercados.save(new Mercado(p, TipoMercado.DOBLE_OPORTUNIDAD));
        Long dc1xId = opcion(mdc, "1X", "Brasil o Empate", new BigDecimal("1.30")).getId();

        Mercado mbtts = mercados.save(new Mercado(p, TipoMercado.AMBOS_MARCAN));
        Long bttsSiId = opcion(mbtts, "BTTS_SI", "Ambos marcan: Sí", new BigDecimal("1.80")).getId();

        Apuesta over = betService.crear(u.getId(), new BigDecimal("10.00"), List.of(overId));
        Apuesta dc = betService.crear(u.getId(), new BigDecimal("10.00"), List.of(dc1xId));
        Apuesta btts = betService.crear(u.getId(), new BigDecimal("10.00"), List.of(bttsSiId));

        marcarComoEmpezado("MKT-1");
        when(provider.fetchResult(any())).thenReturn(Optional.of(new ProviderResult("MKT-1", 2, 1)));
        settlement.run();

        assertThat(apuestas.findById(over.getId()).orElseThrow().getEstado()).isEqualTo(EstadoApuesta.GANADA);
        assertThat(apuestas.findById(dc.getId()).orElseThrow().getEstado()).isEqualTo(EstadoApuesta.GANADA);
        assertThat(apuestas.findById(btts.getId()).orElseThrow().getEstado()).isEqualTo(EstadoApuesta.GANADA);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void seleccionFinalizadaSeResuelveAunqueLaCombinadaSigaPendiente() {
        when(provider.fetchUpcomingMatches()).thenReturn(List.of());
        Usuario u = nuevoUsuario("combicolor");
        Long a = crearPartidoConMercado("CMB-A", "España", "Italia", new BigDecimal("2.00"));
        Long b = crearPartidoConMercado("CMB-B", "Brasil", "Chile", new BigDecimal("2.00"));

        Apuesta bet = betService.crear(u.getId(), new BigDecimal("10.00"), List.of(a, b));
        assertThat(bet.getEstado()).isEqualTo(EstadoApuesta.PENDIENTE);

        // Solo termina el partido A (España gana 2-0); B sigue por jugarse.
        marcarComoEmpezado("CMB-A");
        when(provider.fetchResult("CMB-A")).thenReturn(Optional.of(new ProviderResult("CMB-A", 2, 0)));
        when(provider.fetchResult("CMB-B")).thenReturn(Optional.empty());
        settlement.run();

        Apuesta r = apuestas.findById(bet.getId()).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoApuesta.PENDIENTE); // la combinada sigue viva
        for (Seleccion s : r.getSelecciones()) {
            String ext = s.getOpcionCuota().getMercado().getPartido().getExternalId();
            if (ext.equals("CMB-A")) assertThat(s.getResultado()).isEqualTo(ResultadoSeleccion.ACERTADA);
            else assertThat(s.getResultado()).isEqualTo(ResultadoSeleccion.PENDIENTE);
        }
    }

    private OpcionCuota opcionLinea(Mercado m, String codigo, String desc, BigDecimal cuota, BigDecimal linea) {
        OpcionCuota o = opcion(m, codigo, desc, cuota);
        o.setLinea(linea);
        return opciones.save(o);
    }
}
