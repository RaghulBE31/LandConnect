package in.tamiland.market;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-memory sliding-window limiter for the public write endpoints (sign-in, sign-up, enquiries,
 * support queries) to slow credential guessing and form spam. It is per application instance; behind
 * several instances or a proxy use a shared limiter (and set server.forward-headers-strategy=framework
 * so the real client address is seen).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+10)
public class RateLimitFilter extends OncePerRequestFilter {
    record Rule(int max,long windowMillis) {}
    private static final Map<String,Rule> RULES=Map.of(
            "/api/auth/login",new Rule(10,15*60_000L),
            "/api/auth/register",new Rule(10,60*60_000L),
            "/api/enquiries",new Rule(20,60*60_000L),
            "/api/support",new Rule(10,60*60_000L));

    private final Map<String,ArrayDeque<Long>> hits=new ConcurrentHashMap<>();
    private final Clock clock;
    private volatile long lastSweep;

    public RateLimitFilter() { this(Clock.systemUTC()); }
    RateLimitFilter(Clock clock) { this.clock=clock; }

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        Rule rule="POST".equals(request.getMethod())?RULES.get(request.getRequestURI()):null;
        if(rule!=null&&!allow(request.getRequestURI()+"|"+request.getRemoteAddr(),rule)) {
            response.setStatus(429);
            response.setHeader("Retry-After",String.valueOf(rule.windowMillis()/1000));
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"message\":\"Too many requests. Please wait a while and try again.\"}");
            return;
        }
        chain.doFilter(request,response);
    }

    boolean allow(String key,Rule rule) {
        long now=clock.millis();
        sweep(now);
        ArrayDeque<Long> q=hits.computeIfAbsent(key,k->new ArrayDeque<>());
        synchronized(q) {
            while(!q.isEmpty()&&now-q.peekFirst()>=rule.windowMillis()) q.pollFirst();
            if(q.size()>=rule.max()) return false;
            q.addLast(now);
            return true;
        }
    }

    private void sweep(long now) {
        if(now-lastSweep<60_000) return;
        lastSweep=now;
        hits.entrySet().removeIf(e->{ synchronized(e.getValue()) { Long last=e.getValue().peekLast(); return last==null||now-last>60*60_000L; } });
    }
}
