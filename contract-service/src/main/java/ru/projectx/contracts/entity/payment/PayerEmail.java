package ru.projectx.contracts.entity.payment;

public record PayerEmail(String address) {

    @Override
    public String toString() {
        return "PayerEmail[адрес скрыт]";
    }
}
