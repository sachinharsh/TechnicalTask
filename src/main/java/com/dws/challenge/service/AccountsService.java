package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.AmountTransfer;
import com.dws.challenge.exception.AccountNotFoundException;
import com.dws.challenge.exception.InsufficientBalanceException;
import com.dws.challenge.repository.AccountsRepository;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountsService {

    @Getter
    private final AccountsRepository accountsRepository;

    private final NotificationService notificationService;

    private final Map<String, Object> accountLocks = new ConcurrentHashMap<>();
    
    @Autowired
    public AccountsService(AccountsRepository accountsRepository, NotificationService notificationService) {
        this.accountsRepository = accountsRepository;
        this.notificationService = notificationService;
    }

    public void createAccount(Account account) {
        this.accountsRepository.createAccount(account);
    }

    public Account getAccount(String accountId) {
        return this.accountsRepository.getAccount(accountId);
    }

    public void createAccountTransaction(AmountTransfer amountTransfer) throws Exception {
        String senderAccountId = amountTransfer.getSenderAccountId();
        String receiverAccountId = amountTransfer.getReceiverAccountId();

        // Ensure locks for both sender and receiver accounts to avoid deadlocks
        Object senderLock = accountLocks.computeIfAbsent(senderAccountId, k -> new Object());
        Object receiverLock = accountLocks.computeIfAbsent(receiverAccountId, k -> new Object());

        // Acquire locks in a consistent order to prevent potential deadlocks
        Object firstLock = senderAccountId.compareTo(receiverAccountId) < 0 ? senderLock : receiverLock;
        Object secondLock = firstLock == senderLock ? receiverLock : senderLock;

        synchronized (firstLock) {
            synchronized (secondLock) {
                // Check if both accounts exist in the database
                if (!accountsRepository.doesAccountExistById(senderAccountId)) {
                    throw new AccountNotFoundException("Sender Account not found");
                }

                if (!accountsRepository.doesAccountExistById(receiverAccountId)) {
                    throw new AccountNotFoundException("Receiver Account not found");
                }

                // Check if sender Account has sufficient balance to initiate the transaction
                if (canUserInitiateMoneyTransfer(amountTransfer)) {
                    // Perform the amount transfer
                    updateUserAccountBalances(amountTransfer);
                } else {
                    throw new InsufficientBalanceException("Not Enough Balance to initiate transaction");
                }
            }
        }
    }

    private boolean canUserInitiateMoneyTransfer(AmountTransfer amountTransfer) {
        Account senderAccount = accountsRepository.getAccount(amountTransfer.getSenderAccountId());
        BigDecimal transactionAmount = amountTransfer.getTransactionAmount();

        // If comparisonResult = -1, then it's less than value, 0 = equal, 1 = greater
        return senderAccount.getBalance().compareTo(transactionAmount) >= 0;
    }

    void updateUserAccountBalances(AmountTransfer amountTransfer) {
        Account senderAccount = accountsRepository.getAccount(amountTransfer.getSenderAccountId());
        Account receiverAccount = accountsRepository.getAccount(amountTransfer.getReceiverAccountId());

        BigDecimal transactionAmount = amountTransfer.getTransactionAmount();

        // Debit sender account
        BigDecimal currentBalanceBeforeDebit = senderAccount.getBalance();
        BigDecimal currentBalanceAfterDebit = currentBalanceBeforeDebit.subtract(transactionAmount);
        senderAccount.setBalance(currentBalanceAfterDebit);
        accountsRepository.save(senderAccount);
        notificationService.notifyAboutTransfer(senderAccount, "Amount has been debited from the Account");

        // Credit receiver account
        BigDecimal currentBalanceBeforeCredit = receiverAccount.getBalance();
        BigDecimal currentBalanceAfterCredit = currentBalanceBeforeCredit.add(transactionAmount);
        receiverAccount.setBalance(currentBalanceAfterCredit);
        accountsRepository.save(receiverAccount);
        notificationService.notifyAboutTransfer(receiverAccount, "Amount has been credited to the Account");
    }

}
