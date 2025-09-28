package com.example.taskapi.service;

import com.example.taskapi.model.Task;
import com.example.taskapi.model.TaskExecution;
import com.example.taskapi.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Service
public class TaskService {

    private final TaskRepository repo;

    // Allowed simple commands — whitelist approach (safer).
    private static final Set<String> ALLOWED_COMMANDS = Set.of("echo", "date", "ls", "pwd", "whoami", "sleep");

    public TaskService(TaskRepository repo) {
        this.repo = repo;
    }

    public List<Task> findAll() {
        return repo.findAll();
    }

    public Optional<Task> findById(String id) {
        return repo.findById(id);
    }

    public Task save(Task task) {
        return repo.save(task);
    }

    public void deleteById(String id) {
        repo.deleteById(id);
    }

    public List<Task> findByName(String name) {
        return repo.findByNameContainingIgnoreCase(name);
    }

    /**
     * Validate the command string. Simple rules:
     * - reject dangerous characters that allow chaining or file writes
     * - require command's base verb to be in ALLOWED_COMMANDS
     */
    public boolean validateCommand(String command) {
        if (command == null || command.trim().isEmpty()) return false;
        // Reject characters that allow command-chaining / subshell / redirection
        String forbidden = "[;&|`$()<>]";
        if (command.matches(".*" + forbidden + ".*")) return false;

        // Extract verb
        String[] parts = command.trim().split("\\s+", 2);
        String verb = parts[0];
        return ALLOWED_COMMANDS.contains(verb);
    }

    /**
     * Execute the command (locally) with a timeout and collect output.
     * NOTE: In production, you'd run inside a restricted environment or in a Kubernetes pod via k8s API.
     */
    public TaskExecution runCommand(String command, long timeoutSeconds) throws Exception {
        Date start = new Date();
        StringBuilder output = new StringBuilder();

        ProcessBuilder pb = new ProcessBuilder();
        // Use "sh -c" to allow commands like 'echo Hello'
        // But because we forbid dangerous chars, this usage is acceptable for simple commands.
        pb.command("sh", "-c", command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        ExecutorService ex = Executors.newSingleThreadExecutor();
        Future<?> readerFuture = ex.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            } catch (Exception e) {
                // swallow; will be added to output
                output.append("ERROR reading process output: ").append(e.getMessage());
            }
        });

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            output.append("PROCESS TIMEOUT (killed)");
        } else {
            // wait for reader to finish
            try { readerFuture.get(2, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        ex.shutdownNow();

        Date end = new Date();
        String outStr = output.toString().trim();
        return new TaskExecution(start, end, outStr);
    }

    public TaskExecution executeTaskAndStore(String taskId) throws Exception {
        Task task = repo.findById(taskId).orElseThrow(() -> new NoSuchElementException("Task not found"));
        if (!validateCommand(task.getCommand())) {
            throw new IllegalArgumentException("Command validation failed");
        }

        TaskExecution exec = runCommand(task.getCommand(), 30); // 30s timeout
        task.getTaskExecutions().add(exec);
        repo.save(task);
        return exec;
    }
}
