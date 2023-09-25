package com.dws.challenge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class AmountTransfer {

    @NotNull(message = "Sender Account id must be present")
    @NotEmpty
    private String senderAccountId;

    @NotNull(message = "Receiver Account id must be present")
    @NotEmpty
    private String receiverAccountId;

    @NotNull(message = "Transaction Amount cannot be absent.")
    @Min(value = 0, message = "transaction amount must be positive.")
    private BigDecimal transactionAmount;


    @JsonCreator
    public AmountTransfer(@JsonProperty("senderAccountId") String senderAccountId,
                          @JsonProperty("receiverAccountId") String receiverAccountId,
                          @JsonProperty("transactionAmount") BigDecimal transactionAmount) {
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.transactionAmount = transactionAmount;
    }

    @Override
    public String toString() {
        return "AmountTransfer{" +
                "senderAccountId='" + senderAccountId + '\'' +
                ", receiverAccountId='" + receiverAccountId + '\'' +
                ", transactionAmount=" + transactionAmount +
                '}';
    }
}
