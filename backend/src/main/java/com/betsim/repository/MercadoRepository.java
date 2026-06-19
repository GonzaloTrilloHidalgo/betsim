package com.betsim.repository;

import com.betsim.domain.Enums.TipoMercado;
import com.betsim.domain.Mercado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MercadoRepository extends JpaRepository<Mercado, Long> {
    Optional<Mercado> findByPartidoIdAndTipo(Long partidoId, TipoMercado tipo);
    List<Mercado> findByPartidoId(Long partidoId);
}
