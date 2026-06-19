package com.betsim.domain;

import com.betsim.domain.Enums.EstadoPartido;
import com.betsim.domain.Enums.Fase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "partido")
public class Partido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "liga_id")
    private Liga liga;

    @Column(name = "external_id", nullable = false, unique = true, length = 80)
    private String externalId;

    @Column(name = "equipo_local", nullable = false, length = 80)
    private String equipoLocal;

    @Column(name = "equipo_visitante", nullable = false, length = 80)
    private String equipoVisitante;

    @Column(name = "inicio_utc", nullable = false)
    private Instant inicioUtc;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Fase fase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPartido estado = EstadoPartido.PROGRAMADO;

    @Column(name = "goles_local")
    private Integer golesLocal;

    @Column(name = "goles_visitante")
    private Integer golesVisitante;

    public Long getId() { return id; }
    public Liga getLiga() { return liga; }
    public void setLiga(Liga liga) { this.liga = liga; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getEquipoLocal() { return equipoLocal; }
    public void setEquipoLocal(String equipoLocal) { this.equipoLocal = equipoLocal; }
    public String getEquipoVisitante() { return equipoVisitante; }
    public void setEquipoVisitante(String equipoVisitante) { this.equipoVisitante = equipoVisitante; }
    public Instant getInicioUtc() { return inicioUtc; }
    public void setInicioUtc(Instant inicioUtc) { this.inicioUtc = inicioUtc; }
    public Fase getFase() { return fase; }
    public void setFase(Fase fase) { this.fase = fase; }
    public EstadoPartido getEstado() { return estado; }
    public void setEstado(EstadoPartido estado) { this.estado = estado; }
    public Integer getGolesLocal() { return golesLocal; }
    public void setGolesLocal(Integer golesLocal) { this.golesLocal = golesLocal; }
    public Integer getGolesVisitante() { return golesVisitante; }
    public void setGolesVisitante(Integer golesVisitante) { this.golesVisitante = golesVisitante; }
}
