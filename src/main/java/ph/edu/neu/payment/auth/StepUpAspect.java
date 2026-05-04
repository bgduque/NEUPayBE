package ph.edu.neu.payment.auth;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import ph.edu.neu.payment.common.error.ForbiddenException;

@Aspect
@Component
public class StepUpAspect {

    @Before("@annotation(ph.edu.neu.payment.auth.RequiresStepUp)")
    public void enforce() {
        AuthenticatedUser u = CurrentUser.require();
        if (!u.stepUp()) {
            throw new ForbiddenException("Step-up authentication required (Face ID).");
        }
    }
}
