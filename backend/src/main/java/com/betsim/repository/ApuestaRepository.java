package com.betsim.repository;

import com.betsim.domain.Apuesta;
import com.betsim.domain.Enums.EstadoApuesta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApuestaRepository extends JpaRepository<Apuesta, Long> {
    List<Apuesta> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);
    List<Apuesta> findByUsuarioIdAndEstadoOrderByCreadoEnDesc(Long usuarioId, EstadoApuesta estado);
    Optional<Apuesta> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Query("""
           select distinct a from Apuesta a
           join a.selecciones s
           join s.opcionCuota o
           join o.mercado m
           where m.partido.id = :partidoId and a.estado = :estado
           """)
    List<Apuesta> findPendingByPartido(@Param("partidoId") Long partidoId,
                                       @Param("estado") EstadoApuesta estado);
}
