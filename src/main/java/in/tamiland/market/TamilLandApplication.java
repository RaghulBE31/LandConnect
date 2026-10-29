package in.tamiland.market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TamilLandApplication {
    public static void main(String[] args) { SpringApplication.run(TamilLandApplication.class, args); }
}
