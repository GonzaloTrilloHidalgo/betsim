package com.betsim.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "liga")
public class Liga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "sport_key", nullable = false, unique = true, length = 80)
    private String sportKey;

    @Column(length = 80)
    private String pais;

    @Column(nullable = false)
    private boolean activa = true;

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getSportKey() { return sportKey; }
    public void setSportKey(String sportKey) { this.sportKey = sportKey; }
    public String getPais() { return pais; }
    public void setPais(String pais) { this.pais = pais; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
}
