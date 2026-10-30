package com.shinhan.corebank;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CorebankApplication {

    public static void main(String[] args) {
        Phase2SeedProcessRunner.run(CorebankApplication.class, args);
    }
}
