package com.att.tdp.issueflow.scheduler;

import com.att.tdp.issueflow.service.TicketEscalationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AutoEscalationScheduler {

	private static final Logger log = LoggerFactory.getLogger(AutoEscalationScheduler.class);

	private final TicketEscalationService ticketEscalationService;

	public AutoEscalationScheduler(TicketEscalationService ticketEscalationService) {
		this.ticketEscalationService = ticketEscalationService;
	}

	@Scheduled(
		fixedDelayString = "${issueflow.auto-escalation.fixed-delay-ms:60000}",
		initialDelayString = "${issueflow.auto-escalation.initial-delay-ms:10000}"
	)
	public void runScheduledEscalationCycle() {
		try {
			int changedCount = ticketEscalationService.runEscalationCycle();
			if (changedCount > 0) {
				log.info("Auto-escalation cycle updated {} ticket(s)", changedCount);
			}
		}
		catch (RuntimeException ex) {
			log.error("Auto-escalation scheduler failed", ex);
		}
	}
}
