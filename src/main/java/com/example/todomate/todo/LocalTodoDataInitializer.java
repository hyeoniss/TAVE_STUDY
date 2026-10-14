package com.example.todomate.todo;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 로컬 또는 시연용 Render 실행 시 페이지네이션을 확인할 수 있도록 테스트 투두를 자동 생성한다.
@Component
@Profile({"local", "render"})
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class LocalTodoDataInitializer implements ApplicationRunner {
    private final TodoRepository todoRepository;
    private final Optional<CacheManager> cacheManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (todoRepository.count() > 0) {
            return;
        }

        LocalDate today = LocalDate.now();
        List<Todo> todos = new ArrayList<>();

        for (int i = 1; i <= 65; i++) {
            Todo todo = new Todo(
                    "페이징 테스트 투두 " + i,
                    "페이지네이션 확인용 테스트 데이터입니다.",
                    today.minusDays((i - 1) % 14)
            );
            if (i % 3 == 0) {
                todo.changeCompletion(true);
            }
            todos.add(todo);
        }

        todoRepository.saveAll(todos);

        // 이전 로컬 실행에서 Redis에 남은 목록 캐시를 제거한다.
        Cache todoLists = cacheManager.map(manager -> manager.getCache("todoLists")).orElse(null);
        if (todoLists != null) {
            todoLists.clear();
        }
    }
}
