package com.betsim.domain;

import com.betsim.domain.Enums.ResultadoSeleccion;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "seleccion")
public class Seleccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "apuesta_id")
    private Apuesta apuesta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opcion_cuota_id")
    private OpcionCuota opcionCuota;

    @Column(name = "descripcion_snapshot", nullable = false, length = 160)
    private String descripcionSnapshot;

    @Column(name = "cuota_congelada", nullable = false, precision = 6, scale = 2)
    private BigDecimal cuotaCongelada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultadoSeleccion resultado = ResultadoSeleccion.PENDIENTE;

    public Long getId() { return id; }
    public Apuesta getApuesta() { return apuesta; }
    public void setApuesta(Apuesta apuesta) { this.apuesta = apuesta; }
    public OpcionCuota getOpcionCuota() { return opcionCuota; }
    public void setOpcionCuota(OpcionCuota opcionCuota) { this.opcionCuota = opcionCuota; }
    public String getDescripcionSnapshot() { return descripcionSnapshot; }
    public void setDescripcionSnapshot(String descripcionSnapshot) { this.descripcionSnapshot = descripcionSnapshot; }
    public BigDecimal getCuotaCongelada() { return cuotaCongelada; }
    public void setCuotaCongelada(BigDecimal cuotaCongelada) { this.cuotaCongelada = cuotaCongelada; }
    public ResultadoSeleccion getResultado() { return resultado; }
    public void setResultado(ResultadoSeleccion resultado) { this.resultado = resultado; }
}
