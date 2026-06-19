package com.betsim.domain;

import com.betsim.domain.Enums.TipoTransaccion;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transaccion")
public class Transaccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apuesta_id")
    private Apuesta apuesta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoTransaccion tipo;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal importe;

    @Column(name = "saldo_resultante", nullable = false, precision = 14, scale = 2)
    private BigDecimal saldoResultante;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    public Transaccion() {}

    public Transaccion(Usuario usuario, Apuesta apuesta, TipoTransaccion tipo,
                       BigDecimal importe, BigDecimal saldoResultante) {
        this.usuario = usuario;
        this.apuesta = apuesta;
        this.tipo = tipo;
        this.importe = importe;
        this.saldoResultante = saldoResultante;
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Apuesta getApuesta() { return apuesta; }
    public TipoTransaccion getTipo() { return tipo; }
    public BigDecimal getImporte() { return importe; }
    public BigDecimal getSaldoResultante() { return saldoResultante; }
    public Instant getCreadoEn() { return creadoEn; }
}
