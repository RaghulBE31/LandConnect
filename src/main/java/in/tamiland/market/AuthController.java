package in.tamiland.market;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;

    public AuthController(JdbcTemplate jdbc, PasswordEncoder encoder, AuthenticationManager authenticationManager) {
        this.jdbc=jdbc; this.encoder=encoder; this.authenticationManager=authenticationManager;
    }

    public record RegisterRequest(@NotBlank @Size(max=120) String fullName, @NotBlank @Email String email,
                                  @Size(max=30) String phone, @NotBlank @Size(min=8,max=100) String password,
                                  @NotBlank String role) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record AccountResponse(String email, String fullName, String phone, String role) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        String role=request.role().toUpperCase();
        if(!role.equals("BUYER")&&!role.equals("OWNER")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Role must be BUYER or OWNER");
        String email=request.email().trim().toLowerCase();
        String pw=request.password();
        if(pw.chars().noneMatch(Character::isLetter)||pw.chars().noneMatch(Character::isDigit)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must include at least one letter and one number");
        try { jdbc.update("INSERT INTO users(email,full_name,phone,password_hash,role) VALUES(?,?,?,?,?)",email,request.fullName().trim(),request.phone(),encoder.encode(request.password()),role); }
        catch(DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"An account with this email already exists"); }
        authenticate(email,request.password(),servletRequest);
        return account(email);
    }

    @PostMapping("/login")
    public AccountResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String email=request.email().trim().toLowerCase();
        try { authenticate(email,request.password(),servletRequest); }
        catch(org.springframework.security.core.AuthenticationException ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect"); }
        return account(email);
    }

    @GetMapping("/me")
    public AccountResponse me(Authentication authentication) {
        if(authentication==null||!authentication.isAuthenticated()||authentication.getPrincipal().equals("anonymousUser")) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return account(authentication.getName());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session=request.getSession(false);
        if(session!=null) session.invalidate();
    }

    private void authenticate(String email,String password,HttpServletRequest request) {
        Authentication authentication=authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email,password));
        if(request.getSession(false)!=null) request.changeSessionId();
        SecurityContext context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication); SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
    }
    private AccountResponse account(String email) {
        return jdbc.queryForObject("SELECT email,full_name,phone,role FROM users WHERE email=?",(rs,n)->new AccountResponse(rs.getString("email"),rs.getString("full_name"),rs.getString("phone"),rs.getString("role")),email);
    }
}
