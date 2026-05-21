package com.att.tdp.issueflow.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.entity.Ticket;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

	@Test
	void optimisticLockingFailureReturnsConflictWithInformativeMessage() throws Exception {
		MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new OptimisticLockingTestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

		mockMvc.perform(get("/optimistic-locking-test"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Ticket was updated by another request. Please reload and try again."));
	}

	@RestController
	private static class OptimisticLockingTestController {

		@GetMapping("/optimistic-locking-test")
		void throwOptimisticLockingFailure() {
			throw new ObjectOptimisticLockingFailureException(Ticket.class, 1L);
		}
	}
}
