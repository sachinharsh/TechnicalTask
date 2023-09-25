package com.dws.challenge;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.AmountTransfer;
import com.dws.challenge.exception.DuplicateAccountIdException;
import com.dws.challenge.exception.InsufficientBalanceException;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class AccountsServiceTest {

    @Autowired
    private AccountsService accountsService;

    @MockBean
    @Qualifier("emailNotificationService")
    private NotificationService notificationService;

    @Test
    void addAccount() {
        Account account = new Account("Id-123");
        account.setBalance(new BigDecimal(1000));
        this.accountsService.createAccount(account);

        assertThat(this.accountsService.getAccount("Id-123")).isEqualTo(account);
    }

    @Test
    void addAccount_failsOnDuplicateId() {
        String uniqueId = "Id-" + System.currentTimeMillis();
        Account account = new Account(uniqueId);
        this.accountsService.createAccount(account);

        try {
            this.accountsService.createAccount(account);
            fail("Should have failed when adding duplicate account");
        } catch (DuplicateAccountIdException ex) {
            assertThat(ex.getMessage()).isEqualTo("Account id " + uniqueId + " already exists!");
        }
    }

    @Test
    void createAccountTransactionTest() throws Exception {
        Account senderAccount = new Account("101", new BigDecimal(1000));
        Account receiverAccount = new Account("456", new BigDecimal(500));
        accountsService.createAccount(senderAccount);
        accountsService.createAccount(receiverAccount);
        AmountTransfer amountTransfer = new AmountTransfer("101", "456", new BigDecimal(100));
        this.accountsService.createAccountTransaction(amountTransfer);

        assertThat(this.accountsService.getAccount("101").getBalance()).isEqualTo(new BigDecimal(900));
        assertThat(this.accountsService.getAccount("456").getBalance()).isEqualTo(new BigDecimal(600));
    }

    @Test
    void createAccountTransactionForNegativeBalanceTest() throws Exception {
        Account senderAccount = new Account("123", new BigDecimal(-1000));
        Account receiverAccount = new Account("111", new BigDecimal(500));
        accountsService.createAccount(senderAccount);
        accountsService.createAccount(receiverAccount);
        AmountTransfer amountTransfer = new AmountTransfer("123", "111", new BigDecimal(100));

        try {
            this.accountsService.createAccountTransaction(amountTransfer);
            fail("Not Enough Balance to initiate transaction");
        } catch (InsufficientBalanceException ex) {
            assertThat(ex.getMessage()).isEqualTo("Not Enough Balance to initiate transaction");
        }

    }

    @Test
    void createAccountTransactionForTransferAmountBiggerThanInAccountTest() throws Exception {
        Account senderAccount = new Account("102", new BigDecimal(200));
        Account receiverAccount = new Account("567", new BigDecimal(500));
        accountsService.createAccount(senderAccount);
        accountsService.createAccount(receiverAccount);
        AmountTransfer amountTransfer = new AmountTransfer("102", "567", new BigDecimal(500));

        try {
            this.accountsService.createAccountTransaction(amountTransfer);
            fail("Not Enough Balance to initiate transaction");
        } catch (InsufficientBalanceException ex) {
            assertThat(ex.getMessage()).isEqualTo("Not Enough Balance to initiate transaction");
        }

    }

    @Test
    void createAccountTransactionForMultipleTransaction() throws Exception {
        Account senderAccount = new Account("103", new BigDecimal(1000));
        Account receiverAccount1 = new Account("678", new BigDecimal(500));
        Account receiverAccount2 = new Account("789", new BigDecimal(500));
        accountsService.createAccount(senderAccount);
        accountsService.createAccount(receiverAccount1);
        accountsService.createAccount(receiverAccount2);
        AmountTransfer amountTransfer = new AmountTransfer("103", "678", new BigDecimal(500));
        this.accountsService.createAccountTransaction(amountTransfer);
        AmountTransfer amountTransfer1 = new AmountTransfer("103", "789", new BigDecimal(500));
        this.accountsService.createAccountTransaction(amountTransfer1);
        AmountTransfer amountTransfer2 = new AmountTransfer("103", "678", new BigDecimal(500));
        try {
            this.accountsService.createAccountTransaction(amountTransfer2);
            fail("Not Enough Balance to initiate transaction");
        } catch (InsufficientBalanceException ex) {
            assertThat(ex.getMessage()).isEqualTo("Not Enough Balance to initiate transaction");
        }

    }

    @Test
    void createAccountTransactionToSendNotification() throws Exception {
        Account senderAccount = new Account("104", new BigDecimal(1000));
        Account receiverAccount = new Account("222", new BigDecimal(500));
        accountsService.createAccount(senderAccount);
        accountsService.createAccount(receiverAccount);

        AmountTransfer amountTransfer = new AmountTransfer("104", "222", new BigDecimal(500));
        this.accountsService.createAccountTransaction(amountTransfer);
        this.notificationService.notifyAboutTransfer(senderAccount,"Amount has been debited from the Account");
        this.notificationService.notifyAboutTransfer(receiverAccount,"Amount has been credited to the Account");


    }

}
