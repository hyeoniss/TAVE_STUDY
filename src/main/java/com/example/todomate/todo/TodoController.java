package com.example.todomate.todo;

import com.example.todomate.global.response.ApiResponse;
import com.example.todomate.global.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/todos")
public class TodoController {
    private final TodoService todoService;

    // 투두를 등록한다. 201 상태와 Location 헤더를 지정하기 위해 ResponseEntity를 사용한다.
    @PostMapping
    public ResponseEntity<ApiResponse<TodoResponse>> create(@Valid @RequestBody TodoRequest.Create request) {
        TodoResponse response = todoService.create(request);
        return ResponseEntity.created(URI.create("/api/todos/" + response.id()))
                .body(ApiResponse.success(response));
    }

    // 투두 목록을 날짜 조건과 페이지 단위로 조회한다. 기본 200 응답이라 ResponseEntity를 생략한다.
    @GetMapping
    public ApiResponse<PageResponse<TodoResponse>> findAll(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(todoService.findAll(date, page, size));
    }

    // ID로 투두 한 건을 조회한다.
    @GetMapping("/{id}")
    public ApiResponse<TodoResponse> findOne(@PathVariable Long id) {
        return ApiResponse.success(todoService.findOne(id));
    }

    // ID로 투두의 제목, 메모 또는 날짜를 부분 수정한다.
    @PatchMapping("/{id}")
    public ApiResponse<TodoResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TodoRequest.Update request
    ) {
        return ApiResponse.success(todoService.update(id, request));
    }

    // ID로 투두의 완료 상태를 변경한다.
    @PatchMapping("/{id}/completion")
    public ApiResponse<TodoResponse> changeCompletion(
            @PathVariable Long id,
            @Valid @RequestBody TodoRequest.Completion request
    ) {
        return ApiResponse.success(todoService.changeCompletion(id, request));
    }

    // ID로 투두를 삭제한다.
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        todoService.delete(id);
        return ApiResponse.successWithoutData();
    }
}
