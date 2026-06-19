package com.betsim.api;

import com.betsim.domain.Apuesta;
import com.betsim.domain.Enums.EstadoApuesta;
import com.betsim.domain.Seleccion;
import com.betsim.repository.ApuestaRepository;
import com.betsim.security.JwtAuthFilter.AuthPrincipal;
import com.betsim.web.ApiException;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bets")
public class BetController {

    private final BetService betService;
    private final ApuestaRepository apuestas;

    public BetController(BetService betService, ApuestaRepository apuestas) {
        this.betService = betService;
        this.apuestas = apuestas;
    }

    public record SelectionRequest(@NotNull Long opcionCuotaId) {}
    public record CreateBetRequest(
            @NotNull @Positive BigDecimal importe,
            @NotEmpty List<SelectionRequest> selecciones) {}

    public record SelectionView(String descripcion, BigDecimal cuota, String resultado) {}
    public record BetView(Long id, String tipo, BigDecimal importe, BigDecimal cuotaTotal,
                          BigDecimal retornoPotencial, String estado, Instant creadoEn, Instant resueltoEn,
                          List<SelectionView> selecciones) {}

    @PostMapping
    public BetView create(@AuthenticationPrincipal AuthPrincipal me,
                          @org.springframework.web.bind.annotation.RequestBody CreateBetRequest req) {
        if (req.selecciones() == null || req.selecciones().isEmpty()) {
            throw ApiException.badRequest("Debes añadir al menos una selección");
        }
        List<Long> ids = req.selecciones().stream().map(SelectionRequest::opcionCuotaId).toList();
        Apuesta a = betService.crear(me.userId(), req.importe(), ids);
        return toView(a);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<BetView> list(@AuthenticationPrincipal AuthPrincipal me,
                              @RequestParam(required = false) String estado) {
        List<Apuesta> result;
        if ("PENDIENTE".equalsIgnoreCase(estado)) {
            result = apuestas.findByUsuarioIdAndEstadoOrderByCreadoEnDesc(me.userId(), EstadoApuesta.PENDIENTE);
        } else if ("RESUELTO".equalsIgnoreCase(estado)) {
            result = apuestas.findByUsuarioIdOrderByCreadoEnDesc(me.userId()).stream()
                    .filter(a -> a.getEstado() != EstadoApuesta.PENDIENTE).toList();
        } else {
            result = apuestas.findByUsuarioIdOrderByCreadoEnDesc(me.userId());
        }
        return result.stream().map(this::toView).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public BetView one(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id) {
        Apuesta a = apuestas.findByIdAndUsuarioId(id, me.userId())
                .orElseThrow(() -> ApiException.notFound("Apuesta no encontrada"));
        return toView(a);
    }

    private BetView toView(Apuesta a) {
        List<SelectionView> sels = a.getSelecciones().stream()
                .map((Seleccion s) -> new SelectionView(s.getDescripcionSnapshot(),
                        s.getCuotaCongelada(), s.getResultado().name()))
                .toList();
        return new BetView(a.getId(), a.getTipo().name(), a.getImporte(), a.getCuotaTotal(),
                a.getRetornoPotencial(), a.getEstado().name(), a.getCreadoEn(), a.getResueltoEn(), sels);
    }
}
