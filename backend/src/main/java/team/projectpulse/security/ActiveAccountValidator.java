package team.projectpulse.security;

import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import team.projectpulse.user.UserRepository;

/** FR-SEC-active-account: resolve current account state on every bearer authentication. */
public class ActiveAccountValidator implements OAuth2TokenValidator<Jwt> {
    private final UserRepository users;
    public ActiveAccountValidator(UserRepository users) { this.users = users; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            Object identity = jwt.getClaim("userId");
            Integer id = Integer.valueOf(String.valueOf(identity));
            if (users.findById(id).filter(user -> user.isEnabled()).isPresent()) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (NumberFormatException ignored) {
            // Missing or malformed identity is an authentication failure.
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "The account is inactive or unavailable.", null));
    }
}
