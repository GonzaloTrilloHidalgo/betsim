package com.betsim.repository;

import com.betsim.domain.Enums.EstadoPartido;
import com.betsim.domain.Partido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PartidoRepository extends JpaRepository<Partido, Long> {
    Optional<Partido> findByExternalId(String externalId);

    List<Partido> findByEstadoOrderByInicioUtcAsc(EstadoPartido estado);

    List<Partido> findByEstadoInOrderByInicioUtcDesc(java.util.Collection<EstadoPartido> estados);

    @Query("select p from Partido p where p.estado = :estado and p.inicioUtc <= :limite")
    List<Partido> findReadyToSettle(@Param("estado") EstadoPartido estado, @Param("limite") Instant limite);
}
