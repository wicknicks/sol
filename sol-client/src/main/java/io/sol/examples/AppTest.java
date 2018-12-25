package io.sol.examples;

import io.sol.SolLogger;
import io.sol.SolLoggers;

public class AppTest {

    private final SolLogger sol = SolLoggers.logger(AppTest.class);

    public void start() {
        long deadline = System.currentTimeMillis() + 60_000;
        while(System.currentTimeMillis() < deadline) {
            sol.log("hello", "world");
            try {
                System.out.println("Sleeping...");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        new AppTest().start();
    }

}
