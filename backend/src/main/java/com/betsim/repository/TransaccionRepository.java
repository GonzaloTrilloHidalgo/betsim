package com.betsim.repository;

import com.betsim.domain.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {
    List<Transaccion> findByUsuarioIdOrderByCreadoEnAsc(Long usuarioId);
}
