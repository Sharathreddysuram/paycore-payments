package com.sharath.paycore.transfer;

import com.sharath.paycore.transfer.dto.InitiateTransferRequest;
import com.sharath.paycore.transfer.dto.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * Initiates a transfer. Returns 201 when a new transfer was created
     * (completed or failed), 200 when the idempotency key was replayed.
     */
    @PostMapping
    public ResponseEntity<TransferResponse> initiate(@Valid @RequestBody InitiateTransferRequest req) {
        TransferService.InitiationResult result = transferService.initiate(req);
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(transferService.getById(id));
    }
}
