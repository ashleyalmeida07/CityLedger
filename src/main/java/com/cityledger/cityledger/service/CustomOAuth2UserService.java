package com.cityledger.cityledger.service;

import com.cityledger.cityledger.model.AppUser;
import com.cityledger.cityledger.model.Role;
import com.cityledger.cityledger.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * Called after successful Google login.
 * Looks up or creates the user in our DB, then wraps them with
 * a Spring Security principal that includes their CityLedger role.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AppUserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // Fetch the Google profile attributes
        OAuth2User oauthUser = super.loadUser(userRequest);
        Map<String, Object> attributes = oauthUser.getAttributes();

        String googleId = (String) attributes.get("sub");
        String email    = (String) attributes.get("email");
        String name     = (String) attributes.get("name");
        String picture  = (String) attributes.get("picture");

        // Find existing user or create a new CITIZEN by default
        AppUser user = userRepository.findByGoogleId(googleId)
                .orElseGet(() -> {
                    AppUser newUser = new AppUser();
                    newUser.setGoogleId(googleId);
                    newUser.setEmail(email);
                    newUser.setName(name);
                    newUser.setPictureUrl(picture);
                    
                    if (email != null && email.toLowerCase().startsWith("crce10227ceb")) {
                        newUser.setRole(Role.ADMIN);
                    } else {
                        newUser.setRole(Role.CITIZEN);
                    }
                    
                    log.info("New user registered via Google: {} [{}]", email, newUser.getRole());
                    return newUser;
                });

        // Always sync latest profile info from Google
        user.setName(name);
        user.setPictureUrl(picture);
        
        // Force upgrade the admin account if they previously registered as CITIZEN
        if (email != null && email.toLowerCase().startsWith("crce10227ceb") && user.getRole() != Role.ADMIN) {
            user.setRole(Role.ADMIN);
            log.info("Upgraded existing user {} to ADMIN", email);
        }
        
        userRepository.save(user);

        // Build a Spring Security authority from their CityLedger role
        String authority = "ROLE_" + user.getRole().name();
        var grantedAuthority = new OAuth2UserAuthority(authority, attributes);

        return new DefaultOAuth2User(Set.of(grantedAuthority), attributes, "email");
    }
}
