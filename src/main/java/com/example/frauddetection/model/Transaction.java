package com.example.frauddetection.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import java.math.BigDecimal;
import java.util.Date;

@Entity
public class Transaction {
    public static final String SEQUENCE_NAME = "transaction_sequence";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private Date timestamp;
    private String merchantId;
    private String location;
    private String previousLocation;
    private String previousCurrency;
    private TransactionStatus status;
    
    public Transaction() {}
    
    public Transaction(Long id, String accountId, BigDecimal amount, String currency,
                      Date timestamp, String merchantId, String location,
                      TransactionStatus status) {
        this.id = id;
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.timestamp = timestamp;
        this.merchantId = merchantId;
        this.location = location;
        this.status = status;
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getAccountId() {
        return accountId;
    }
    
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public Date getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getMerchantId() {
        return merchantId;
    }
    
    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }
    
    public String getLocation() {
        return location;
    }
    
    public void setLocation(String location) {
        this.location = location;
    }
    
    public String getPreviousLocation() {
        return previousLocation;
    }
    
    public void setPreviousLocation(String previousLocation) {
        this.previousLocation = previousLocation;
    }
    
    public String getPreviousCurrency() {
        return previousCurrency;
    }
    
    public void setPreviousCurrency(String previousCurrency) {
        this.previousCurrency = previousCurrency;
    }
    
    public TransactionStatus getStatus() {
        return status;
    }
    
    public void setStatus(TransactionStatus status) {
        this.status = status;
    }
    
    public enum TransactionStatus {
        PENDING,
        APPROVED,
        REJECTED,
        FLAGGED_SUSPICIOUS
    }
}