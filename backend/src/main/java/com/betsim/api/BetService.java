package com.betsim.api;

import com.betsim.domain.*;
import com.betsim.domain.Enums.*;
import com.betsim.repository.*;
import com.betsim.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BetService {

    private final UsuarioRepository usuarios;
    private final ApuestaRepository apuestas;
    private final OpcionCuotaRepository opciones;
    private final TransaccionRepository transacciones;
    private final int maxSelecciones;

    public BetService(UsuarioRepository usuarios, ApuestaRepository apuestas,
                      OpcionCuotaRepository opciones, TransaccionRepository transacciones,
                      @Value("${betsim.bet.max-selecciones}") int maxSelecciones) {
        this.usuarios = usuarios;
        this.apuestas = apuestas;
        this.opciones = opciones;
        this.transacciones = transacciones;
        this.maxSelecciones = maxSelecciones;
    }

    /**
     * Crea un ticket (simple o combinada) de forma atómica: valida saldo y opciones,
     * congela las cuotas vigentes y bloquea el importe del saldo. Todo en una transacción.
     */
    @Transactional
    public Apuesta crear(Long userId, BigDecimal importe, List<Long> opcionIds) {
        if (importe == null || importe.signum() <= 0) {
            throw ApiException.badRequest("El importe debe ser mayor que 0");
        }
        if (opcionIds == null || opcionIds.isEmpty()) {
            throw ApiException.badRequest("Debes añadir al menos una selección");
        }
        if (new HashSet<>(opcionIds).size() != opcionIds.size()) {
            throw ApiException.badRequest("Hay selecciones duplicadas");
        }
        if (opcionIds.size() > maxSelecciones) {
            throw ApiException.badRequest("Máximo " + maxSelecciones + " selecciones por combinada");
        }

        Usuario u = usuarios.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Sesión no válida: vuelve a iniciar sesión"));
        if (u.getSaldo().compareTo(importe) < 0) {
            throw ApiException.badRequest("Saldo insuficiente");
        }

        Apuesta apuesta = new Apuesta();
        apuesta.setUsuario(u);
        apuesta.setImporte(importe.setScale(2, RoundingMode.HALF_UP));

        BigDecimal cuotaTotal = BigDecimal.ONE;
        // Se permiten varias selecciones del MISMO partido (combinada del mismo partido), pero no dos
        // del MISMO grupo correlacionado: clave = partidoId + grupo (ver grupo()).
        Set<String> gruposVistos = new HashSet<>();
        List<Seleccion> sels = new ArrayList<>();

        for (Long opcionId : opcionIds) {
            OpcionCuota oc = opciones.findById(opcionId)
                    .orElseThrow(() -> ApiException.notFound("Opción no encontrada: " + opcionId));
            Mercado m = oc.getMercado();
            Partido p = m.getPartido();

            if (!oc.isDisponible()) throw ApiException.badRequest("Cuota no disponible: " + oc.getDescripcion());
            if (m.getEstado() != EstadoMercado.ABIERTO) throw ApiException.badRequest("Mercado cerrado");
            if (p.getEstado() != EstadoPartido.PROGRAMADO || !p.getInicioUtc().isAfter(Instant.now())) {
                throw ApiException.badRequest("El partido ya ha empezado: " + p.getEquipoLocal() + " vs " + p.getEquipoVisitante());
            }
            if (!gruposVistos.add(p.getId() + ":" + grupo(m.getTipo()))) {
                throw ApiException.badRequest("No puedes combinar dos apuestas del mismo tipo en el mismo partido");
            }

            Seleccion s = new Seleccion();
            s.setOpcionCuota(oc);
            s.setCuotaCongelada(oc.getCuota());
            s.setDescripcionSnapshot(p.getEquipoLocal() + " vs " + p.getEquipoVisitante() + " — " + oc.getDescripcion());
            sels.add(s);
            cuotaTotal = cuotaTotal.multiply(oc.getCuota());
        }

        cuotaTotal = cuotaTotal.setScale(2, RoundingMode.HALF_UP);
        apuesta.setTipo(sels.size() == 1 ? TipoApuesta.SIMPLE : TipoApuesta.COMBINADA);
        apuesta.setCuotaTotal(cuotaTotal);
        apuesta.setRetornoPotencial(importe.multiply(cuotaTotal).setScale(2, RoundingMode.HALF_UP));
        sels.forEach(apuesta::addSeleccion);

        // Bloqueo del importe (saldo nunca negativo, ya validado).
        u.setSaldo(u.getSaldo().subtract(apuesta.getImporte()));
        usuarios.save(u);

        Apuesta guardada = apuestas.save(apuesta);
        transacciones.save(new Transaccion(u, guardada, TipoTransaccion.APUESTA,
                apuesta.getImporte().negate(), u.getSaldo()));
        return guardada;
    }

    /**
     * Grupo correlacionado de un mercado: dentro de un mismo partido solo se admite una selección por
     * grupo. 1X2 y Doble oportunidad comparten grupo (ambos son el resultado del partido).
     */
    private static String grupo(Enums.TipoMercado tipo) {
        return (tipo == Enums.TipoMercado.UNO_X_DOS || tipo == Enums.TipoMercado.DOBLE_OPORTUNIDAD)
                ? "RESULTADO" : tipo.name();
    }
}
