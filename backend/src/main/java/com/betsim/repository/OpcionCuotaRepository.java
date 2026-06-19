package com.betsim.repository;

import com.betsim.domain.OpcionCuota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OpcionCuotaRepository extends JpaRepository<OpcionCuota, Long> {
    Optional<OpcionCuota> findByMercadoIdAndCodigo(Long mercadoId, String codigo);
    List<OpcionCuota> findByMercadoId(Long mercadoId);
}
