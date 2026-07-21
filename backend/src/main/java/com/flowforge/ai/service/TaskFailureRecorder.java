package com.flowforge.ai.service;

import com.flowforge.ai.entity.Task;
import com.flowforge.ai.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskFailureRecorder {

    private final TaskRepository taskRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Task task) {
        taskRepository.save(task);
    }
}
