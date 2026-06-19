package com.betsim.domain;

import com.betsim.domain.Enums.EstadoApuesta;
import com.betsim.domain.Enums.TipoApuesta;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "apuesta")
public class Apuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoApuesta tipo;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal importe;

    @Column(name = "cuota_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal cuotaTotal;

    @Column(name = "retorno_potencial", nullable = false, precision = 14, scale = 2)
    private BigDecimal retornoPotencial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoApuesta estado = EstadoApuesta.PENDIENTE;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    @Column(name = "resuelto_en")
    private Instant resueltoEn;

    @OneToMany(mappedBy = "apuesta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Seleccion> selecciones = new ArrayList<>();

    public void addSeleccion(Seleccion s) {
        s.setApuesta(this);
        selecciones.add(s);
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public TipoApuesta getTipo() { return tipo; }
    public void setTipo(TipoApuesta tipo) { this.tipo = tipo; }
    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }
    public BigDecimal getCuotaTotal() { return cuotaTotal; }
    public void setCuotaTotal(BigDecimal cuotaTotal) { this.cuotaTotal = cuotaTotal; }
    public BigDecimal getRetornoPotencial() { return retornoPotencial; }
    public void setRetornoPotencial(BigDecimal retornoPotencial) { this.retornoPotencial = retornoPotencial; }
    public EstadoApuesta getEstado() { return estado; }
    public void setEstado(EstadoApuesta estado) { this.estado = estado; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getResueltoEn() { return resueltoEn; }
    public void setResueltoEn(Instant resueltoEn) { this.resueltoEn = resueltoEn; }
    public List<Seleccion> getSelecciones() { return selecciones; }
}
