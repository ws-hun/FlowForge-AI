package com.flowforge.ai.service;

import com.flowforge.ai.dto.FlowRunTraceResponse;
import com.flowforge.ai.entity.Task;
import com.flowforge.ai.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskFailureRecorderTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private FlowNodeArtifactService flowNodeArtifactService;

    private TaskFailureRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new TaskFailureRecorder(taskRepository, flowNodeArtifactService);
    }

    @Test
    void savesTheFailedTaskBeforeItsArtifactsInOneRecorderBoundary() {
        Task task = Task.builder().id(UUID.randomUUID()).build();
        FlowRunTraceResponse trace = trace(task.getId());

        recorder.record(task, trace);

        InOrder writes = inOrder(taskRepository, flowNodeArtifactService);
        writes.verify(taskRepository).save(task);
        writes.verify(flowNodeArtifactService).persist(task, trace, null);
    }

    @Test
    void propagatesArtifactPersistenceFailureSoTheTransactionCanRollBack() {
        Task task = Task.builder().id(UUID.randomUUID()).build();
        FlowRunTraceResponse trace = trace(task.getId());
        when(flowNodeArtifactService.persist(task, trace, null))
                .thenThrow(new IllegalStateException("artifact write failed"));

        assertThatThrownBy(() -> recorder.record(task, trace))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("artifact write failed");
    }

    @Test
    void recordsFailuresInAnIndependentTransaction() throws Exception {
        Method record = TaskFailureRecorder.class.getDeclaredMethod(
                "record",
                Task.class,
                FlowRunTraceResponse.class
        );
        Transactional transaction = record.getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private FlowRunTraceResponse trace(UUID taskId) {
        return new FlowRunTraceResponse(
                taskId,
                UUID.randomUUID(),
                Task.STATUS_FAILED,
                "single-pass",
                1,
                "flow-compiler-v1",
                "a".repeat(64),
                "compiled-flow",
                null,
                null,
                List.of()
        );
    }
}
