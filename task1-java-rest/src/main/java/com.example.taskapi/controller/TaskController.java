package com.example.taskapi.controller;

import com.example.taskapi.model.Task;
import com.example.taskapi.model.TaskExecution;
import com.example.taskapi.service.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    // GET /tasks or /tasks?id=123
    @GetMapping
    public ResponseEntity<?> getTasks(@RequestParam(name = "id", required = false) String id) {
        if (id == null) {
            List<Task> all = service.findAll();
            return ResponseEntity.ok(all);
        } else {
            return service.findById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "task not found")));
        }
    }

    // PUT /tasks  (create or update)
    @PutMapping
    public ResponseEntity<?> putTask(@Valid @RequestBody Task task) {
        if (!service.validateCommand(task.getCommand())) {
            return ResponseEntity.badRequest().body(Map.of("error", "command validation failed"));
        }
        Task saved = service.save(task);
        return ResponseEntity.ok(saved);
    }

    // DELETE /tasks/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTask(@PathVariable String id) {
        Optional<Task> t = service.findById(id);
        if (t.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "task not found"));
        }
        service.deleteById(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    // GET /tasks/search?name=...
    @GetMapping("/search")
    public ResponseEntity<?> searchByName(@RequestParam("name") String name) {
        List<Task> found = service.findByName(name);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "no tasks found"));
        }
        return ResponseEntity.ok(found);
    }

    // PUT /tasks/{id}/executions  -> run the command and store execution
    @PutMapping("/{id}/executions")
    public ResponseEntity<?> runTask(@PathVariable String id) {
        try {
            TaskExecution exec = service.executeTaskAndStore(id);
            return ResponseEntity.ok(exec);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}

