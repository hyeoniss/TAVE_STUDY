package com.example.todomate.todo;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "todos", indexes = @Index(name = "idx_todos_date_created", columnList = "todo_date, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Todo extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String memo;

    @Column(name = "todo_date", nullable = false)
    private LocalDate todoDate;

    @Column(nullable = false)
    private boolean completed;

    public Todo(String title, String memo, LocalDate todoDate) {
        this.title = title;
        this.memo = memo;
        this.todoDate = todoDate;
        this.completed = false;
    }

    public void update(String title, String memo, LocalDate todoDate) {
        if (title != null) this.title = title;
        if (memo != null) this.memo = memo;
        if (todoDate != null) this.todoDate = todoDate;
    }

    public void changeCompletion(boolean completed) {
        this.completed = completed;
    }
}
