package com.betsim.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "opcion_cuota")
public class OpcionCuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mercado_id")
    private Mercado mercado;

    @Column(nullable = false, length = 60)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String descripcion;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal cuota;

    @Column(nullable = false)
    private boolean disponible = true;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn = Instant.now();

    public Long getId() { return id; }
    public Mercado getMercado() { return mercado; }
    public void setMercado(Mercado mercado) { this.mercado = mercado; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getCuota() { return cuota; }
    public void setCuota(BigDecimal cuota) { this.cuota = cuota; }
    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
