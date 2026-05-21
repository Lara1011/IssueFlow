package com.att.tdp.issueflow.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.service.TicketEscalationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutoEscalationSchedulerTest {

	@Mock
	private TicketEscalationService ticketEscalationService;

	@Test
	void scheduledCycleDelegatesToEscalationService() {
		when(ticketEscalationService.runEscalationCycle()).thenReturn(2);

		new AutoEscalationScheduler(ticketEscalationService).runScheduledEscalationCycle();

		verify(ticketEscalationService).runEscalationCycle();
	}
}
