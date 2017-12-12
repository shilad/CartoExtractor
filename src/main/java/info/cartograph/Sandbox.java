package info.cartograph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * @author Shilad Sen
 */
public class Sandbox {
    public static void main(String args[]) {
        for (int p = 0;; p++) {
            int n = (int) Math.ceil(Math.pow(2, p));
            List<Double> numbers = new ArrayList<Double>();
            Random rand = new Random();
            for (int i = 0; i < n; i++) {
                numbers.add(rand.nextDouble());
            }
            long t0 = System.currentTimeMillis();
            Collections.sort(numbers);
            long t1 = System.currentTimeMillis();
            System.err.println("elapsed " + n + " is " + (t1 - t0) / 1000.0);
        }
    }
}
