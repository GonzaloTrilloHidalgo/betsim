package com.betsim.domain;

import com.betsim.domain.Enums.EstadoMercado;
import com.betsim.domain.Enums.TipoMercado;
import jakarta.persistence.*;

@Entity
@Table(name = "mercado")
public class Mercado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partido_id")
    private Partido partido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMercado tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMercado estado = EstadoMercado.ABIERTO;

    public Mercado() {}

    public Mercado(Partido partido, TipoMercado tipo) {
        this.partido = partido;
        this.tipo = tipo;
    }

    public Long getId() { return id; }
    public Partido getPartido() { return partido; }
    public void setPartido(Partido partido) { this.partido = partido; }
    public TipoMercado getTipo() { return tipo; }
    public void setTipo(TipoMercado tipo) { this.tipo = tipo; }
    public EstadoMercado getEstado() { return estado; }
    public void setEstado(EstadoMercado estado) { this.estado = estado; }
}
