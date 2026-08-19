package com.eventledger.gateway.dto; // Defines the package/folder location for this DTO class

import lombok.AllArgsConstructor; // Lombok annotation to generate a constructor with all parameters
import lombok.Builder;          // Lombok annotation enabling the Builder pattern for easy object creation
import lombok.Data;             // Lombok annotation generating Getters, Setters, toString, equals, and hashCode
import lombok.NoArgsConstructor;  // Lombok annotation to generate an empty default constructor

import java.math.BigDecimal;    // Imports BigDecimal for exact precision financial amounts

/**
 * AccountResponse DTO
 * Used to transfer account balance details between Account Service,
 * Event Gateway, and frontend clients.
 */
@Data // Generates getters (e.g. getAccountId()), setters, equals(), hashCode(), and toString()
@Builder // Enables fluent creation style: AccountResponse.builder().accountId("123").build()
@NoArgsConstructor // Generates default empty constructor required by JSON deserializers like Jackson
@AllArgsConstructor // Generates constructor accepting all fields as arguments
public class AccountResponse {

    // Unique identifier for the account (e.g., "acct-555")
    private String accountId;

    // Current net balance of the account using high-precision BigDecimal to avoid float rounding errors
    private BigDecimal balance;

    // ISO 4217 currency code for the balance (e.g., "USD", "EUR")
    private String currency;
}