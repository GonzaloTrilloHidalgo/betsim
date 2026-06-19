package com.betsim.repository;

import com.betsim.domain.Liga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LigaRepository extends JpaRepository<Liga, Long> {
    Optional<Liga> findBySportKey(String sportKey);
    List<Liga> findByActivaTrue();
}
