package com.sharath.paycore.transfer;

import com.sharath.paycore.transfer.client.AccountClient;
import com.sharath.paycore.transfer.client.BalanceResponse;
import com.sharath.paycore.transfer.client.InsufficientFundsException;
import com.sharath.paycore.transfer.ledger.EntryType;
import com.sharath.paycore.transfer.ledger.LedgerEntry;
import com.sharath.paycore.transfer.ledger.LedgerEntryRepository;
import com.sharath.paycore.transfer.Transfer;
import com.sharath.paycore.transfer.TransferRepository;
import com.sharath.paycore.transfer.TransferService;
import com.sharath.paycore.transfer.TransferStatus;
import com.sharath.paycore.transfer.dto.InitiateTransferRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Plain unit test for {@link TransferService} — no Spring context. */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private AccountClient accountClient;

    @InjectMocks
    private TransferService transferService;

    private static InitiateTransferRequest request(String idempotencyKey) {
        return new InitiateTransferRequest(
                idempotencyKey, "ACC-001", "ACC-002", new BigDecimal("250.00"), "USD");
    }

    @Test
    void success_debitsFirstThenCreditsAndPersistsTransferPlusTwoLedgerEntries() {
        InitiateTransferRequest req = request("key-1");
        when(transferRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(accountClient.debit("ACC-001", new BigDecimal("250.00")))
                .thenReturn(new BalanceResponse("ACC-001", new BigDecimal("750.00")));
        when(accountClient.credit("ACC-002", new BigDecimal("250.00")))
                .thenReturn(new BalanceResponse("ACC-002", new BigDecimal("1250.00")));
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransferService.InitiationResult result = transferService.initiate(req);

        assertTrue(result.created());
        assertEquals(TransferStatus.COMPLETED, result.response().status());
        assertNull(result.response().failureReason());
        assertEquals(new BigDecimal("250.00"), result.response().amount());

        // debit must happen before credit
        InOrder inOrder = inOrder(accountClient);
        inOrder.verify(accountClient).debit("ACC-001", new BigDecimal("250.00"));
        inOrder.verify(accountClient).credit("ACC-002", new BigDecimal("250.00"));

        // exactly one DEBIT (source) and one CREDIT (destination) ledger row
        ArgumentCaptor<LedgerEntry> entries = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(entries.capture());
        List<LedgerEntry> captured = entries.getAllValues();
        assertEquals(EntryType.DEBIT, captured.get(0).getEntryType());
        assertEquals("ACC-001", captured.get(0).getAccountNumber());
        assertEquals(EntryType.CREDIT, captured.get(1).getEntryType());
        assertEquals("ACC-002", captured.get(1).getAccountNumber());
    }

    @Test
    void idempotentReplay_returnsOriginalWithoutTouchingAccountService() {
        InitiateTransferRequest req = request("key-1");
        Transfer existing = new Transfer(
                "key-1", "ACC-001", "ACC-002", new BigDecimal("250.00"), "USD",
                TransferStatus.COMPLETED, null);
        when(transferRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        TransferService.InitiationResult result = transferService.initiate(req);

        assertFalse(result.created());
        assertEquals(existing.getId(), result.response().transferId());
        assertEquals(TransferStatus.COMPLETED, result.response().status());
        verifyNoInteractions(accountClient);
        verify(transferRepository, never()).save(any());
        verifyNoInteractions(ledgerEntryRepository);
    }

    @Test
    void insufficientFunds_persistsFailedTransferAndNeverCredits() {
        InitiateTransferRequest req = request("key-2");
        when(transferRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
        when(accountClient.debit("ACC-001", new BigDecimal("250.00")))
                .thenThrow(new InsufficientFundsException("Insufficient funds"));
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransferService.InitiationResult result = transferService.initiate(req);

        assertTrue(result.created());
        assertEquals(TransferStatus.FAILED, result.response().status());
        assertEquals("Insufficient funds", result.response().failureReason());

        verify(accountClient, never()).credit(any(String.class), any(BigDecimal.class));

        ArgumentCaptor<Transfer> transfers = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository, times(1)).save(transfers.capture());
        assertEquals(TransferStatus.FAILED, transfers.getValue().getStatus());
        assertEquals("Insufficient funds", transfers.getValue().getFailureReason());
        verifyNoInteractions(ledgerEntryRepository);
    }
}
