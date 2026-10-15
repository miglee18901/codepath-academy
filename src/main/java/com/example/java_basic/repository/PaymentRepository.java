package com.example.java_basic.repository;

import com.example.java_basic.entity.Payment;
import org.springframework.data.jpa.repository.*;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
