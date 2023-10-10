package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.AmountTransfer;
import com.dws.challenge.exception.AccountNotFoundException;
import com.dws.challenge.exception.InsufficientBalanceException;
import com.dws.challenge.exception.TransferException;
import com.dws.challenge.repository.AccountsRepository;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AccountsService {

    @Getter
    private final AccountsRepository accountsRepository;

    private NotificationService notificationService;

    private final Object debitLock = new Object();
    private final Object creditLock = new Object();

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

        //check if both accounts exist in database.
        if (!accountsRepository.doesAccountExistById(senderAccountId)) {
            throw new AccountNotFoundException("Sender Account not found");
        }

        if (!accountsRepository.doesAccountExistById(receiverAccountId)) {
            throw new AccountNotFoundException("Receiver Account not found");
        }


        //check if sender Account has sufficient balance to initiate transaction
        if (canUserInitiateMoneyTransfer(amountTransfer)) {

            try {
                //perform the amount transfer
                updateUserAccountBalances(amountTransfer);
            } catch (TransferException ex) {
                throw new TransferException("Could not complete the request");
            }
        } else {
            throw new InsufficientBalanceException("Not Enough Balance to initiate transaction");
        }

    }

    private boolean canUserInitiateMoneyTransfer(AmountTransfer amountTransfer) {
        Account senderAccount =
                accountsRepository.getAccount(amountTransfer.getSenderAccountId());

        BigDecimal transactionAmount =
                amountTransfer.getTransactionAmount();

        //if comparisonResult =-1,then it's less than value ,0=equal,1=greater
        int comparisonResult =
                senderAccount.getBalance().compareTo(transactionAmount);
        if (comparisonResult >= 0)
            return true;
        return false;
    }

    void updateUserAccountBalances(AmountTransfer amountTransfer) {
        Account senderAccount =
                accountsRepository.getAccount(amountTransfer.getSenderAccountId());

        Account receiverAccount =
                accountsRepository.getAccount(amountTransfer.getReceiverAccountId());

        BigDecimal transactionAmount = amountTransfer.getTransactionAmount();

        synchronized (debitLock) {
            senderAccount = debitAccountEntity(senderAccount, transactionAmount);
            accountsRepository.save(senderAccount);
            notificationService.notifyAboutTransfer(senderAccount, "Amount has been debited from the Account");
        }

        synchronized (creditLock) {
            receiverAccount = creditAccountEntity(receiverAccount, transactionAmount);
            accountsRepository.save(receiverAccount);
            notificationService.notifyAboutTransfer(receiverAccount, "Amount has been credited to the Account");
        }
    }


    private Account debitAccountEntity(Account senderAccount, BigDecimal amountToDebit) {

        BigDecimal currentBalanceBeforeDebit = senderAccount.getBalance();
        BigDecimal currentBalanceAfterDebit = currentBalanceBeforeDebit.subtract(amountToDebit);
        senderAccount.setBalance(currentBalanceAfterDebit);

        return senderAccount;
    }

    private Account creditAccountEntity(Account receiverAccount, BigDecimal amountToCredit) {

        BigDecimal currentBalanceBeforeAddition = receiverAccount.getBalance();
        BigDecimal currentBalanceAfterAddition = currentBalanceBeforeAddition.add(amountToCredit);
        receiverAccount.setBalance(currentBalanceAfterAddition);

        return receiverAccount;
    }
}
