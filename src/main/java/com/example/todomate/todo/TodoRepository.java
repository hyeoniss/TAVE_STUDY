package com.example.todomate.todo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

// Todo 엔티티의 기본 CRUD와 페이징 조회를 담당한다. Long은 Todo의 ID 타입이다.
public interface TodoRepository extends JpaRepository<Todo, Long> {

    // 특정 날짜의 Todo 목록과 전체 개수·페이지 수 등의 정보를 Page<Todo>로 반환한다.
    // Todo는 페이지 안에 담기는 엔티티이고, Pageable은 페이지 번호·크기·정렬 조건이다.
    Page<Todo> findAllByTodoDate(LocalDate todoDate, Pageable pageable);
}
