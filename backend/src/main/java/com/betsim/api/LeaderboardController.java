package com.betsim.api;

import com.betsim.domain.Enums.TipoTransaccion;
import com.betsim.domain.Transaccion;
import com.betsim.domain.Usuario;
import com.betsim.repository.TransaccionRepository;
import com.betsim.repository.UsuarioRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/leaderboard")
public class LeaderboardController {

    private final UsuarioRepository usuarios;
    private final TransaccionRepository transacciones;

    public LeaderboardController(UsuarioRepository usuarios, TransaccionRepository transacciones) {
        this.usuarios = usuarios;
        this.transacciones = transacciones;
    }

    public record Row(int posicion, String username, BigDecimal beneficioNeto, BigDecimal saldo) {}

    /** Ranking por beneficio neto histórico = Σ premios − Σ importes apostados (excluye bonos y resets). */
    @GetMapping
    public List<Row> ranking() {
        List<Usuario> all = usuarios.findAll();
        List<Row> rows = all.stream().map(u -> {
            BigDecimal neto = transacciones.findByUsuarioIdOrderByCreadoEnAsc(u.getId()).stream()
                    .filter(t -> t.getTipo() == TipoTransaccion.PREMIO || t.getTipo() == TipoTransaccion.APUESTA)
                    .map(Transaccion::getImporte)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new Row(0, u.getUsername(), neto, u.getSaldo());
        }).sorted(Comparator.comparing(Row::beneficioNeto).reversed()).toList();

        // Asignar posiciones 1..n
        return java.util.stream.IntStream.range(0, rows.size())
                .mapToObj(i -> new Row(i + 1, rows.get(i).username(), rows.get(i).beneficioNeto(), rows.get(i).saldo()))
                .toList();
    }
}
