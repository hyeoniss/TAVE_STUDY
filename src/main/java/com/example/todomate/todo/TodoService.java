package com.example.todomate.todo;

import com.example.todomate.global.exception.BusinessException;
import com.example.todomate.global.exception.CommonErrorCode;
import com.example.todomate.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {
    private static final int MAX_PAGE_SIZE = 100;
    private final TodoRepository todoRepository;

    @Transactional
    // 투두 등록 후 이전 목록이 노출되지 않도록 모든 목록 캐시를 삭제한다.
    @CacheEvict(cacheNames = "todoLists", allEntries = true)
    public TodoResponse create(TodoRequest.Create request) {
        Todo todo = new Todo(request.title(), request.memo(), request.todoDate());
        return TodoResponse.from(todoRepository.save(todo));
    }

    // 같은 날짜와 페이지의 반복 조회를 DB 대신 Redis에서 반환하기 위해 캐싱한다.
    @Cacheable(
            cacheNames = "todoLists",
            // 예: todoLists::2026-10-06:0:20
            key = "T(java.lang.String).valueOf(#date) + ':' + #page + ':' + #size"
    )
    public PageResponse<TodoResponse> findAll(LocalDate date, int page, int size) {
        // 페이지 번호와 한 번에 조회할 데이터 수를 검증한다.
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_PAGE);
        }

        // 페이지 조건과 날짜·생성일 내림차순 정렬 조건을 만든다.
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("todoDate"), Sort.Order.desc("createdAt"))
        );

        // 날짜가 없으면 전체를, 있으면 해당 날짜의 투두만 조회한다.
        Page<Todo> todos = date == null
                ? todoRepository.findAll(pageable)
                : todoRepository.findAllByTodoDate(date, pageable);  //삼항연산자 : 조건 ? 조건이_true일_때_값 : 조건이_false일_때_값

        // 엔티티 페이지를 API 응답용 DTO 페이지로 변환한다.
        return PageResponse.from(todos, TodoResponse::from);
    }

    // ID로 투두 한 건을 조회하고 응답 DTO로 변환한다.
    public TodoResponse findOne(Long id) {
        return TodoResponse.from(findTodo(id));
    }

    @Transactional
    // 투두 수정 후 이전 목록이 노출되지 않도록 모든 목록 캐시를 삭제한다.
    @CacheEvict(cacheNames = "todoLists", allEntries = true)
    public TodoResponse update(Long id, TodoRequest.Update request) {
        if (request.title() == null && request.memo() == null && request.todoDate() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        Todo todo = findTodo(id);
        todo.update(request.title(), request.memo(), request.todoDate());
        return TodoResponse.from(todo);
    }

    @Transactional
    // 완료 상태 변경 후 이전 목록이 노출되지 않도록 모든 목록 캐시를 삭제한다.
    @CacheEvict(cacheNames = "todoLists", allEntries = true)
    public TodoResponse changeCompletion(Long id, TodoRequest.Completion request) {
        Todo todo = findTodo(id);
        todo.changeCompletion(request.completed());
        return TodoResponse.from(todo);
    }

    @Transactional
    // 투두 삭제 후 삭제된 데이터가 목록에 남지 않도록 모든 목록 캐시를 삭제한다.
    @CacheEvict(cacheNames = "todoLists", allEntries = true)
    public void delete(Long id) {
        todoRepository.delete(findTodo(id));
    }

    private Todo findTodo(Long id) {
        return todoRepository.findById(id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.TODO_NOT_FOUND));
    }
}
