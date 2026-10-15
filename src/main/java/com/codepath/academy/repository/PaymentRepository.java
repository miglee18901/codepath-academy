package com.codepath.academy.repository;

import com.codepath.academy.entity.Payment;
import org.springframework.data.jpa.repository.*;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
