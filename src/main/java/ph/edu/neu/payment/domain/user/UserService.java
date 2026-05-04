package ph.edu.neu.payment.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import ph.edu.neu.payment.api.dto.AdminDtos;

import java.util.UUID;

public interface UserService {

    Page<AdminDtos.UserSummary> search(String query, Pageable pageable);

    AdminDtos.UserDetails details(UUID userId);

    AdminDtos.UserDetails suspend(UUID userId, UUID actorUserId);

    AdminDtos.UserDetails reinstate(UUID userId, UUID actorUserId);

    /** Provision a CASHIER or ADMIN account. Caller must already be an ADMIN. */
    AdminDtos.UserDetails createStaff(AdminDtos.CreateStaffRequest req, UUID actorUserId);
}
