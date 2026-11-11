package bd.shromikai.api;

import bd.shromikai.verify.ReviewDesk;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-22 {@code GET /api/v1/review-tickets/{ticketId}} (FR-61): the worker's view of the human
 * decision on the findings the verifier could not back. The secret token travels in the {@code
 * X-Ticket-Token} header, never in the URL; a wrong token is {@code 404}, a ticket whose last item
 * has expired {@code 410 ticket-expired}.
 */
@RestController
@RequestMapping("/api/v1/review-tickets")
public class ReviewTicketController {

  private final ReviewDesk desk;

  public ReviewTicketController(ReviewDesk desk) {
    this.desk = desk;
  }

  @GetMapping("/{ticketId}")
  public ObjectNode get(
      @PathVariable UUID ticketId,
      @RequestHeader(value = "X-Ticket-Token", required = false) @Nullable String token) {
    return desk.ticket(ticketId, token);
  }
}
