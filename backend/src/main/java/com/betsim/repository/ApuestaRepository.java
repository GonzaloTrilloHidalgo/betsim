package com.betsim.repository;

import com.betsim.domain.Apuesta;
import com.betsim.domain.Enums.EstadoApuesta;
import com.betsim.domain.Enums.ResultadoSeleccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApuestaRepository extends JpaRepository<Apuesta, Long> {
    List<Apuesta> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);
    List<Apuesta> findByUsuarioIdAndEstadoOrderByCreadoEnDesc(Long usuarioId, EstadoApuesta estado);
    List<Apuesta> findByEstadoOrderByCreadoEnDesc(EstadoApuesta estado);
    Optional<Apuesta> findByIdAndUsuarioId(Long id, Long usuarioId);

    /** Apuestas (en cualquier estado) con alguna selección de este partido aún sin resolver. */
    @Query("""
           select distinct a from Apuesta a
           join a.selecciones s
           join s.opcionCuota o
           join o.mercado m
           where m.partido.id = :partidoId and s.resultado = :resultado
           """)
    List<Apuesta> findWithSelectionResultByPartido(@Param("partidoId") Long partidoId,
                                                   @Param("resultado") ResultadoSeleccion resultado);
}
