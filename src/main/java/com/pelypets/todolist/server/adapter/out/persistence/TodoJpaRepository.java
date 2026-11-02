package com.pelypets.todolist.server.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface TodoJpaRepository extends JpaRepository<TodoEntity, UUID> {

    @Query("""
            select t from TodoEntity t
            where t.userId = :userId and t.dueAt >= :from and t.dueAt < :to
            order by t.dueAt, t.createdAt""")
    List<TodoEntity> findDueBetween(UUID userId, Instant from, Instant to);

    Optional<TodoEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TodoEntity t where t.id = :id and t.userId = :userId")
    int deleteByIdAndUserId(UUID id, UUID userId);
}
